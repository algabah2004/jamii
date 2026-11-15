package dev.jamii.api.missions;

import java.sql.Array;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jamii.api.auth.Annuaire;
import dev.jamii.api.demandes.Demandes;

/**
 * L'annonce d'une mission et ses invitations (contracts/missions.yaml, tag « missions »).
 *
 * <p>Qui voit une mission : le client toujours ; tout le monde une mission publique déjà publiée, fermée ou terminée ;
 * un invité une mission sur invitation, dès qu'elle n'est plus un brouillon. Pour tout autre, elle n'existe pas (404).
 */
@Service
class Missions {

	record Montant(long centimes, String devise) {
	}

	/** Une mission telle que la base la garde. */
	record Mission(UUID id, UUID clientId, UUID demandeId, String titre, String besoin, List<String> competences,
			Montant budget, LocalDate dateSouhaitee, String visibilite, String ville, String pays, boolean aDistance,
			String statut, int version, Instant creeLe, Instant publieeLe, UUID developpeurId) {
	}

	/** Une mission nouvelle, déjà validée par le contrôleur. */
	record Nouvelle(UUID demandeId, String titre, String besoin, List<String> competences, Montant budget,
			LocalDate dateSouhaitee, String visibilite, String ville, String pays, boolean aDistance) {
	}

	/** Une mission, et ce que la personne qui la lit est pour elle : `client`, `invite` ou `visiteur`. */
	record Lecture(Mission mission, String role) {
	}

	record Page(List<Mission> elements, String curseurSuivant) {
	}

	/** Contre le spam d'annonces : 5 publications par période glissante de 24 heures et par compte. */
	static final int PUBLICATIONS_PAR_JOUR = 5;

	private static final Duration JOUR = Duration.ofHours(24);

	private static final String COLONNES = """
			id, client_id, demande_id, titre, besoin, competences, budget_centimes, devise, date_souhaitee, visibilite,
			ville, pays, a_distance, statut, version, cree_le, publiee_le, developpeur_id""";

	/** L'ordre de la liste : la publication la plus récente d'abord, et la création pour un brouillon. */
	private static final String TRI = "COALESCE(publiee_le, cree_le)";

	private final JdbcClient base;

	private final Annuaire annuaire;

	private final Demandes demandes;

	private final Clock horloge;

	Missions(JdbcClient base, Annuaire annuaire, Demandes demandes, ObjectProvider<Clock> horloge) {
		this.base = base;
		this.annuaire = annuaire;
		this.demandes = demandes;
		this.horloge = horloge.getIfAvailable(Clock::systemUTC);
	}

	@Transactional
	UUID creer(UUID client, Nouvelle n) {
		if (n.demandeId() != null && !demandes.estAuteur(n.demandeId(), client)) {
			throw ErreurMissions.introuvable();
		}
		UUID id = UUID.randomUUID();
		base.sql("""
				INSERT INTO mission (id, client_id, demande_id, titre, besoin, competences, budget_centimes, devise,
					date_souhaitee, visibilite, ville, pays, a_distance, cree_le)
				VALUES (:id, :client, :demande, :titre, :besoin, :competences, :centimes, :devise,
					:date, :visibilite, :ville, :pays, :aDistance, :maintenant)
				""")
			.param("id", id)
			.param("client", client)
			.param("demande", n.demandeId())
			.param("titre", n.titre())
			.param("besoin", n.besoin())
			.param("competences", n.competences().toArray(String[]::new))
			.param("centimes", n.budget() == null ? null : n.budget().centimes())
			.param("devise", n.budget() == null ? null : n.budget().devise())
			.param("date", n.dateSouhaitee() == null ? null : Date.valueOf(n.dateSouhaitee()))
			.param("visibilite", n.visibilite())
			.param("ville", n.ville())
			.param("pays", n.pays())
			.param("aDistance", n.aDistance())
			.param("maintenant", Timestamp.from(horloge.instant()))
			.update();
		return id;
	}

	/** La mission, si `moi` (null : visiteur sans connexion) a le droit de savoir qu'elle existe. Sinon 404. */
	@Transactional(readOnly = true)
	Lecture lire(UUID id, UUID moi) {
		return role(charger(id, false), moi).orElseThrow(ErreurMissions::introuvable);
	}

