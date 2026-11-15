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

	/** Se connecter avec Google (#260), seulement si l'application Google est configurée. */
	static final String CONNEXION_GOOGLE = "/api/auth/google";

	/** La valeur par défaut d'un identifiant d'application dans application.yaml : pas configuré. */
	static final String NON_CONFIGURE = "a-configurer";

	private static final String ATTRIBUT_RETOUR = "jamii.retour";

	/** L'identifiant du compte connecté qui relie son GitHub (`/api/auth/github?lier=1`, proposition 0005). */
	static final String ATTRIBUT_LIER = "jamii.lier";

	/** Là où revient une personne qui a relié son GitHub, ou qui n'y est pas arrivée. */
	static final String RETOUR_LIER = "/profil";

	@Bean
	SecurityFilterChain securite(HttpSecurity http, ConnexionGithub connexionGithub, ErreursSecurite erreurs,
			ClientRegistrationRepository clients, @Value("${jamii.web-url}") String webUrl) {
		http.authorizeHttpRequests(acces -> acces
				// /error : pour qu'une route publique inconnue réponde 404, et non 401.
				.requestMatchers("/health", "/health/**", "/api/auth/**", "/error").permitAll()
				// Lectures publiques, sans connexion (le jury, un visiteur) : uniquement ces routes et seulement
				// en GET. Elles ne montrent que ce que les projets montrent déjà à tous (contracts/vitrine.yaml,
				// `security: []`). Toute autre route reste fermée. L'espace Systalink est réservé à son équipe (AccesSystalink).
				// /api/profils/* : un profil publié, pour le lien partagé (#159). La recherche (/api/profils) reste réservée.
				// /api/missions et /api/missions/* : les annonces publiques (contracts/missions.yaml). Le module décide, mission
				// par mission, de ce que la personne a le droit de voir (404 sinon) ; le détail du travail reste fermé.
				// /api/hackathons/*/equipes et /resultats : les équipes d'un hackathon publié et ses résultats une fois publiés
				// (contracts/hackathons.yaml), sans les inscriptions ni les notes. /discussion : ses annonces, publiques comme lui.
				.requestMatchers(HttpMethod.GET, "/api/vitrine",
						"/api/profils/*", "/api/missions", "/api/missions/*", "/api/hackathons", "/api/hackathons/*", "/api/hackathons/*/equipes",
						"/api/hackathons/*/resultats", "/api/hackathons/*/discussion")
				.permitAll()
				.anyRequest().authenticated())
			.oauth2Login(oauth -> oauth
				.authorizationEndpoint(debut -> debut.authorizationRequestResolver(resolveurAvecRetour(clients)))
				.redirectionEndpoint(retour -> retour.baseUri("/api/auth/*/retour"))
				.userInfoEndpoint(profil -> profil.userService(connexionGithub))
				.successHandler((requete, reponse, auth) -> {
					boolean lier = retirerLier(requete);
					reponse.sendRedirect(webUrl + (lier ? RETOUR_LIER + "?github=relie" : retirerRetour(requete)));
				})
				.failureHandler((requete, reponse, echec) -> {
					String code = codeEchec(echec, requete.getRequestURI());
					reponse.sendRedirect(webUrl + (retirerLier(requete) ? RETOUR_LIER + "?erreurGithub=" + code : "/connexion?erreur=" + code));
				}))
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
	 * Ne démarre la connexion que sur /api/auth/github, et sur /api/auth/google si Google est configuré (les autres
	 * routes /api/auth/… ne sont pas des fournisseurs), et retient en session le chemin « retour » s'il est sûr.
	 */
	static OAuth2AuthorizationRequestResolver resolveurAvecRetour(ClientRegistrationRepository clients) {
		var parDefaut = new DefaultOAuth2AuthorizationRequestResolver(clients, "/api/auth");
		return new OAuth2AuthorizationRequestResolver() {

			@Override
			public OAuth2AuthorizationRequest resolve(HttpServletRequest requete) {
				String chemin = requete.getRequestURI().substring(requete.getContextPath().length());
				boolean google = CONNEXION_GOOGLE.equals(chemin) && googleConfigure(clients);
				if (!CONNEXION_GITHUB.equals(chemin) && !google) {
					return null;
				}
				requete.getSession().setAttribute(ATTRIBUT_RETOUR, CheminRetour.valider(requete.getParameter("retour")));
				// Relier son GitHub : seulement pour une personne déjà connectée. Sinon, une connexion GitHub ordinaire.
				var connecte = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
				if (!google && "1".equals(requete.getParameter("lier")) && connecte != null
						&& connecte.getPrincipal() instanceof UtilisateurConnecte moi) {
					requete.getSession().setAttribute(ATTRIBUT_LIER, moi.id());
				}
				else {
					requete.getSession().removeAttribute(ATTRIBUT_LIER);
				}
				return parDefaut.resolve(requete);
			}

			@Override
			public OAuth2AuthorizationRequest resolve(HttpServletRequest requete, String fournisseur) {
				return parDefaut.resolve(requete, fournisseur);
			}

		};
	}

	/** Le compte à relier, lu pendant le retour de GitHub (la requête en cours), ou `null`. */
	static java.util.UUID compteALier() {
		if (!(org.springframework.web.context.request.RequestContextHolder.getRequestAttributes()
				instanceof org.springframework.web.context.request.ServletRequestAttributes attributs)) {
			return null;
		}
		HttpSession session = attributs.getRequest().getSession(false);
		return session != null && session.getAttribute(ATTRIBUT_LIER) instanceof java.util.UUID id ? id : null;
	}

	static boolean retirerLier(HttpServletRequest requete) {
		HttpSession session = requete.getSession(false);
		if (session == null || session.getAttribute(ATTRIBUT_LIER) == null) {
			return false;
		}
		session.removeAttribute(ATTRIBUT_LIER);
		return true;
	}

	static String retirerRetour(HttpServletRequest requete) {
		HttpSession session = requete.getSession(false);
		if (session == null || !(session.getAttribute(ATTRIBUT_RETOUR) instanceof String retour)) {
			return CheminRetour.PAR_DEFAUT;
		}
		session.removeAttribute(ATTRIBUT_RETOUR);
		return CheminRetour.valider(retour);
	}

	static boolean googleConfigure(ClientRegistrationRepository clients) {
		var google = clients.findByRegistrationId("google");
		return google != null && FournisseursController.configure(google.getClientId());
	}

	/** Pour un retour de Google, les codes du contrat : refus_google, google_indisponible, email_non_verifie. */
	static String codeEchec(Exception echec, String chemin) {
		if (chemin == null || !chemin.startsWith(CONNEXION_GOOGLE + "/")) {
			return codeEchec(echec);
		}
		String code = echec instanceof OAuth2AuthenticationException oauth ? oauth.getError().getErrorCode() : "";
		return switch (code) {
			case "access_denied" -> "refus_google";
			case "email_non_verifie" -> "email_non_verifie";
			case "invalid_state_parameter", "authorization_request_not_found", "invalid_request" -> "requete_invalide";
			default -> "google_indisponible";
		};
	}

	/** Codes du contrat : refus_github, github_indisponible, requete_invalide. */
	static String codeEchec(Exception echec) {
		if (!(echec instanceof OAuth2AuthenticationException oauth)) {
			return "github_indisponible";
		}
		return switch (oauth.getError().getErrorCode()) {
			case "access_denied" -> "refus_github";
			case "github_deja_utilise", "compte_deja_relie" -> oauth.getError().getErrorCode();
			case "invalid_state_parameter", "authorization_request_not_found", "invalid_request" -> "requete_invalide";
			default -> "github_indisponible";
		};
	}

}
