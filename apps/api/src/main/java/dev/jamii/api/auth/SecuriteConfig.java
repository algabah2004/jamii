package dev.jamii.api.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;

/**
 * Règles de sécurité de toute l'API (docs/api-conventions.md §5).
 * Tout est fermé par défaut : une route publique doit être ouverte ici, explicitement.
 */
@Configuration
class SecuriteConfig {

	static final String CONNEXION_GITHUB = "/api/auth/github";

	private static final String ATTRIBUT_RETOUR = "jamii.retour";

	@Bean
	SecurityFilterChain securite(HttpSecurity http, ConnexionGithub connexionGithub, ErreursSecurite erreurs,
			ClientRegistrationRepository clients, @Value("${jamii.web-url}") String webUrl) {
		http.authorizeHttpRequests(acces -> acces
				// /error : pour qu'une route publique inconnue réponde 404, et non 401.
				.requestMatchers("/health", "/health/**", "/api/auth/**", "/error").permitAll()
				// Lectures publiques, sans connexion (le jury, un visiteur) : uniquement ces deux routes et seulement
				// en GET. Elles ne montrent que des chiffres agrégés et ce que les projets montrent déjà à tous
				// (contracts/systalink.yaml et contracts/vitrine.yaml, `security: []`). Toute autre route reste fermée.
				.requestMatchers(HttpMethod.GET, "/api/systalink/tableau-de-bord", "/api/vitrine").permitAll()
				.anyRequest().authenticated())
			.oauth2Login(oauth -> oauth
				.authorizationEndpoint(debut -> debut.authorizationRequestResolver(resolveurAvecRetour(clients)))
				.redirectionEndpoint(retour -> retour.baseUri("/api/auth/*/retour"))
				.userInfoEndpoint(profil -> profil.userService(connexionGithub))
				.successHandler((requete, reponse, auth) -> reponse.sendRedirect(webUrl + retirerRetour(requete)))
				.failureHandler((requete, reponse, echec) -> reponse
					.sendRedirect(webUrl + "/connexion?erreur=" + codeEchec(echec))))
			.logout(sortie -> sortie.logoutUrl("/api/auth/deconnexion")
				.logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
				.deleteCookies("JAMII_SESSION"))
			// Jeton CSRF dans le cookie XSRF-TOKEN, à renvoyer dans l'en-tête X-XSRF-TOKEN.
			.csrf(csrf -> csrf.spa())
			// Une API ne redirige jamais vers une page de connexion : elle répond 401 ou 403 en JSON.
			.exceptionHandling(refus -> refus
				.authenticationEntryPoint((requete, reponse, e) -> erreurs.nonConnecte(requete, reponse))
				.accessDeniedHandler((requete, reponse, e) -> erreurs.interdit(requete, reponse)))
			.requestCache(cache -> cache.disable())
			.headers(entetes -> entetes
				.contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'")));
		return http.build();
	}

	/**
	 * Ne démarre la connexion que sur /api/auth/github (les autres routes /api/auth/… ne sont pas des fournisseurs),
	 * et retient en session le chemin « retour » s'il est sûr.
	 */
	static OAuth2AuthorizationRequestResolver resolveurAvecRetour(ClientRegistrationRepository clients) {
		var parDefaut = new DefaultOAuth2AuthorizationRequestResolver(clients, "/api/auth");
		return new OAuth2AuthorizationRequestResolver() {

			@Override
			public OAuth2AuthorizationRequest resolve(HttpServletRequest requete) {
				String chemin = requete.getRequestURI().substring(requete.getContextPath().length());
				if (!CONNEXION_GITHUB.equals(chemin)) {
					return null;
				}
				requete.getSession().setAttribute(ATTRIBUT_RETOUR, CheminRetour.valider(requete.getParameter("retour")));
				return parDefaut.resolve(requete);
			}

			@Override
			public OAuth2AuthorizationRequest resolve(HttpServletRequest requete, String fournisseur) {
				return parDefaut.resolve(requete, fournisseur);
			}

		};
	}

	static String retirerRetour(HttpServletRequest requete) {
		HttpSession session = requete.getSession(false);
		if (session == null || !(session.getAttribute(ATTRIBUT_RETOUR) instanceof String retour)) {
			return CheminRetour.PAR_DEFAUT;
		}
		session.removeAttribute(ATTRIBUT_RETOUR);
		return CheminRetour.valider(retour);
	}

	/** Codes du contrat : refus_github, github_indisponible, requete_invalide. */
	static String codeEchec(Exception echec) {
		if (!(echec instanceof OAuth2AuthenticationException oauth)) {
			return "github_indisponible";
		}
		return switch (oauth.getError().getErrorCode()) {
			case "access_denied" -> "refus_github";
			case "invalid_state_parameter", "authorization_request_not_found", "invalid_request" -> "requete_invalide";
			default -> "github_indisponible";
		};
	}

}