	/**
	 * Comme {@link #lire(UUID, UUID)}, et un modérateur voit en plus, comme `moderateur`, une mission qui a un désaccord :
	 * il doit lire le travail pour trancher.
	 */
	@Transactional(readOnly = true)
	Lecture lire(UUID id, UUID moi, boolean moderateur) {
		Mission m = charger(id, false);
		Optional<Lecture> l = role(m, moi);
		boolean partie = l.map(Lecture::role).filter(r -> "client".equals(r) || "developpeur_retenu".equals(r)).isPresent();
		if (moderateur && !partie && aUnDesaccord(m.id())) {
			return new Lecture(m, "moderateur");
		}
		return l.orElseThrow(ErreurMissions::introuvable);
	}

	private boolean aUnDesaccord(UUID missionId) {
		return base.sql("SELECT EXISTS (SELECT 1 FROM desaccord WHERE mission_id = :m)").param("m", missionId).query(Boolean.class).single();
	}

	/**
	 * Une page de missions. `miennes` : celles dont je suis le client, celles où je suis invité et celles où j'ai
	 * proposé mes services, retenu ou non, tant que je ne me suis pas retiré (hors brouillon) ;
	 * sinon, les missions publiques encore ouvertes aux propositions.
	 */
	@Transactional(readOnly = true)
	Page lister(UUID moi, boolean miennes, String competence, String pays, int limite, String curseur) {
		var requete = new StringBuilder("SELECT " + COLONNES + " FROM mission WHERE ");
		if (miennes) {
			requete.append("""
					(client_id = :moi OR (statut <> 'brouillon' AND (
						EXISTS (SELECT 1 FROM invitation_mission i WHERE i.mission_id = mission.id AND i.utilisateur_id = :moi)
						OR EXISTS (SELECT 1 FROM proposition p WHERE p.mission_id = mission.id AND p.developpeur_id = :moi
							AND p.statut <> 'retiree'))))""");
		}
		else {
			requete.append("statut = 'publiee' AND visibilite = 'publique'");
		}
		if (competence != null) {
			requete.append(" AND :competence = ANY (competences)");
		}
		if (pays != null) {
			requete.append(" AND pays = :pays");
		}
		Curseur c = curseur == null ? null : Curseur.lire(curseur);
		if (c != null) {
			requete.append(" AND (" + TRI + ", id) < (:t, :cid)");
		}
		requete.append(" ORDER BY " + TRI + " DESC, id DESC LIMIT :n");

		var q = base.sql(requete.toString()).param("n", limite + 1);
		if (miennes) {
			q = q.param("moi", moi);
		}
		if (competence != null) {
			q = q.param("competence", competence);
		}
		if (pays != null) {
			q = q.param("pays", pays);
		}
		if (c != null) {
			q = q.param("t", Timestamp.from(c.tri())).param("cid", c.id());
		}
		List<Mission> lues = new ArrayList<>(q.query(Missions::mission).list());
		String suivant = null;
		if (lues.size() > limite) {
			lues = new ArrayList<>(lues.subList(0, limite));
			Mission derniere = lues.getLast();
			suivant = new Curseur(derniere.publieeLe() != null ? derniere.publieeLe() : derniere.creeLe(), derniere.id()).texte();
		}
		return new Page(lues, suivant);
	}

