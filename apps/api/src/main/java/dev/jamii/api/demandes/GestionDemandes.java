package dev.jamii.api.demandes;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Les demandes et leur orientation (contracts/demandes.yaml v0.2). L'orientation complète (et son plan) vit dans le
 * service IA, relue à chaque lecture. Seul son résumé est recopié une fois terminée, pour les tendances et les clients
 * suivis de Systalink. Le texte de l'auteur, lui, est toujours conservé, même si l'IA est en panne.
 */
@Service
class GestionDemandes {

	/** 10 orientations par heure et par utilisateur (contracts/ia.yaml). */
	static final int PLANS_PAR_HEURE = 10;

	/** Au-delà, un service IA qui ne répond toujours pas fait passer l'orientation à « échouée ». */
	static final Duration DELAI_PLAN = Duration.ofMinutes(2);

	static final int PRECISIONS_MAX = 5;

	/** Une adresse e-mail ou un numéro de téléphone n'est jamais envoyé à l'IA. */
	private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");

	private static final Pattern TELEPHONE = Pattern.compile("\\+?\\d[\\d .-]{7,}\\d");

	private static final Logger journal = LoggerFactory.getLogger(GestionDemandes.class);

	record Fiche(UUID id, UUID auteurId, String titre, String description, Integer dureeSemaines, String secteur,
			String ville, String pays, boolean offresSystalink, UUID tacheIaId, JsonNode orientation, Instant creeLe,
			Instant publieeLe, String publieeResume, List<Precision> precisions) {

		/** Le début de l'analyse en cours : la création, ou la dernière précision. */
		Instant analyseDepuis() {
			return precisions.isEmpty() ? creeLe : precisions.getLast().creeLe();
		}

		/**
		 * Une clé d'idempotence par version du besoin : la même tant que rien n'est ajouté (un dépôt arrivé sans qu'on
		 * le sache donne la même tâche), une nouvelle à chaque précision.
		 */
		UUID cleIa() {
			return precisions.isEmpty() ? id
					: UUID.nameUUIDFromBytes((id + ":" + precisions.size()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
		}

		/** Le besoin tel que l'IA le lit : le texte de départ, puis les précisions, sans dépasser 4 000 caractères. */
		String besoinComplet() {
			if (precisions.isEmpty()) {
				return description;
			}
			StringBuilder ajout = new StringBuilder("\n\nPrécisions de la personne :");
			precisions.forEach(p -> ajout.append("\n- ").append(p.texte()));
			int place = Math.max(20, 4000 - ajout.length());
			return (description.length() > place ? description.substring(0, place) : description) + ajout;
		}

	}

	record Precision(String texte, Instant creeLe) {
	}

	/** Une ligne de « Tes demandes ». */
	record Resume(UUID id, String titre, String resume, Instant creeLe, int precisions, boolean publiee) {
	}

	/** Ce que l'auteur ajoute à son besoin, tout facultatif. */
	record Contexte(String secteur, String ville, String pays, boolean offresSystalink) {
	}

	/** L'état de l'orientation, tel que le service IA le décrit. Les champs absents ne sont pas écrits. */
	record Etat(String statut, String origine, JsonNode resultat, String erreurCode) {
	}

	/** Trop de demandes : à reprendre dans `attenteSecondes`. */
	static final class TropDePlans extends RuntimeException {

		final long attenteSecondes;

		TropDePlans(long attenteSecondes) {
			super(null, null, false, false);
			this.attenteSecondes = attenteSecondes;
		}

	}

	private final JdbcClient base;

	private final PlansIa ia;

	private final JsonMapper json;

	GestionDemandes(JdbcClient base, PlansIa ia, JsonMapper json) {
		this.base = base;
		this.ia = ia;
		this.json = json;
	}

	/** Enregistre la demande si l'auteur n'a pas dépassé sa limite. Une demande refusée n'est pas enregistrée. */
	@Transactional
	UUID creer(UUID auteur, String titre, String description, Integer dureeSemaines, Contexte contexte,
			Instant maintenant) {
		List<Instant> recentes = base.sql("SELECT cree_le FROM demande WHERE auteur_id = :a AND cree_le > :depuis ORDER BY cree_le")
			.param("a", auteur)
			.param("depuis", Timestamp.from(maintenant.minus(Duration.ofHours(1))))
			.query((rs, n) -> rs.getTimestamp("cree_le").toInstant())
			.list();
		if (recentes.size() >= PLANS_PAR_HEURE) {
			throw new TropDePlans(Math.max(1, Duration.between(maintenant, recentes.getFirst().plus(Duration.ofHours(1))).toSeconds()));
		}
		UUID id = UUID.randomUUID();
		base.sql("""
				INSERT INTO demande (id, auteur_id, titre, description, duree_semaines, secteur, ville, pays,
				  offres_systalink, offres_accord_le, cree_le)
				VALUES (:id, :auteur, :titre, :description, :duree, :secteur, :ville, :pays, :offres, :accord, :t)
				""")
			.param("id", id).param("auteur", auteur).param("titre", titre).param("description", description)
			.param("duree", dureeSemaines, java.sql.Types.SMALLINT)
			.param("secteur", contexte.secteur(), java.sql.Types.VARCHAR)
			.param("ville", contexte.ville(), java.sql.Types.VARCHAR)
			.param("pays", contexte.pays(), java.sql.Types.CHAR)
			.param("offres", contexte.offresSystalink())
			.param("accord", contexte.offresSystalink() ? Timestamp.from(maintenant) : null, java.sql.Types.TIMESTAMP)
			.param("t", Timestamp.from(maintenant)).update();
		return id;
	}

	/** La demande, si elle existe ET appartient à cet utilisateur : pour les autres, elle n'existe pas. */
	@Transactional(readOnly = true)
	Optional<Fiche> lire(UUID id, UUID auteur) {
		return base.sql("""
				SELECT id, auteur_id, titre, description, duree_semaines, secteur, ville, pays, offres_systalink,
				  tache_ia_id, orientation, cree_le, publiee_le, publiee_resume
				FROM demande WHERE id = :id AND auteur_id = :auteur
				""")
			.param("id", id).param("auteur", auteur)
			.query((rs, n) -> {
				int duree = rs.getInt("duree_semaines");
				Integer dureeSemaines = rs.wasNull() ? null : duree;
				String orientation = rs.getString("orientation");
				Timestamp publiee = rs.getTimestamp("publiee_le");
				return new Fiche(rs.getObject("id", UUID.class), rs.getObject("auteur_id", UUID.class), rs.getString("titre"),
						rs.getString("description"), dureeSemaines, rs.getString("secteur"), rs.getString("ville"),
						rs.getString("pays"), rs.getBoolean("offres_systalink"), rs.getObject("tache_ia_id", UUID.class),
						orientation == null ? null : json.readTree(orientation), rs.getTimestamp("cree_le").toInstant(),
						publiee == null ? null : publiee.toInstant(), rs.getString("publiee_resume"), List.<Precision>of());
			})
			.optional()
			.map(f -> new Fiche(f.id(), f.auteurId(), f.titre(), f.description(), f.dureeSemaines(), f.secteur(), f.ville(),
					f.pays(), f.offresSystalink(), f.tacheIaId(), f.orientation(), f.creeLe(), f.publieeLe(), f.publieeResume(),
						precisions(f.id())));
	}

	/**
	 * L'état de l'orientation. Sans transaction : on n'attend jamais le service IA en tenant une connexion à la base.
	 * Service injoignable : `en_attente` pendant deux minutes (on réessaie à chaque lecture), puis `echouee`. Même après,
	 * chaque lecture (« Réessayer ») retente le dépôt : une demande créée pendant une panne repart dès que l'IA revient.
	 */
	Etat orientation(Fiche demande, Instant maintenant) {
		boolean tropVieux = Duration.between(demande.analyseDepuis(), maintenant).compareTo(DELAI_PLAN) > 0;
		try {
			UUID tache = demande.tacheIaId();
			if (tache == null) {
				// La même clé d'idempotence : si un dépôt précédent est arrivé sans qu'on le sache, c'est la même tâche.
				tache = ia.deposer(demande.cleIa(), new PlansIa.Entree(demande.titre(), sansCoordonnees(demande.besoinComplet()),
						demande.dureeSemaines(), demande.secteur(), demande.pays()));
				retenirTache(demande.id(), tache);
			}
			PlansIa.Etat etat = ia.lire(tache);
			if ("terminee".equals(etat.statut()) && etat.resultat() != null && demande.orientation() == null) {
				retenirResume(demande.id(), etat.resultat());
			}
			return new Etat(etat.statut(), etat.origine(), etat.resultat(), etat.erreurCode());
		}
		catch (RuntimeException e) {
			// Ni le texte de l'auteur ni le jeton dans les journaux : seulement le type de l'erreur.
			journal.warn("Service IA injoignable pour une demande : {}", e.getClass().getSimpleName());
			return tropVieux ? new Etat("echouee", null, null, "delai_depasse") : new Etat("en_attente", null, null, null);
		}
	}

	/** Accorder ou retirer « Recevoir les offres Systalink adaptées à mon projet ». Faux si la demande n'est pas à lui. */
	@Transactional
	boolean choisirOffres(UUID id, UUID auteur, boolean accepte, Instant maintenant) {
		return base.sql("""
				UPDATE demande SET offres_systalink = :accepte,
				  offres_accord_le = CASE WHEN :accepte THEN coalesce(offres_accord_le, :t) ELSE NULL END
				WHERE id = :id AND auteur_id = :auteur
				""")
			.param("accepte", accepte).param("t", Timestamp.from(maintenant)).param("id", id).param("auteur", auteur)
			.update() == 1;
	}

	private List<Precision> precisions(UUID demandeId) {
		return base.sql("SELECT texte, cree_le FROM demande_precision WHERE demande_id = :d ORDER BY rang")
			.param("d", demandeId)
			.query((rs, n) -> new Precision(rs.getString("texte"), rs.getTimestamp("cree_le").toInstant()))
			.list();
	}

	/** Les demandes de cet auteur, les plus récentes d'abord. */
	@Transactional(readOnly = true)
	List<Resume> mesDemandes(UUID auteur) {
		return base.sql("""
				SELECT d.id, d.titre, d.orientation ->> 'resume' AS resume, d.cree_le, d.publiee_le IS NOT NULL AS publiee,
				  (SELECT count(*) FROM demande_precision p WHERE p.demande_id = d.id) AS precisions
				FROM demande d
				WHERE d.auteur_id = :a ORDER BY d.cree_le DESC, d.id LIMIT 50
				""")
			.param("a", auteur)
			.query((rs, n) -> new Resume(rs.getObject("id", UUID.class), rs.getString("titre"), rs.getString("resume"),
					rs.getTimestamp("cree_le").toInstant(), rs.getInt("precisions"), rs.getBoolean("publiee")))
			.list();
	}

	enum Ajout {

		AJOUTEE, INTROUVABLE, LIMITE

	}

	/**
	 * Ajoute une précision et relance l'analyse : la tâche et le résumé précédents sont oubliés, la prochaine lecture
	 * dépose une nouvelle tâche avec le besoin complet. 5 précisions au plus par demande.
	 */
	@Transactional
	Ajout preciser(UUID demandeId, UUID auteur, String texte, Instant maintenant) {
		Integer rang = base.sql("""
				SELECT coalesce(max(p.rang), 0) + 1 FROM demande d LEFT JOIN demande_precision p ON p.demande_id = d.id
				WHERE d.id = :d AND d.auteur_id = :a GROUP BY d.id
				""")
			.param("d", demandeId).param("a", auteur).query(Integer.class).optional().orElse(null);
		if (rang == null) {
			return Ajout.INTROUVABLE;
		}
		if (rang > PRECISIONS_MAX) {
			return Ajout.LIMITE;
		}
		base.sql("INSERT INTO demande_precision (demande_id, rang, texte, cree_le) VALUES (:d, :r, :t, :c)")
			.param("d", demandeId).param("r", rang).param("t", texte).param("c", Timestamp.from(maintenant)).update();
		base.sql("UPDATE demande SET tache_ia_id = NULL, orientation = NULL WHERE id = :d").param("d", demandeId).update();
		return Ajout.AJOUTEE;
	}

	@Transactional
	void retenirTache(UUID demandeId, UUID tacheId) {
		base.sql("UPDATE demande SET tache_ia_id = :t WHERE id = :id AND tache_ia_id IS NULL")
			.param("t", tacheId).param("id", demandeId).update();
	}

	/** Seulement le résumé : jamais le plan, jamais le texte d'origine. */
	@Transactional
	void retenirResume(UUID demandeId, JsonNode orientation) {
		ObjectNode resume = json.createObjectNode();
		resume.put("resume", texte(orientation, "resume"));
		resume.put("secteur", texte(orientation, "secteur"));
		resume.put("developpeursNecessaires", orientation.path("developpeursNecessaires").asBoolean(false));
		var services = resume.putArray("services");
		orientation.path("services").forEach(service -> services.add(service.path("id").asString("")));
		var competences = resume.putArray("competences");
		orientation.path("competences").forEach(competence -> competences.add(competence.asString("")));
		base.sql("UPDATE demande SET orientation = CAST(:o AS jsonb) WHERE id = :id AND orientation IS NULL")
			.param("o", json.writeValueAsString(resume)).param("id", demandeId).update();
	}

	static String sansCoordonnees(String texte) {
		return TELEPHONE.matcher(EMAIL.matcher(texte).replaceAll("[e-mail retiré]")).replaceAll("[téléphone retiré]");
	}

	private static String texte(JsonNode noeud, String champ) {
		JsonNode valeur = noeud.get(champ);
		return valeur == null || valeur.isNull() ? null : valeur.asString();
	}

}
