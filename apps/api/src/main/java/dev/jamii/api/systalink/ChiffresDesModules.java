package dev.jamii.api.systalink;

import java.time.Instant;
import java.util.Map;

/**
 * Ce que le tableau de bord demande aux autres modules, sans jamais lire leurs tables (docs/modele-donnees.md §6).
 * Chaque module implémente l'interface qui le concerne avec un bean Spring. Tant qu'il ne l'a pas fait,
 * le tableau affiche 0 : on ne montre jamais un chiffre inventé.
 */
public final class ChiffresDesModules {

	private ChiffresDesModules() {
	}

	/** Module auth (#17) ou profils (#19). */
	public interface Inscrits {

		/** Inscrits créés à partir de `depuis` (tous si `depuis` est null). */
		long compter(Instant depuis);

		/** Inscrits par pays déclaré (code ISO 3166-1 alpha-2). Ceux sans pays n'y figurent pas. */
		Map<String, Long> parPays();

	}

	/** Module projets (#17). */
	public interface Projets {

		/** Projets créés à partir de `depuis` (tous si `depuis` est null). */
		long compter(Instant depuis);

	}

	/** Module deploiement (#20). */
	public interface Deploiements {

		/** Nombre de projets distincts avec un déploiement `en_ligne` vérifié, quel que soit l'hébergeur. */
		long projetsEnLigne();

		/** Parmi eux, ceux hébergés sur DataCloud. */
		long projetsSurDatacloud();

	}

}