	/**
	 * brouillon → publiee. Le client seulement, avec la version qu'il a lue, et au plus {@value #PUBLICATIONS_PAR_JOUR}
	 * publications par période de 24 heures (429 sinon : rien n'est alors publié).
	 */
	@Transactional
	Mission publier(UUID id, UUID moi, int version) {
		Mission m = clientDe(id, moi);
		if (m.version() != version) {
			throw ErreurMissions.conflit("modification_concurrente", "La mission a changé entre-temps");
		}
		if (!"brouillon".equals(m.statut())) {
			throw ErreurMissions.conflit("etat_invalide", "Seul un brouillon se publie");
		}
		// Deux publications simultanées du même compte se comptent l'une après l'autre.
		base.sql("SELECT pg_advisory_xact_lock(hashtextextended(:client, 0))").param("client", moi.toString())
			.query((rs, i) -> 1).single();
		Instant maintenant = horloge.instant();
		List<Instant> recentes = base.sql("SELECT publiee_le FROM mission WHERE client_id = :c AND publiee_le > :depuis ORDER BY publiee_le")
			.param("c", moi)
			.param("depuis", Timestamp.from(maintenant.minus(JOUR)))
			.query((rs, i) -> rs.getTimestamp(1).toInstant())
			.list();
		if (recentes.size() >= PUBLICATIONS_PAR_JOUR) {
			// La place se libère quand la publication qui nous fait dépasser sort de la fenêtre de 24 heures.
			Instant liberee = recentes.get(recentes.size() - PUBLICATIONS_PAR_JOUR).plus(JOUR);
			throw ErreurMissions.limiteAtteinte("Trop de missions publiées aujourd'hui", Duration.between(maintenant, liberee).toSeconds() + 1);
		}
		base.sql("UPDATE mission SET statut = 'publiee', publiee_le = :maintenant, version = version + 1 WHERE id = :id")
			.param("maintenant", Timestamp.from(maintenant)).param("id", id).update();
		return charger(id, false);
	}

	/** publiee → fermee : le client ne choisit personne. */
	@Transactional
	Mission fermer(UUID id, UUID moi, int version) {
		Mission m = clientDe(id, moi);
		if (m.version() != version) {
			throw ErreurMissions.conflit("modification_concurrente", "La mission a changé entre-temps");
		}
		if (!"publiee".equals(m.statut())) {
			throw ErreurMissions.conflit("etat_invalide", "Seule une mission publiée se ferme");
		}
		base.sql("UPDATE mission SET statut = 'fermee', version = version + 1 WHERE id = :id").param("id", id).update();
		base.sql("UPDATE proposition SET statut = 'non_retenue' WHERE mission_id = :id AND statut IN ('envoyee', 'retenue')")
			.param("id", id).update();
		base.sql("DELETE FROM devis WHERE mission_id = :id AND accepte_le IS NULL").param("id", id).update();
		return charger(id, false);
	}

	/** Inviter un développeur, tant que la mission est un brouillon ou publiée. Inviter deux fois ne change rien. */
	@Transactional
	void inviter(UUID id, UUID moi, UUID invite) {
		Mission m = clientDe(id, moi);
		if (!"brouillon".equals(m.statut()) && !"publiee".equals(m.statut())) {
			throw ErreurMissions.conflit("etat_invalide", "On n'invite plus une fois la mission attribuée");
		}
		if (invite.equals(moi)) {
			throw ErreurMissions.conflit("propre_mission", "On n'invite pas soi-même");
		}
		if (annuaire.lire(invite).isEmpty()) {
			throw ErreurMissions.introuvable();
		}
		base.sql("""
				INSERT INTO invitation_mission (mission_id, utilisateur_id, cree_le) VALUES (:m, :u, :t)
				ON CONFLICT DO NOTHING
				""")
			.param("m", id).param("u", invite).param("t", Timestamp.from(horloge.instant())).update();
	}

	// ---------------------------------------------------------------- règles d'accès

	/** La mission verrouillée, pour son client seulement : 404 si `moi` ne peut pas la voir, 403 s'il la voit sans être le client. */
	Mission clientDe(UUID id, UUID moi) {
		Mission m = charger(id, true);
		Lecture l = role(m, moi).orElseThrow(ErreurMissions::introuvable);
		if (!"client".equals(l.role())) {
			throw ErreurMissions.interdit();
		}
		return m;
	}

