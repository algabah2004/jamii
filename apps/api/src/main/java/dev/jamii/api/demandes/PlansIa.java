package dev.jamii.api.demandes;

import java.util.UUID;

import tools.jackson.databind.JsonNode;

/**
 * Le service IA qui oriente une demande (tâche `orientation_besoin` de contracts/ia.yaml : services Systalink,
 * compétences nécessaires et plan si besoin, en un seul appel). Le code métier ne parle
 * à aucun fournisseur : il passe par cette interface (AGENTS.md §3). Choisie par la configuration `IA_FOURNISSEUR` :
 * `simule` (sans réseau : local, tests, démo) ou `service` (le service IA de l'équipe, en HTTP).
 */
interface PlansIa {

	/**
	 * Ce que reçoit l'IA : le besoin, avec les mots de son auteur, sans e-mail ni téléphone (retirés avant). Aucun nom
	 * ni identifiant d'utilisateur.
	 */
	record Entree(String titre, String description, Integer dureeSemaines, String secteur, String pays) {
	}

	/** L'état d'une tâche, tel que le service IA le décrit. `resultat` est l'`Orientation` de ia.yaml. */
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
