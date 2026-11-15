package dev.jamii.api.auth;

import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

/**
 * Au retour de GitHub ou de Google : lit le profil puis crée ou met à jour le compte Jamii ({@link ComptesGithub},
 * {@link ComptesGoogle}). La session ne garde que l'identifiant Jamii et les rôles. Le jeton d'accès n'est ni stocké
 * en base ni envoyé au navigateur.
 */
@Service
class ConnexionGithub implements OAuth2UserService<OAuth2UserRequest, OAuth2User> {

	private final OAuth2UserService<OAuth2UserRequest, OAuth2User> github = new DefaultOAuth2UserService();

	private final ComptesGithub comptes;

	private final ComptesGoogle comptesGoogle;

	ConnexionGithub(ComptesGithub comptes, ComptesGoogle comptesGoogle) {
		this.comptes = comptes;
		this.comptesGoogle = comptesGoogle;
	}

	@Override
	public OAuth2User loadUser(OAuth2UserRequest requete) {
		// Appel réseau à GitHub hors transaction : on ne bloque pas une connexion à la base pendant ce temps.
		OAuth2User profil = github.loadUser(requete);
		if ("google".equals(requete.getClientRegistration().getRegistrationId())) {
			return depuisGoogle(profil);
		}
		if (!(profil.getAttribute("id") instanceof Number githubId) || !(profil.getAttribute("login") instanceof String login)) {
			throw new OAuth2AuthenticationException(new OAuth2Error("profil_github_invalide"));
		}
		return comptes.enregistrer(githubId.longValue(), login, profil.getAttribute("name"), profil.getAttribute("avatar_url"));
	}

	/** Google : seule une adresse que Google a vérifiée ouvre un compte (`email_verified`). */
	UtilisateurConnecte depuisGoogle(OAuth2User profil) {
		Object verifie = profil.getAttribute("email_verified");
		if (!(profil.getAttribute("email") instanceof String email) || email.isBlank() || email.length() > 254
				|| !(Boolean.TRUE.equals(verifie) || "true".equals(verifie))) {
			throw new OAuth2AuthenticationException(new OAuth2Error("email_non_verifie"));
		}
		return comptesGoogle.enregistrer(email, profil.getAttribute("name"), profil.getAttribute("picture"));
	}

}
