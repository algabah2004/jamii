package dev.jamii.api.systalink;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Ce que le tableau de bord demande aux autres modules, sans jamais lire leurs tables (docs/modele-donnees.md §6).
 * Chaque module implémente l'interface qui le concerne avec un bean Spring. Tant qu'il ne l'a pas fait,
 * le tableau affiche 0 : on ne montre jamais un chiffre inventé.
 */
public final class ChiffresDesModules {

	private ChiffresDesModules() {
	}

	/**
	 * Module profils (#19) : le pays est déclaré dans le profil. Le total des inscrits, lui, vient directement de
	 * {@code auth.Annuaire} (#17, #57).
	 */
	public interface PaysDesInscrits {

		/** Inscrits par pays déclaré (code ISO 3166-1 alpha-2). Ceux sans pays n'y figurent pas. */
		Map<String, Long> parPays();

		/**
		 * Même compte, limité à ces personnes (les membres des cercles). Tant que le module profils ne l'a pas écrit,
		 * aucun pays n'est connu : tous comptent dans « autres ».
		 */
		default Map<String, Long> parPays(Collection<UUID> parmi) {
			return Map.of();
		}

	}

	/** Module cercles (#98) : la Tech Tontine. */
	public interface Cercles {

		/** Cercles `ouvert` ou `en_cours`. */
		long actifs();

		/** Cercles dont tous les tours sont livrés. */
		long termines();

		/** Tours livrés à partir de `depuis` (tous si `depuis` est null). */
		long toursLivres(Instant depuis);

		/**
		 * Les personnes qui font partie d'au moins un cercle, chacune une fois. Ces identifiants ne quittent jamais
		 * l'API : le tableau de bord n'en montre que le nombre, et leur répartition par pays.
		 */
		Set<UUID> membres();

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
