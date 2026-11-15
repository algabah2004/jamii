package dev.jamii.api.hackathons;

import dev.jamii.api.questionnaires.Questionnaire;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * S'inscrire, former les équipes, trier les candidatures (contracts/hackathons.yaml, tag « inscriptions »).
 *
 * <p>Les inscriptions ont une fenêtre : avant l'ouverture, le hackathon n'est pas prêt (409 `etat_invalide`) ; **à la
 * fermeture, les équipes sont verrouillées** (409 `equipes_verrouillees`), même si l'écran était ouvert avant. Le
 * hackathon est verrouillé le temps de chaque action : deux personnes ne prennent pas la même dernière place.
 */
@Service
class Inscriptions {

	record Inscription(UUID id, UUID hackathonId, UUID utilisateurId, UUID equipeId, String statut, boolean chercheEquipe,
			List<String> competences, List<Questionnaire.Reponse> reponses, Instant creeLe) {
	}

	record Equipe(UUID id, UUID hackathonId, String nom, UUID chefId, List<UUID> membres, int tailleMax, Instant creeLe) {
	}

	record PageInscriptions(List<Inscription> elements, String curseurSuivant) {
	}

	record PageEquipes(List<Equipe> elements, String curseurSuivant) {
	}

	private static final String COLONNES = "id, hackathon_id, utilisateur_id, equipe_id, statut, cherche_equipe, competences, reponses, cree_le";

	private final JdbcClient base;

	private final Hackathons hackathons;

	private final ApplicationEventPublisher evenements;

	Inscriptions(JdbcClient base, Hackathons hackathons, ApplicationEventPublisher evenements) {
		this.base = base;
		this.hackathons = hackathons;
		this.evenements = evenements;
	}

	// ---------------------------------------------------------------- s'inscrire

	/**
	 * S'inscrire seul. Libre : `acceptee` tout de suite ; sur candidature : `en_attente`. Une seule inscription par
	 * personne (409 `deja_inscrit`, même avec deux requêtes simultanées). L'organisateur ne participe pas (403).
	 */
	@Transactional
	Inscription sInscrire(UUID hackathonId, UUID moi, boolean chercheEquipe, List<String> competences, Object reponses) {
		Hackathons.Hackathon h = inscriptionsOuvertes(hackathonId, moi);
		return inscrire(h, moi, chercheEquipe, competences, reponses);
	}

	/**
	 * Le seul endroit où naît une inscription : les réponses au questionnaire du hackathon y sont vérifiées, qu'on
	 * s'inscrive seul ou en créant son équipe (400 sous `reponses.<id>` si une réponse obligatoire manque).
	 */
	private Inscription inscrire(Hackathons.Hackathon h, UUID moi, boolean chercheEquipe, List<String> competences, Object brutes) {
		List<Map<String, String>> erreurs = new ArrayList<>();
		List<Questionnaire.Reponse> reponses = Questionnaire.verifier(brutes, h.questionnaire(), "reponses",
				(champ, code) -> erreurs.add(Map.of("champ", champ, "code", code)));
		if (!erreurs.isEmpty()) {
			throw ErreurHackathons.validation(erreurs);
		}
		UUID id = UUID.randomUUID();
		try {
			base.sql("""
					INSERT INTO inscription (id, hackathon_id, utilisateur_id, statut, cherche_equipe, competences, reponses, cree_le)
					VALUES (:id, :h, :u, :statut, :cherche, :competences, CAST(:reponses AS jsonb), :t)
					""")
				.param("id", id).param("h", h.id()).param("u", moi)
				.param("statut", "ouvert".equals(h.modeInscription()) ? "acceptee" : "en_attente").param("cherche", chercheEquipe)
				.param("competences", competences.toArray(String[]::new)).param("reponses", Questionnaire.enJson(reponses))
				.param("t", Timestamp.from(hackathons.maintenant()))
				.update();
		}
		catch (DuplicateKeyException e) {
			throw ErreurHackathons.conflit("deja_inscrit", "Tu es déjà inscrit à ce hackathon");
		}
		if (!"ouvert".equals(h.modeInscription())) {
			evenements.publishEvent(new InscriptionRecue(h.id(), id, h.organisateurId(), moi, hackathons.maintenant()));
		}
		return lireInscription(id);
	}

	// ---------------------------------------------------------------- les équipes

