package dev.jamii.api.missions;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Candidater à une mission, la retenir, se retirer (contracts/missions.yaml, tag « propositions »).
 *
 * <p>Qui voit une proposition : le client les voit toutes ; un développeur voit seulement la sienne. Une mission
 * qu'on n'a pas le droit de voir répond 404 (voir {@link Missions#role}).
 */
@Service
class Propositions {

	/** Une proposition telle que la base la garde. */
	record Proposition(UUID id, UUID missionId, UUID developpeurId, String message, Missions.Montant prix, int delaiJours,
			List<String> reponses, String statut, Instant envoyeeLe) {
	}

	/** Une proposition nouvelle, déjà validée par le contrôleur. */
	record Nouvelle(String message, Missions.Montant prix, int delaiJours, List<String> reponses) {
	}

	record Page(List<Proposition> elements, String curseurSuivant) {
	}

	/** Contre le spam de candidatures : 20 par période glissante de 24 heures et par développeur (c'est gratuit). */
	static final int PROPOSITIONS_PAR_JOUR = 20;

	private static final Duration JOUR = Duration.ofHours(24);

	private static final String COLONNES = "id, mission_id, developpeur_id, message, prix_centimes, devise, delai_jours, reponses, statut, cree_le";

	private final JdbcClient base;

	private final Missions missions;

	private final ApplicationEventPublisher evenements;

	private final Clock horloge;

	Propositions(JdbcClient base, Missions missions, ApplicationEventPublisher evenements, ObjectProvider<Clock> horloge) {
		this.base = base;
		this.missions = missions;
		this.evenements = evenements;
		this.horloge = horloge.getIfAvailable(Clock::systemUTC);
	}

	/**
	 * Envoyer sa proposition. Dans l'ordre : la mission est-elle visible (404), est-ce la mienne (409 `propre_mission`),
	 * est-elle ouverte (409 `etat_invalide`), ai-je déjà proposé (409 `deja_candidat`), ai-je atteint ma limite du
	 * jour (429). Une proposition refusée n'est pas enregistrée.
	 */
	@Transactional
	Proposition proposer(UUID missionId, UUID moi, Nouvelle n) {
		Missions.Mission m = missions.charger(missionId, true);
		Missions.Lecture lecture = missions.role(m, moi).orElseThrow(ErreurMissions::introuvable);
		if ("client".equals(lecture.role())) {
			throw ErreurMissions.conflit("propre_mission", "On ne propose pas ses services sur sa propre mission");
		}
		if (!"publiee".equals(m.statut())) {
			throw ErreurMissions.conflit("etat_invalide", "Cette mission n'est plus ouverte aux propositions");
		}
		if ("candidat".equals(lecture.role())) {
			throw ErreurMissions.conflit("deja_candidat", "Tu as déjà proposé tes services pour cette mission");
		}
		// Deux envois simultanés du même développeur se comptent l'un après l'autre.
		base.sql("SELECT pg_advisory_xact_lock(hashtextextended(:dev, 1))").param("dev", moi.toString()).query((rs, i) -> 1).single();
		Instant maintenant = horloge.instant();
		List<Instant> recentes = base.sql("SELECT cree_le FROM proposition WHERE developpeur_id = :d AND cree_le > :depuis ORDER BY cree_le")
			.param("d", moi)
			.param("depuis", Timestamp.from(maintenant.minus(JOUR)))
			.query((rs, i) -> rs.getTimestamp(1).toInstant())
			.list();
		if (recentes.size() >= PROPOSITIONS_PAR_JOUR) {
			Instant liberee = recentes.get(recentes.size() - PROPOSITIONS_PAR_JOUR).plus(JOUR);
			throw ErreurMissions.limiteAtteinte("Trop de propositions envoyées aujourd'hui",
					Duration.between(maintenant, liberee).toSeconds() + 1);
		}
		UUID id = UUID.randomUUID();
		try {
			base.sql("""
					INSERT INTO proposition (id, mission_id, developpeur_id, message, prix_centimes, devise, delai_jours, reponses, cree_le)
					VALUES (:id, :m, :d, :message, :centimes, :devise, :delai, :reponses, :t)
					""")
				.param("id", id).param("m", missionId).param("d", moi).param("message", n.message())
				.param("centimes", n.prix().centimes()).param("devise", n.prix().devise()).param("delai", n.delaiJours())
				.param("reponses", n.reponses().toArray(String[]::new)).param("t", Timestamp.from(maintenant))
				.update();
		}
		catch (DuplicateKeyException e) {
			throw ErreurMissions.conflit("deja_candidat", "Tu as déjà proposé tes services pour cette mission");
		}
		evenements.publishEvent(new PropositionRecue(missionId, id, m.clientId(), moi, maintenant));
		return lire(id);
	}

	/** Le client voit toutes les propositions, tout autre lecteur seulement la sienne (peut-être aucune). */
	@Transactional(readOnly = true)
	Page lister(UUID missionId, UUID moi, int limite, String curseur) {
		Missions.Mission m = missions.charger(missionId, false);
		Missions.Lecture lecture = missions.role(m, moi).orElseThrow(ErreurMissions::introuvable);
		var requete = new StringBuilder("SELECT " + COLONNES + " FROM proposition WHERE mission_id = :m");
		if (!"client".equals(lecture.role())) {
			requete.append(" AND developpeur_id = :moi");
		}
		Curseur c = curseur == null ? null : Curseur.lire(curseur);
		if (c != null) {
			requete.append(" AND (cree_le, id) < (:t, :cid)");
		}
		requete.append(" ORDER BY cree_le DESC, id DESC LIMIT :n");
		var q = base.sql(requete.toString()).param("m", missionId).param("n", limite + 1);
		if (!"client".equals(lecture.role())) {
			q = q.param("moi", moi);
		}
		if (c != null) {
			q = q.param("t", Timestamp.from(c.creeLe())).param("cid", c.id());
		}
		List<Proposition> lues = new ArrayList<>(q.query(Propositions::proposition).list());
		String suivant = null;
		if (lues.size() > limite) {
			lues = new ArrayList<>(lues.subList(0, limite));
			suivant = new Curseur(lues.getLast().envoyeeLe(), lues.getLast().id()).texte();
		}
		return new Page(lues, suivant);
	}

	/** Une proposition par son identifiant, sans règle d'accès : réservé aux contrôleurs qui l'ont déjà vérifiée. */
	@Transactional(readOnly = true)
	Proposition lire(UUID id) {
		return base.sql("SELECT " + COLONNES + " FROM proposition WHERE id = :id").param("id", id)
			.query(Propositions::proposition).single();
	}

	/**
	 * Retenir une proposition : le client seulement, avec la version qu'il a lue. Une seule proposition retenue à la
	 * fois : retenir une autre remet la précédente à « envoyée » et fait disparaître son devis non accepté. Ça ne
	 * change pas la mission elle-même (sa version non plus).
	 */
	@Transactional
	void retenir(UUID missionId, UUID propositionId, UUID moi, int version) {
		Missions.Mission m = missions.clientDe(missionId, moi);
		if (m.version() != version) {
			throw ErreurMissions.conflit("modification_concurrente", "La mission a changé entre-temps");
		}
		if (!"publiee".equals(m.statut())) {
			throw ErreurMissions.conflit("etat_invalide", "Cette mission n'est plus ouverte aux propositions");
		}
		String statut = base.sql("SELECT statut FROM proposition WHERE id = :p AND mission_id = :m").param("p", propositionId)
			.param("m", missionId).query(String.class).optional().orElseThrow(ErreurMissions::introuvable);
		if (!"envoyee".equals(statut) && !"retenue".equals(statut)) {
			throw ErreurMissions.conflit("etat_invalide", "Cette proposition n'est plus en cours");
		}
		if ("retenue".equals(statut)) {
			return;
		}
		base.sql("UPDATE proposition SET statut = 'envoyee' WHERE mission_id = :m AND statut = 'retenue'").param("m", missionId).update();
		base.sql("DELETE FROM devis WHERE mission_id = :m AND accepte_le IS NULL").param("m", missionId).update();
		base.sql("UPDATE proposition SET statut = 'retenue' WHERE id = :p").param("p", propositionId).update();
	}

	/** Se retirer : l'auteur seulement, tant que la mission est ouverte. Une proposition retirée ne revient pas. */
	@Transactional
	void retirer(UUID missionId, UUID propositionId, UUID moi) {
		Missions.Mission m = missions.charger(missionId, true);
		missions.role(m, moi).orElseThrow(ErreurMissions::introuvable);
		String statut = base.sql("SELECT statut FROM proposition WHERE id = :p AND mission_id = :m AND developpeur_id = :d")
			.param("p", propositionId).param("m", missionId).param("d", moi).query(String.class).optional()
			.orElseThrow(ErreurMissions::introuvable);
		if (!"publiee".equals(m.statut()) || (!"envoyee".equals(statut) && !"retenue".equals(statut))) {
			throw ErreurMissions.conflit("etat_invalide", "Cette proposition ne peut plus être retirée");
		}
		if ("retenue".equals(statut)) {
			base.sql("DELETE FROM devis WHERE proposition_id = :p AND accepte_le IS NULL").param("p", propositionId).update();
		}
		base.sql("UPDATE proposition SET statut = 'retiree' WHERE id = :p").param("p", propositionId).update();
	}

	private static Proposition proposition(java.sql.ResultSet rs, int ligne) throws java.sql.SQLException {
		String[] reponses = (String[]) rs.getArray("reponses").getArray();
		return new Proposition(rs.getObject("id", UUID.class), rs.getObject("mission_id", UUID.class),
				rs.getObject("developpeur_id", UUID.class), rs.getString("message"),
				new Missions.Montant(rs.getLong("prix_centimes"), rs.getString("devise")), rs.getInt("delai_jours"),
				List.of(reponses), rs.getString("statut"), rs.getTimestamp("cree_le").toInstant());
	}

	/** Le curseur de la liste : la date d'envoi et l'identifiant de la dernière proposition, en base 64. */
	private record Curseur(Instant creeLe, UUID id) {

		String texte() {
			return Base64.getUrlEncoder().withoutPadding().encodeToString((creeLe + "|" + id).getBytes(StandardCharsets.UTF_8));
		}

		static Curseur lire(String texte) {
			try {
				String[] parties = new String(Base64.getUrlDecoder().decode(texte), StandardCharsets.UTF_8).split("\\|");
				return new Curseur(Instant.parse(parties[0]), UUID.fromString(parties[1]));
			}
			catch (RuntimeException e) {
				throw ErreurMissions.validation(List.of(java.util.Map.of("champ", "curseur", "code", "invalide")));
			}
		}

	}

}
