package dev.jamii.api.profils;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Ce qu'un module sait du travail d'une personne sur Jamii (contracts/profils.yaml : `projets`, `fiabilite` et
 * `GET /api/profils/{id}/preuves`). Chaque module l'implémente avec ses propres tables : les projets et les tâches
 * (projets), les réponses acceptées (communautes), les avis de missions (missions). Le module profils ne lit jamais
 * leurs tables ; il assemble ({@link Preuves}).
 * <p>
 * Le sens de la dépendance est voulu : demandes dépend déjà de profils, et projets ou missions de demandes. Si profils
 * appelait ces modules, il y aurait un cycle. Ce sont donc eux qui viennent se brancher ici, comme pour les chiffres
 * de l'espace Systalink (systalink.ChiffresDesModules).
 * <p>
 * Rien n'est déclaré par la personne : tout est calculé à partir de ce qui s'est passé sur Jamii, au moment de la
 * lecture. Les chiffres sont donc toujours à jour et vérifiables en un clic.
 */
public interface SourcePreuves {

	/** Les quatre preuves de l'indice de fiabilité (contracts/profils.yaml, `TypePreuve`). */
	String TACHE_LIVREE_A_TEMPS = "tache_livree_a_temps";

	String AVIS_COEQUIPIER = "avis_coequipier";

	String REPONSE_ACCEPTEE = "reponse_acceptee";

	String PROJET_EN_LIGNE = "projet_en_ligne";

	/**
	 * Un projet Jamii de la personne. `equipe` : les identifiants des membres, dont profils tire les pays.
	 * `hebergeur` : `datacloud`, `autre` ou null.
	 */
	record Projet(UUID projetId, String titre, String role, List<UUID> equipe, boolean enLigne, String url, String hebergeur,
			Instant verifieLe, Instant derniereActivite) {
	}

	/** Le chiffre d'une preuve : `sur` seulement pour les tâches (livrées à temps sur livrées avec une échéance). */
	record Point(String type, int nombre, Integer sur) {
	}

	/** Une preuve, pour la vérifier en un clic. `module` : projets, communautes ou missions. */
	record Preuve(UUID id, String type, String module, UUID sourceId, String titre, String detail, UUID auteurId,
			Instant creeLe) {
	}

	/** Ses projets Jamii, si ce module en a. */
	default List<Projet> projets(UUID personne) {
		return List.of();
	}

	/** Ses chiffres, un par type que ce module prouve (rien pour un type sans preuve). */
	List<Point> points(UUID personne);

	/** Le détail d'un type, les plus récentes d'abord ; vide si ce module ne le prouve pas. */
	List<Preuve> preuves(UUID personne, String type, int limite);

}
