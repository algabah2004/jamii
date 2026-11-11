package dev.jamii.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import dev.jamii.api.TestcontainersConfiguration;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SecuriteTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	ComptesGithub comptes;

	@Autowired
	ClientRegistrationRepository clients;

	@Test
	void sans_session_api_me_repond_401_en_json() {
		MvcTestResult reponse = mvc.get().uri("/api/me").exchange();
		assertThat(reponse).hasStatus(HttpStatus.UNAUTHORIZED).hasContentType("application/problem+json");
		assertThat(reponse).bodyJson().extractingPath("$.code").isEqualTo("non_connecte");
	}

	@Test
	void toute_route_est_fermee_par_defaut() {
		assertThat(mvc.get().uri("/api/projets")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void route_publique_inconnue_repond_404_et_pas_500() {
		assertThat(mvc.post().uri("/api/auth/lien-email").with(csrf())).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void connexion_github_redirige_vers_github_avec_le_minimum_de_permissions() {
		MvcTestResult reponse = mvc.get().uri("/api/auth/github").exchange();
		assertThat(reponse).hasStatus(HttpStatus.FOUND);
		assertThat(reponse.getResponse().getRedirectedUrl())
			.startsWith("https://github.com/login/oauth/authorize?")
			.contains("scope=read:user&")
			.contains("state=")
			.contains("redirect_uri=http://localhost/api/auth/github/retour");
	}

	@Test
	void connexion_github_retient_seulement_un_chemin_de_retour_sur() {
		var resolveur = SecuriteConfig.resolveurAvecRetour(clients);

		var sur = new MockHttpServletRequest("GET", "/api/auth/github");
		sur.setParameter("retour", "/projets/42");
		assertThat(resolveur.resolve(sur)).isNotNull();
		assertThat(SecuriteConfig.retirerRetour(sur)).isEqualTo("/projets/42");
		assertThat(SecuriteConfig.retirerRetour(sur)).as("utilisé une seule fois").isEqualTo("/");

		var piege = new MockHttpServletRequest("GET", "/api/auth/github");
		piege.setParameter("retour", "//faux-site.com");
		resolveur.resolve(piege);
		assertThat(SecuriteConfig.retirerRetour(piege)).isEqualTo("/");
	}

	@Test
	void seule_api_auth_github_demarre_une_connexion() {
		var autre = new MockHttpServletRequest("POST", "/api/auth/lien-email");
		assertThat(SecuriteConfig.resolveurAvecRetour(clients).resolve(autre)).isNull();
	}

	@Test
	void api_me_renvoie_le_compte_connecte() {
		UtilisateurConnecte awa = comptes.enregistrer(2001, "awa-dev", "Awa", "https://avatars.example/2001");

		MvcTestResult reponse = mvc.get().uri("/api/me").with(oauth2Login().oauth2User(awa)).exchange();

		assertThat(reponse).hasStatusOk().bodyJson().isLenientlyEqualTo("""
				{"id":"%s","nom":"Awa","email":null,"loginGithub":"awa-dev",
				 "methodesConnexion":["github"],"roles":["membre"]}""".formatted(awa.id()));
	}

	@Test
	void api_me_ferme_la_session_d_un_compte_supprime() {
		var fantome = new UtilisateurConnecte(UUID.randomUUID(), Set.of(Role.MEMBRE));
		assertThat(mvc.get().uri("/api/me").with(oauth2Login().oauth2User(fantome))).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void deconnexion_exige_le_jeton_csrf() {
		MvcTestResult sansJeton = mvc.post().uri("/api/auth/deconnexion").exchange();
		assertThat(sansJeton).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(sansJeton).bodyJson().extractingPath("$.code").isEqualTo("interdit");

		assertThat(mvc.post().uri("/api/auth/deconnexion").with(csrf())).hasStatus(HttpStatus.NO_CONTENT);
	}

	@Test
	void en_tetes_de_securite_presents() {
		assertThat(mvc.get().uri("/health")).headers()
			.hasValue("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'")
			.hasValue("X-Content-Type-Options", "nosniff");
	}

	@Test
	void echecs_github_traduits_en_codes_du_contrat() {
		assertThat(SecuriteConfig.codeEchec(new OAuth2AuthenticationException(new OAuth2Error("access_denied"))))
			.isEqualTo("refus_github");
		assertThat(SecuriteConfig.codeEchec(new OAuth2AuthenticationException(new OAuth2Error("invalid_state_parameter"))))
			.isEqualTo("requete_invalide");
		assertThat(SecuriteConfig.codeEchec(new OAuth2AuthenticationException(new OAuth2Error("server_error"))))
			.isEqualTo("github_indisponible");
	}

}
