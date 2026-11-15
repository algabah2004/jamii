package dev.jamii.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

import dev.jamii.api.TestcontainersConfiguration;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ComptesGithubTest {

	@Autowired
	ComptesGithub comptes;

	@Autowired
	UtilisateurRepository utilisateurs;

	@Test
	void premiere_connexion_cree_un_membre() {
		UtilisateurConnecte connecte = comptes.enregistrer(1001, "awa-dev", "Awa Diallo", "https://avatars.example/1001");

		Utilisateur cree = utilisateurs.findById(connecte.id()).orElseThrow();
		assertThat(cree.nom()).isEqualTo("Awa Diallo");
		assertThat(cree.loginGithub()).isEqualTo("awa-dev");
		assertThat(cree.githubId()).isEqualTo(1001L);
		assertThat(cree.email()).as("jamais l'e-mail public de GitHub").isNull();
		assertThat(connecte.roles()).containsExactly(Role.MEMBRE);
	}

	@Test
	void relier_github_a_un_compte_venu_par_e_mail_garde_le_compte_et_son_nom() {
		Utilisateur parEmail = utilisateurs.saveAndFlush(Utilisateur.depuisEmail("lier." + System.nanoTime() + "@exemple.sn", java.time.Instant.now()));
		long githubId = System.nanoTime();
		UtilisateurConnecte relie = comptes.lier(parEmail.id(), githubId, "awa-code", "https://avatars.example/a");

		assertThat(relie.id()).isEqualTo(parEmail.id());
		Utilisateur apres = utilisateurs.findById(parEmail.id()).orElseThrow();
		assertThat(apres.loginGithub()).isEqualTo("awa-code");
		assertThat(apres.githubId()).isEqualTo(githubId);
		assertThat(apres.nom()).isEqualTo("Nouveau membre");
		// Une connexion GitHub ensuite retrouve ce même compte.
		assertThat(comptes.enregistrer(githubId, "awa-code", "Awa", null).id()).isEqualTo(parEmail.id());
	}

	@Test
	void on_ne_relie_pas_un_github_qui_a_deja_son_compte_ni_un_deuxieme_github() {
		long pris = System.nanoTime();
		comptes.enregistrer(pris, "deja-la", "Déjà là", null);
		Utilisateur parEmail = utilisateurs.saveAndFlush(Utilisateur.depuisEmail("vol." + System.nanoTime() + "@exemple.sn", java.time.Instant.now()));

		assertThatThrownBy(() -> comptes.lier(parEmail.id(), pris, "deja-la", null))
			.isInstanceOf(OAuth2AuthenticationException.class)
			.extracting(e -> ((OAuth2AuthenticationException) e).getError().getErrorCode()).isEqualTo("github_deja_utilise");
		comptes.lier(parEmail.id(), pris + 1, "le-mien", null);
		assertThatThrownBy(() -> comptes.lier(parEmail.id(), pris + 2, "un-autre", null))
			.isInstanceOf(OAuth2AuthenticationException.class)
			.extracting(e -> ((OAuth2AuthenticationException) e).getError().getErrorCode()).isEqualTo("compte_deja_relie");
	}

	@Test
	void reconnait_le_compte_meme_si_le_login_github_a_change() {
		UtilisateurConnecte avant = comptes.enregistrer(1002, "kofi", "Kofi", null);
		UtilisateurConnecte apres = comptes.enregistrer(1002, "kofi-mensah", "Kofi Mensah", null);

		assertThat(apres.id()).isEqualTo(avant.id());
		assertThat(utilisateurs.findById(apres.id()).orElseThrow().loginGithub()).isEqualTo("kofi-mensah");
	}

	@Test
	void nom_vide_remplace_par_le_login_et_nom_trop_long_coupe() {
		UtilisateurConnecte sansNom = comptes.enregistrer(1003, "fatou", "  ", null);
		UtilisateurConnecte longNom = comptes.enregistrer(1004, "moussa", "M".repeat(300), null);

		assertThat(utilisateurs.findById(sansNom.id()).orElseThrow().nom()).isEqualTo("fatou");
		assertThat(utilisateurs.findById(longNom.id()).orElseThrow().nom()).hasSize(Utilisateur.NOM_MAX);
	}

}
