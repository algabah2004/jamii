package dev.jamii.api.auth;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Crée le compte à la première connexion GitHub, ou le met à jour. Reconnu par l'identifiant numérique GitHub. */
@Service
class ComptesGithub {

	private final UtilisateurRepository utilisateurs;

	ComptesGithub(UtilisateurRepository utilisateurs) {
		this.utilisateurs = utilisateurs;
	}

	/**
	 * Relie ce compte GitHub au compte déjà connecté. Refusé si ce GitHub appartient déjà à un autre compte Jamii
	 * (`github_deja_utilise`), ou si ce compte est déjà relié à un autre GitHub (`compte_deja_relie`) : on ne vole pas
	 * un compte, et on n'en écrase pas un.
	 */
	@Transactional
	UtilisateurConnecte lier(java.util.UUID moi, long githubId, String login, String avatarUrl) {
		Utilisateur utilisateur = utilisateurs.findById(moi)
			.orElseThrow(() -> new org.springframework.security.oauth2.core.OAuth2AuthenticationException(
					new org.springframework.security.oauth2.core.OAuth2Error("requete_invalide")));
		if (utilisateur.githubId() != null && utilisateur.githubId() != githubId) {
			throw new org.springframework.security.oauth2.core.OAuth2AuthenticationException(
					new org.springframework.security.oauth2.core.OAuth2Error("compte_deja_relie"));
		}
		utilisateurs.findByGithubId(githubId).filter(autre -> !autre.id().equals(moi)).ifPresent(autre -> {
			throw new org.springframework.security.oauth2.core.OAuth2AuthenticationException(
					new org.springframework.security.oauth2.core.OAuth2Error("github_deja_utilise"));
		});
		utilisateur.lierGithub(githubId, login, avatarUrl, Instant.now());
		utilisateur = utilisateurs.save(utilisateur);
		return new UtilisateurConnecte(utilisateur.id(), utilisateur.roles());
	}

	@Transactional
	UtilisateurConnecte enregistrer(long githubId, String login, String nom, String avatarUrl) {
		Instant maintenant = Instant.now();
		Utilisateur utilisateur = utilisateurs.findByGithubId(githubId)
			.orElseGet(() -> Utilisateur.depuisGithub(githubId, maintenant));
		utilisateur.majDepuisGithub(login, nom, avatarUrl, maintenant);
		utilisateur = utilisateurs.save(utilisateur);
		return new UtilisateurConnecte(utilisateur.id(), utilisateur.roles());
	}

}
