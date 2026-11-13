package dev.jamii.api.demandes;

import java.util.UUID;

import tools.jackson.databind.JsonNode;

/**
 * Le service IA qui transforme une demande en plan (tâche `plan_projet` de contracts/ia.yaml). Le code métier ne parle
 * à aucun fournisseur : il passe par cette interface (AGENTS.md §3). Choisie par la configuration `IA_FOURNISSEUR` :
 * `simule` (sans réseau : local, tests, démo) ou `service` (le service IA de l'équipe, en HTTP).
 */
interface PlansIa {

	/** Ce que reçoit l'IA : le besoin, avec les mots de la PME. Aucun nom, e-mail ou identifiant d'utilisateur. */
	record Entree(String titre, String description, Integer dureeSemaines) {
	}

	/** L'état d'une tâche, tel que le service IA le décrit. `resultat` est le `PlanProjet` de ia.yaml, transmis tel quel. */
	record Etat(String statut, String origine, JsonNode resultat, String erreurCode) {
	}

	/**
	 * Dépose la tâche et renvoie son identifiant. `cle` rend l'appel idempotent : la rejouer ne crée pas une deuxième
	 * tâche (en-tête `Idempotency-Key`). Lève une exception si le service est injoignable.
	 */
	UUID deposer(UUID cle, Entree entree);

	/** Lit l'état de la tâche. Lève une exception si le service est injoignable ou ne connaît plus la tâche. */
	Etat lire(UUID tacheId);

}
