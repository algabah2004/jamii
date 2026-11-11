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