	/**
	 * Créer une équipe dont je suis le chef. Si je ne suis pas encore inscrit, l'inscription est créée en même temps.
	 * Déjà dans une équipe : 409 `deja_inscrit`.
	 */
	@Transactional
	Equipe creerEquipe(UUID hackathonId, UUID moi, String nom, Object reponses) {
		Hackathons.Hackathon h = inscriptionsOuvertes(hackathonId, moi);
		var existante = base.sql("SELECT id, equipe_id, statut FROM inscription WHERE hackathon_id = :h AND utilisateur_id = :u")
			.param("h", hackathonId).param("u", moi)
			.query((rs, i) -> new Object[] { rs.getObject("id", UUID.class), rs.getObject("equipe_id", UUID.class), rs.getString("statut") })
			.optional();
		if (existante.isPresent()) {
			if (existante.get()[1] != null) {
				throw ErreurHackathons.conflit("deja_inscrit", "Tu fais déjà partie d'une équipe");
			}
			if ("refusee".equals(existante.get()[2])) {
				throw ErreurHackathons.conflit("etat_invalide", "Ton inscription a été refusée");
			}
		}
		else {
			inscrire(h, moi, false, List.of(), reponses);
		}
		UUID equipe = UUID.randomUUID();
		base.sql("INSERT INTO equipe (id, hackathon_id, nom, chef_id, cree_le) VALUES (:id, :h, :nom, :chef, :t)")
			.param("id", equipe).param("h", hackathonId).param("nom", nom).param("chef", moi)
			.param("t", Timestamp.from(hackathons.maintenant())).update();
		base.sql("UPDATE inscription SET equipe_id = :e, cherche_equipe = FALSE WHERE hackathon_id = :h AND utilisateur_id = :u")
			.param("e", equipe).param("h", hackathonId).param("u", moi).update();
		return lireEquipe(equipe);
	}

	/**
	 * Inviter un participant dans mon équipe : le chef seulement. La personne doit être inscrite et acceptée, sans
	 * équipe ; l'équipe ne doit pas être pleine (409 `equipe_complete`). Elle la rejoint tout de suite.
	 */
	@Transactional
	Equipe inviter(UUID hackathonId, UUID equipeId, UUID moi, UUID invite) {
		Hackathons.Hackathon h = inscriptionsOuvertes(hackathonId, moi);
		Equipe equipe = base.sql("SELECT id FROM equipe WHERE id = :e AND hackathon_id = :h").param("e", equipeId).param("h", hackathonId)
			.query(UUID.class).optional().map(this::lireEquipe).orElseThrow(ErreurHackathons::introuvable);
		if (!equipe.chefId().equals(moi)) {
			throw ErreurHackathons.interdit();
		}
		var cible = base.sql("SELECT equipe_id, statut FROM inscription WHERE hackathon_id = :h AND utilisateur_id = :u")
			.param("h", hackathonId).param("u", invite)
			.query((rs, i) -> new Object[] { rs.getObject("equipe_id", UUID.class), rs.getString("statut") }).optional()
			.orElseThrow(ErreurHackathons::introuvable);
		if (cible[0] != null) {
			throw ErreurHackathons.conflit("deja_inscrit", "Cette personne fait déjà partie d'une équipe");
		}
		if (!"acceptee".equals(cible[1])) {
			throw ErreurHackathons.conflit("etat_invalide", "Cette inscription n'est pas acceptée");
		}
		if (equipe.membres().size() >= h.tailleMax()) {
			throw ErreurHackathons.conflit("equipe_complete", "Cette équipe est complète");
		}
		base.sql("UPDATE inscription SET equipe_id = :e, cherche_equipe = FALSE WHERE hackathon_id = :h AND utilisateur_id = :u")
			.param("e", equipeId).param("h", hackathonId).param("u", invite).update();
		evenements.publishEvent(new InvitationEquipe(hackathonId, equipeId, invite, moi, hackathons.maintenant()));
		return lireEquipe(equipeId);
	}

	// ---------------------------------------------------------------- trier les candidatures

