package dev.jamii.api.profils;

import java.time.Instant;
import java.util.List;

/**
 * Lit les données publiques GitHub d'un compte. Deux adaptateurs, choisis par la variable GITHUB_SOURCE :
 * {@link SourceGithubHttp} (l'API de GitHub) et {@link SourceGithubSimulee} (données factices, pour les tests et la démo).
 */
interface SourceGithub {

	/** Les dépôts publics dont le compte est propriétaire, forks compris (on les écarte ensuite). */
	List<Depot> depotsPublics(String login);

	record Depot(String nom, String url, String description, String langage, int etoiles, int tailleKo, boolean fork,
			List<String> sujets, Instant pousseLe) {
	}

	/** Le compte n'existe pas ou n'est plus visible sur GitHub. */
	class CompteIntrouvable extends RuntimeException {

		CompteIntrouvable(String login) {
			super("Compte GitHub introuvable : " + login);
		}

	}

}
