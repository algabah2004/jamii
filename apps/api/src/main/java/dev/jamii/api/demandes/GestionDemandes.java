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

	/** Une adresse e-mail ou un numéro de téléphone n'est jamais envoyé à l'IA. */
	private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");

	private static final Pattern TELEPHONE = Pattern.compile("\\+?\\d[\\d .-]{7,}\\d");

	private static final Logger journal = LoggerFactory.getLogger(GestionDemandes.class);

	record Fiche(UUID id, UUID auteurId, String titre, String description, Integer dureeSemaines, String secteur,
			String ville, String pays, boolean offresSystalink, UUID tacheIaId, JsonNode orientation, Instant creeLe) {
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
			.param("accord", contexte.offresSystalink() ? Timestamp.from(maintenant) : null, java.sql.Types.TIMESTAMP_WITH_TIMEZONE)
			.param("t", Timestamp.from(maintenant)).update();
		return id;
	}

	/** La demande, si elle existe ET appartient à cet utilisateur : pour les autres, elle n'existe pas. */
	@Transactional(readOnly = true)
	Optional<Fiche> lire(UUID id, UUID auteur) {
		return base.sql("""
				SELECT id, auteur_id, titre, description, duree_semaines, secteur, ville, pays, offres_systalink,
				  tache_ia_id, orientation, cree_le
				FROM demande WHERE id = :id AND auteur_id = :auteur
				""")
			.param("id", id).param("auteur", auteur)
			.query((rs, n) -> {
				int duree = rs.getInt("duree_semaines");
				Integer dureeSemaines = rs.wasNull() ? null : duree;
				String orientation = rs.getString("orientation");
				return new Fiche(rs.getObject("id", UUID.class), rs.getObject("auteur_id", UUID.class), rs.getString("titre"),
						rs.getString("description"), dureeSemaines, rs.getString("secteur"), rs.getString("ville"),
						rs.getString("pays"), rs.getBoolean("offres_systalink"), rs.getObject("tache_ia_id", UUID.class),
						orientation == null ? null : json.readTree(orientation), rs.getTimestamp("cree_le").toInstant());
			})
			.optional();
	}

	/**
	 * L'état de l'orientation. Sans transaction : on n'attend jamais le service IA en tenant une connexion à la base.
	 * Service injoignable : `en_attente` pendant deux minutes (on réessaie à chaque lecture), puis `echouee`.
	 */
	Etat orientation(Fiche demande, Instant maintenant) {
		boolean tropVieux = Duration.between(demande.creeLe(), maintenant).compareTo(DELAI_PLAN) > 0;
		try {
			UUID tache = demande.tacheIaId();
			if (tache == null) {
				if (tropVieux) {
					return new Etat("echouee", null, null, "delai_depasse");
				}
				// La même clé d'idempotence : si un dépôt précédent est arrivé sans qu'on le sache, c'est la même tâche.
				tache = ia.deposer(demande.id(), new PlansIa.Entree(demande.titre(), sansCoordonnees(demande.description()),
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