	/**
	 * Accepter, refuser ou mettre en attente une inscription (organisateur, mode candidature). Refuser retire la
	 * personne de son équipe, et supprime l'équipe dont elle était le chef (ses membres redeviennent sans équipe).
	 */
	@Transactional
	Inscription trier(UUID hackathonId, UUID inscriptionId, UUID moi, String statut) {
		Hackathons.Hackathon h = hackathons.organisateurDe(hackathonId, moi);
		if (!"candidature".equals(h.modeInscription())) {
			throw ErreurHackathons.conflit("etat_invalide", "Les inscriptions de ce hackathon sont libres");
		}
		String etat = h.statut(hackathons.maintenant());
		if (!"publie".equals(etat) && !"en_cours".equals(etat)) {
			throw ErreurHackathons.conflit("etat_invalide", "Ce hackathon n'accepte plus de décisions");
		}
		UUID personne = base.sql("SELECT utilisateur_id FROM inscription WHERE id = :i AND hackathon_id = :h").param("i", inscriptionId)
			.param("h", hackathonId).query(UUID.class).optional().orElseThrow(ErreurHackathons::introuvable);
		base.sql("UPDATE inscription SET statut = :s WHERE id = :i").param("s", statut).param("i", inscriptionId).update();
		if ("refusee".equals(statut)) {
			base.sql("DELETE FROM equipe WHERE hackathon_id = :h AND chef_id = :u").param("h", hackathonId).param("u", personne).update();
			base.sql("UPDATE inscription SET equipe_id = NULL WHERE id = :i").param("i", inscriptionId).update();
		}
		evenements.publishEvent(new InscriptionTriee(hackathonId, inscriptionId, personne, statut, hackathons.maintenant()));
		return lireInscription(inscriptionId);
	}

	// ---------------------------------------------------------------- lectures

	/** Toutes les inscriptions, pour l'organisateur seulement, avec un filtre de statut. */
	@Transactional(readOnly = true)
	PageInscriptions lister(UUID hackathonId, UUID moi, String statut, int limite, String curseur) {
		hackathons.exigerOrganisateur(hackathonId, moi);
		return page(hackathonId, statut == null ? "" : " AND statut = :statut", statut, null, limite, curseur);
	}

	/** « Je cherche une équipe » : les participants acceptés, seuls et volontaires (pas moi), pour les participants seulement. */
	@Transactional(readOnly = true)
	PageInscriptions sansEquipe(UUID hackathonId, UUID moi, int limite, String curseur) {
		Hackathons.Lecture l = hackathons.role(hackathons.charger(hackathonId, false), moi).orElseThrow(ErreurHackathons::introuvable);
		if (!"participant".equals(l.role())) {
			throw ErreurHackathons.interdit();
		}
		return page(hackathonId, " AND equipe_id IS NULL AND cherche_equipe AND statut = 'acceptee' AND utilisateur_id <> :moi", null, moi, limite, curseur);
	}

	/** Les équipes d'un hackathon visible de la personne (public pour un hackathon publié). */
	@Transactional(readOnly = true)
	PageEquipes equipes(UUID hackathonId, UUID moi, int limite, String curseur) {
		hackathons.role(hackathons.charger(hackathonId, false), moi).orElseThrow(ErreurHackathons::introuvable);
		Curseur c = curseur == null ? null : Curseur.lire(curseur);
		var q = base.sql("SELECT id, cree_le FROM equipe WHERE hackathon_id = :h" + (c == null ? "" : " AND (cree_le, id) > (:t, :cid)")
				+ " ORDER BY cree_le, id LIMIT :n").param("h", hackathonId).param("n", limite + 1);
		if (c != null) {
			q = q.param("t", Timestamp.from(c.creeLe())).param("cid", c.id());
		}
		List<Curseur> lues = new ArrayList<>(q.query((rs, i) -> new Curseur(rs.getTimestamp("cree_le").toInstant(), rs.getObject("id", UUID.class))).list());
		String suivant = null;
		if (lues.size() > limite) {
			lues = new ArrayList<>(lues.subList(0, limite));
			suivant = lues.getLast().texte();
		}
		return new PageEquipes(lues.stream().map(l -> lireEquipe(l.id())).toList(), suivant);
	}

	private PageInscriptions page(UUID hackathonId, String filtre, String statut, UUID moi, int limite, String curseur) {
		Curseur c = curseur == null ? null : Curseur.lire(curseur);
		var q = base.sql("SELECT " + COLONNES + " FROM inscription WHERE hackathon_id = :h" + filtre
				+ (c == null ? "" : " AND (cree_le, id) > (:t, :cid)") + " ORDER BY cree_le, id LIMIT :n")
			.param("h", hackathonId).param("n", limite + 1);
		if (statut != null) {
			q = q.param("statut", statut);
		}
		if (moi != null) {
			q = q.param("moi", moi);
		}
		if (c != null) {
			q = q.param("t", Timestamp.from(c.creeLe())).param("cid", c.id());
		}
		List<Inscription> lues = new ArrayList<>(q.query(Inscriptions::inscription).list());
		String suivant = null;
		if (lues.size() > limite) {
			lues = new ArrayList<>(lues.subList(0, limite));
			suivant = new Curseur(lues.getLast().creeLe(), lues.getLast().id()).texte();
		}
		return new PageInscriptions(lues, suivant);
	}

