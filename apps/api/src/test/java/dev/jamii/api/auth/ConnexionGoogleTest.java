package dev.jamii.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import dev.jamii.api.TestcontainersConfiguration;

/** Se connecter avec Google (#260), sur un serveur où l'application Google est configurée. */
@SpringBootTest(properties = { "GOOGLE_CLIENT_ID=client-google-de-test", "GOOGLE_CLIENT_SECRET=secret-de-test" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ConnexionGoogleTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	ConnexionGithub connexion;

	@Autowired
	ComptesEmail comptesEmail;

	@Autowired
	JetonsConnexion jetons;

	@Autowired
	UtilisateurRepository utilisateurs;

	private static DefaultOAuth2User profil(Map<String, Object> attributs) {
		return new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("OAUTH2_USER")), attributs, "sub");
	}

	@Test
	void le_bouton_google_est_propose_et_la_connexion_demande_le_minimum() {
		assertThat(mvc.get().uri("/api/auth/fournisseurs")).hasStatusOk().bodyJson().extractingPath("$.google").isEqualTo(true);
		var reponse = mvc.get().uri("/api/auth/google?retour=/demandes").exchange();
		assertThat(reponse).hasStatus(HttpStatus.FOUND);
		assertThat(reponse.getResponse().getHeader("Location"))
			.startsWith("https://accounts.google.com/o/oauth2/v2/auth?")
			.contains("scope=profile%20email")
			.contains("redirect_uri=http://localhost/api/auth/google/retour");
	}

	@Test
	void premiere_connexion_cree_le_compte_avec_le_nom_et_la_photo_de_google() {
		String adresse = "awa." + System.nanoTime() + "@exemple.sn";
		UtilisateurConnecte moi = connexion.depuisGoogle(profil(Map.of("sub", "1", "email", adresse, "email_verified", true,
				"name", "Awa Diallo", "picture", "https://lh3.googleusercontent.com/a/photo")));
		Utilisateur cree = utilisateurs.findById(moi.id()).orElseThrow();
		assertThat(cree.nom()).isEqualTo("Awa Diallo");
		assertThat(cree.email()).isEqualTo(adresse);
		assertThat(cree.avatarUrl()).isEqualTo("https://lh3.googleusercontent.com/a/photo");
		assertThat(cree.githubId()).isNull();
		assertThat(moi.roles()).containsExactly(Role.MEMBRE);

		// Une deuxième connexion retrouve le même compte, même avec l'adresse écrite autrement.
		UtilisateurConnecte encore = connexion.depuisGoogle(profil(Map.of("sub", "1", "email", adresse.toUpperCase(),
				"email_verified", "true", "name", "Autre nom")));
		assertThat(encore.id()).isEqualTo(moi.id());
		assertThat(utilisateurs.findById(moi.id()).orElseThrow().nom()).as("le profil Jamii décide ensuite").isEqualTo("Awa Diallo");
	}

	@Test
	void une_personne_venue_par_lien_e_mail_retrouve_son_compte() {
		String adresse = "kofi." + System.nanoTime() + "@exemple.gh";
		String jeton = jetons.creer(adresse, Instant.now());
		UtilisateurConnecte parLien = comptesEmail.confirmer(jeton, Instant.now()).orElseThrow();

		UtilisateurConnecte parGoogle = connexion.depuisGoogle(profil(Map.of("sub", "2", "email", adresse, "email_verified", true)));
		assertThat(parGoogle.id()).isEqualTo(parLien.id());
	}

	@Test
	void une_adresse_non_verifiee_par_google_est_refusee() {
		assertThatThrownBy(() -> connexion.depuisGoogle(profil(Map.of("sub", "3", "email", "x@exemple.sn", "email_verified", false))))
			.isInstanceOf(OAuth2AuthenticationException.class)
			.extracting(e -> ((OAuth2AuthenticationException) e).getError().getErrorCode()).isEqualTo("email_non_verifie");
		assertThatThrownBy(() -> connexion.depuisGoogle(profil(Map.of("sub", "4"))))
			.isInstanceOf(OAuth2AuthenticationException.class);
	}

	@Test
	void echecs_google_traduits_en_codes_du_contrat() {
		String retour = "/api/auth/google/retour";
		assertThat(SecuriteConfig.codeEchec(new OAuth2AuthenticationException(new OAuth2Error("access_denied")), retour))
			.isEqualTo("refus_google");
		assertThat(SecuriteConfig.codeEchec(new OAuth2AuthenticationException(new OAuth2Error("email_non_verifie")), retour))
			.isEqualTo("email_non_verifie");
		assertThat(SecuriteConfig.codeEchec(new OAuth2AuthenticationException(new OAuth2Error("server_error")), retour))
			.isEqualTo("google_indisponible");
		// Un retour de GitHub garde ses propres codes.
		assertThat(SecuriteConfig.codeEchec(new OAuth2AuthenticationException(new OAuth2Error("access_denied")), "/api/auth/github/retour"))
			.isEqualTo("refus_github");
	}

}