	/**
	 * Ce que `moi` est pour cette mission : `client`, `developpeur_retenu`, `candidat` (il a proposé, même s'il s'est retiré
	 * ou a été écarté : il peut toujours voir où il en est), `invite` (hors brouillon) ou `visiteur` (une annonce publique
	 * publiée, fermée ou terminée). Vide : la mission n'existe pas pour lui.
	 */
	Optional<Lecture> role(Mission m, UUID moi) {
		if (m.clientId().equals(moi)) {
			return Optional.of(new Lecture(m, "client"));
		}
		if (moi != null && moi.equals(m.developpeurId())) {
			return Optional.of(new Lecture(m, "developpeur_retenu"));
		}
		if (moi != null && base.sql("SELECT EXISTS (SELECT 1 FROM proposition WHERE mission_id = :m AND developpeur_id = :u)")
			.param("m", m.id()).param("u", moi).query(Boolean.class).single()) {
			return Optional.of(new Lecture(m, "candidat"));
		}
		boolean invite = moi != null && !"brouillon".equals(m.statut()) && base
			.sql("SELECT EXISTS (SELECT 1 FROM invitation_mission WHERE mission_id = :m AND utilisateur_id = :u)")
			.param("m", m.id()).param("u", moi).query(Boolean.class).single();
		if (invite) {
			return Optional.of(new Lecture(m, "invite"));
		}
		boolean annonceOuverte = "publique".equals(m.visibilite())
				&& List.of("publiee", "fermee", "terminee").contains(m.statut());
		return annonceOuverte ? Optional.of(new Lecture(m, "visiteur")) : Optional.empty();
	}

	/** Combien de propositions comptent pour chaque mission (une proposition retirée ne compte plus). */
	@Transactional(readOnly = true)
	java.util.Map<UUID, Integer> nombresDePropositions(java.util.Collection<UUID> missions) {
		java.util.Map<UUID, Integer> nombres = new java.util.HashMap<>();
		if (missions.isEmpty()) {
			return nombres;
		}
		base.sql("SELECT mission_id, count(*) FROM proposition WHERE mission_id IN (:ids) AND statut <> 'retiree' GROUP BY mission_id")
			.param("ids", missions)
			.query((rs, i) -> nombres.put(rs.getObject(1, UUID.class), rs.getInt(2)))
			.list();
		return nombres;
	}

	Mission charger(UUID id, boolean verrouiller) {
		return base.sql("SELECT " + COLONNES + " FROM mission WHERE id = :id" + (verrouiller ? " FOR UPDATE" : ""))
			.param("id", id)
			.query(Missions::mission)
			.optional()
			.orElseThrow(ErreurMissions::introuvable);
	}

	private static Mission mission(ResultSet rs, int ligne) throws SQLException {
		Array tableau = rs.getArray("competences");
		List<String> competences = List.of((String[]) tableau.getArray());
		long centimes = rs.getLong("budget_centimes");
		Montant budget = rs.wasNull() ? null : new Montant(centimes, rs.getString("devise"));
		Date date = rs.getDate("date_souhaitee");
		Timestamp publiee = rs.getTimestamp("publiee_le");
		return new Mission(rs.getObject("id", UUID.class), rs.getObject("client_id", UUID.class),
				rs.getObject("demande_id", UUID.class), rs.getString("titre"), rs.getString("besoin"), competences, budget,
				date == null ? null : date.toLocalDate(), rs.getString("visibilite"), rs.getString("ville"),
				rs.getString("pays"), rs.getBoolean("a_distance"), rs.getString("statut"), rs.getInt("version"),
				rs.getTimestamp("cree_le").toInstant(), publiee == null ? null : publiee.toInstant(),
				rs.getObject("developpeur_id", UUID.class));
	}

	/** Le curseur de la liste : l'instant de tri et l'identifiant de la dernière mission, en base 64 (opaque pour le front). */
	private record Curseur(Instant tri, UUID id) {

		String texte() {
			return Base64.getUrlEncoder().withoutPadding().encodeToString((tri + "|" + id).getBytes(java.nio.charset.StandardCharsets.UTF_8));
		}

		static Curseur lire(String texte) {
			try {
				String[] parties = new String(Base64.getUrlDecoder().decode(texte), java.nio.charset.StandardCharsets.UTF_8).split("\\|");
				return new Curseur(Instant.parse(parties[0]), UUID.fromString(parties[1]));
			}
			catch (RuntimeException e) {
				throw ErreurMissions.validation(List.of(java.util.Map.of("champ", "curseur", "code", "invalide")));
			}
		}

	}

}