	// ---------------------------------------------------------------- règles de la fenêtre d'inscription

	/**
	 * Le hackathon verrouillé, si on peut s'y inscrire ou y former une équipe maintenant. Dans l'ordre : il est visible
	 * (404), je ne suis pas l'organisateur (403), il est encore publié (409 `etat_invalide`), les inscriptions ont
	 * ouvert (409 `etat_invalide`) et n'ont pas fermé (409 `equipes_verrouillees`).
	 */
	private Hackathons.Hackathon inscriptionsOuvertes(UUID hackathonId, UUID moi) {
		Hackathons.Hackathon h = hackathons.charger(hackathonId, true);
		Hackathons.Lecture l = hackathons.role(h, moi).orElseThrow(ErreurHackathons::introuvable);
		if ("organisateur".equals(l.role())) {
			throw ErreurHackathons.interdit();
		}
		Instant maintenant = hackathons.maintenant();
		if (!"publie".equals(h.statutBase()) || h.calendrier().inscriptionsOuvertesLe().isAfter(maintenant)) {
			throw ErreurHackathons.conflit("etat_invalide", "Les inscriptions ne sont pas ouvertes");
		}
		if (!h.calendrier().inscriptionsFermeesLe().isAfter(maintenant)) {
			throw ErreurHackathons.conflit("equipes_verrouillees", "Les inscriptions sont fermées : les équipes sont verrouillées");
		}
		return h;
	}

	private Inscription lireInscription(UUID id) {
		return base.sql("SELECT " + COLONNES + " FROM inscription WHERE id = :id").param("id", id).query(Inscriptions::inscription).single();
	}

	/** Des équipes par leur identifiant, dans l'ordre demandé (pour les résultats, déjà filtrés par droit). */
	@Transactional(readOnly = true)
	List<Equipe> lireEquipes(List<UUID> ids) {
		return ids.stream().map(this::lireEquipe).toList();
	}

	private Equipe lireEquipe(UUID id) {
		var ligne = base.sql("""
				SELECT e.id, e.hackathon_id, e.nom, e.chef_id, e.cree_le, h.taille_max
				FROM equipe e JOIN hackathon h ON h.id = e.hackathon_id WHERE e.id = :id
				""").param("id", id)
			.query((rs, i) -> new Object[] { rs.getObject("id", UUID.class), rs.getObject("hackathon_id", UUID.class), rs.getString("nom"),
					rs.getObject("chef_id", UUID.class), rs.getTimestamp("cree_le").toInstant(), rs.getInt("taille_max") })
			.single();
		UUID chef = (UUID) ligne[3];
		// Le chef d'abord, puis les autres dans l'ordre d'arrivée.
		List<UUID> membres = base.sql("SELECT utilisateur_id FROM inscription WHERE equipe_id = :e ORDER BY (utilisateur_id <> :chef), cree_le, id")
			.param("e", id).param("chef", chef).query(UUID.class).list();
		return new Equipe((UUID) ligne[0], (UUID) ligne[1], (String) ligne[2], chef, membres, (Integer) ligne[5], (Instant) ligne[4]);
	}

	private static Inscription inscription(ResultSet rs, int ligne) throws SQLException {
		return new Inscription(rs.getObject("id", UUID.class), rs.getObject("hackathon_id", UUID.class),
				rs.getObject("utilisateur_id", UUID.class), rs.getObject("equipe_id", UUID.class), rs.getString("statut"),
				rs.getBoolean("cherche_equipe"), List.of((String[]) rs.getArray("competences").getArray()),
				Questionnaire.reponses(rs.getString("reponses")), rs.getTimestamp("cree_le").toInstant());
	}

	/** Le curseur : la date de création et l'identifiant du dernier élément de la page, en base 64 (opaque pour le front). */
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
				throw ErreurHackathons.validation("curseur", "invalide");
			}
		}

	}

}
