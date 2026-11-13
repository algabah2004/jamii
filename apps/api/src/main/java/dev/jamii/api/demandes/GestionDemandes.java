package dev.jamii.api.demandes;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;

/**
 * Les demandes de PME et l'état de leur plan (contracts/demandes.yaml). Le plan n'est jamais copié en base : il est
 * relu dans le service IA à chaque lecture. Le texte de la PME, lui, est toujours conservé, même si l'IA est en panne.
 */
@Service
class GestionDemandes {

	/** 10 plans par heure et par utilisateur (contracts/ia.yaml). */
	static final int PLANS_PAR_HEURE = 10;

	/** Au-delà, un service IA qui ne répond toujours pas fait passer le plan à « échoué ». */
	static final Duration DELAI_PLAN = Duration.ofMinutes(2);

	private static final Logger journal = LoggerFactory.getLogger(GestionDemandes.class);

	record Fiche(UUID id, UUID auteurId, String titre, String description, Integer dureeSemaines, UUID tacheIaId,
			Instant creeLe) {
	}

	/** Le plan tel que le navigateur le voit. Les champs absents ne sont pas écrits. */
	record Plan(String statut, String origine, JsonNode resultat, String erreurCode) {
	}

	/** Trop de plans demandés : à reprendre dans `attenteSecondes`. */
	static final class TropDePlans extends RuntimeException {

		final long attenteSecondes;

		TropDePlans(long attenteSecondes) {
			super(null, null, false, false);
			this.attenteSecondes = attenteSecondes;
		}

	}

	private final JdbcClient base;

	private final PlansIa ia;

	GestionDemandes(JdbcClient base, PlansIa ia) {
		this.base = base;
		this.ia = ia;
	}

	/** Enregistre la demande si l'auteur n'a pas dépassé sa limite. Une demande refusée n'est pas enregistrée. */
	@Transactional
	UUID creer(UUID auteur, String titre, String description, Integer dureeSemaines, Instant maintenant) {
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
				INSERT INTO demande (id, auteur_id, titre, description, duree_semaines, cree_le)
				VALUES (:id, :auteur, :titre, :description, :duree, :t)
				""")
			.param("id", id).param("auteur", auteur).param("titre", titre).param("description", description)
			.param("duree", dureeSemaines, java.sql.Types.SMALLINT).param("t", Timestamp.from(maintenant)).update();
		return id;
	}

	/** La demande, si elle existe ET appartient à cet utilisateur : pour les autres, elle n'existe pas. */
	@Transactional(readOnly = true)
	Optional<Fiche> lire(UUID id, UUID auteur) {
		return base.sql("""
				SELECT id, auteur_id, titre, description, duree_semaines, tache_ia_id, cree_le
				FROM demande WHERE id = :id AND auteur_id = :auteur
				""")
			.param("id", id).param("auteur", auteur)
			.query((rs, n) -> {
				int duree = rs.getInt("duree_semaines");
				Integer dureeSemaines = rs.wasNull() ? null : duree;
				return new Fiche(rs.getObject("id", UUID.class), rs.getObject("auteur_id", UUID.class), rs.getString("titre"),
						rs.getString("description"), dureeSemaines, rs.getObject("tache_ia_id", UUID.class),
						rs.getTimestamp("cree_le").toInstant());
			})
			.optional();
	}

	/**
	 * L'état du plan. Sans transaction : on n'attend jamais le service IA en tenant une connexion à la base.
	 * Service injoignable : `en_attente` pendant deux minutes (on réessaie à chaque lecture), puis `echouee`.
	 */
	Plan plan(Fiche demande, Instant maintenant) {
		boolean tropVieux = Duration.between(demande.creeLe(), maintenant).compareTo(DELAI_PLAN) > 0;
		try {
			UUID tache = demande.tacheIaId();
			if (tache == null) {
				if (tropVieux) {
					return new Plan("echouee", null, null, "delai_depasse");
				}
				// La même clé d'idempotence : si un dépôt précédent est arrivé sans qu'on le sache, c'est la même tâche.
				tache = ia.deposer(demande.id(), new PlansIa.Entree(demande.titre(), demande.description(), demande.dureeSemaines()));
				retenirTache(demande.id(), tache);
			}
			PlansIa.Etat etat = ia.lire(tache);
			return new Plan(etat.statut(), etat.origine(), etat.resultat(), etat.erreurCode());
		}
		catch (RuntimeException e) {
			// Ni le texte de la PME ni le jeton dans les journaux : seulement le type de l'erreur.
			journal.warn("Service IA injoignable pour une demande : {}", e.getClass().getSimpleName());
			return tropVieux ? new Plan("echouee", null, null, "delai_depasse") : new Plan("en_attente", null, null, null);
		}
	}

	@Transactional
	void retenirTache(UUID demandeId, UUID tacheId) {
		base.sql("UPDATE demande SET tache_ia_id = :t WHERE id = :id AND tache_ia_id IS NULL")
			.param("t", tacheId).param("id", demandeId).update();
	}

}
