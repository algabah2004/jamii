package dev.jamii.api.auth;

import java.time.Instant;
import java.util.UUID;

/**
 * Ce qu'un module fait des données personnelles d'un compte, pour `GET /api/me/export` et `DELETE /api/me`
 * (contracts/socle.yaml). Tout module qui garde des données liées à un compte l'implémente : le module auth les
 * découvre ainsi sans dépendre d'aucun d'eux.
 */
public interface DonneesPersonnelles {

	/** Nom de la section de l'export (« projets », « cercles »…), unique entre les modules. */
	String section();

	/**
	 * Les données de ce compte dans ce module, prêtes pour le JSON. Jamais l'e-mail ni les données personnelles
	 * d'un autre compte : seulement des identifiants pour les autres.
	 */
	Object exporter(UUID utilisateurId);

	/**
	 * Appelé dans la transaction de suppression, avant l'effacement du compte : le module transmet ce qui doit lui
	 * survivre (un projet, un cercle). Les clés étrangères en cascade effacent le reste.
	 */
	default void avantSuppression(UUID utilisateurId, Instant maintenant) {
	}

}
