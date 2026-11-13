package dev.jamii.api.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import dev.jamii.api.TestcontainersConfiguration;
import dev.jamii.api.auth.Role;
import dev.jamii.api.auth.UtilisateurConnecte;

/** Le profil `demo` charge les données de la démo au démarrage, lisibles par les vraies routes de l'API. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@Import(TestcontainersConfiguration.class)
class DonneesDemoTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient base;

	@Autowired
	DonneesDemo demo;

	private static UtilisateurConnecte connecte(DonneesDemo.Personne p) {
		return new UtilisateurConnecte(p.id(), Set.of(Role.MEMBRE));
	}

	private long compter(String sql) {
		return base.sql(sql).query(Long.class).single();
	}

	private UUID projetDuTour(UUID cercle, int rang) {
		return base.sql("SELECT projet_id FROM cercle_membre WHERE cercle_id = :c AND rang = :r").param("c", cercle).param("r", rang)
			.query(UUID.class).single();
	}

	@Test
	void le_cercle_de_dakar_a_un_tour_livre_et_un_tour_en_cours() {
		var reponse = mvc.get().uri("/api/cercles/" + DonneesDemo.DAKAR).with(oauth2Login().oauth2User(connecte(DonneesDemo.AWA)));

		assertThat(reponse).hasStatusOk();
		assertThat(reponse).bodyJson().extractingPath("$.statut").isEqualTo("en_cours");
		assertThat(reponse).bodyJson().extractingPath("$.tours.length()").isEqualTo(6);
		assertThat(reponse).bodyJson().extractingPath("$.tours[0].livreLe").isNotNull();
		assertThat(reponse).bodyJson().extractingPath("$.tours[1].projetId").isNotNull();
		assertThat(reponse).bodyJson().extractingPath("$.tours[1].livreLe").isNull();
		assertThat(reponse).bodyJson().extractingPath("$.tours[2].projetId").isNull();

		var livre = mvc.get().uri("/api/projets/" + projetDuTour(DonneesDemo.DAKAR, 1))
			.with(oauth2Login().oauth2User(connecte(DonneesDemo.KOFI)));
		assertThat(livre).bodyJson().extractingPath("$.statut").isEqualTo("livre");
		assertThat(livre).bodyJson().extractingPath("$.origine").isEqualTo("cercle");
		assertThat(livre).bodyJson().extractingPath("$.porteur.nom").isEqualTo("Awa Diop");
		assertThat(mvc.get().uri("/api/projets/" + projetDuTour(DonneesDemo.DAKAR, 2))
			.with(oauth2Login().oauth2User(connecte(DonneesDemo.KOFI)))).bodyJson().extractingPath("$.statut").isEqualTo("en_cours");
	}

	@Test
	void les_deux_autres_cercles_un_ouvert_que_l_on_peut_rejoindre_et_un_termine() {
		assertThat(mvc.get().uri("/api/cercles/" + DonneesDemo.ABIDJAN).with(oauth2Login().oauth2User(connecte(DonneesDemo.AWA))))
			.bodyJson().extractingPath("$.statut").isEqualTo("ouvert");
		assertThat(compter("SELECT count(*) FROM cercle_membre WHERE cercle_id = '" + DonneesDemo.ABIDJAN + "'")).isEqualTo(3);
		assertThat(compter("SELECT count(*) FROM cercle WHERE id = '" + DonneesDemo.NAIROBI + "' AND statut = 'termine'")).isEqualTo(1);
		assertThat(compter("""
				SELECT count(*) FROM projet p JOIN cercle_membre m ON m.projet_id = p.id
				WHERE m.cercle_id = '%s' AND p.statut = 'livre'
				""".formatted(DonneesDemo.NAIROBI))).isEqualTo(4);
	}

	@Test
	void recharger_ne_cree_aucun_doublon() {
		long utilisateurs = compter("SELECT count(*) FROM utilisateur");
		long projets = compter("SELECT count(*) FROM projet");

		assertThat(demo.charger(Instant.now())).isFalse();

		assertThat(compter("SELECT count(*) FROM utilisateur")).isEqualTo(utilisateurs);
		assertThat(compter("SELECT count(*) FROM projet")).isEqualTo(projets);
	}

	@Test
	void aucun_compte_de_demo_n_a_de_login_github_ni_d_adresse_joignable() {
		assertThat(compter("SELECT count(*) FROM utilisateur WHERE email LIKE '%@demo.jamii.test'")).isEqualTo(6);
		assertThat(compter("SELECT count(*) FROM utilisateur WHERE email LIKE '%@demo.jamii.test' AND login_github IS NOT NULL")).isZero();
	}

}
