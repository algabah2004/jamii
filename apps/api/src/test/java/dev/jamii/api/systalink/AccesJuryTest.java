package dev.jamii.api.systalink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import dev.jamii.api.TestcontainersConfiguration;
import dev.jamii.api.auth.Role;
import dev.jamii.api.auth.UtilisateurConnecte;

/** Le code d'accès jury de la CADEV (contracts/systalink.yaml v0.4). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "jamii.clients-systalink.code-jury=code-du-jury-2026")
class AccesJuryTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient base;

	private UtilisateurConnecte jure() {
		UUID id = UUID.randomUUID();
		Timestamp t = Timestamp.from(Instant.now());
		base.sql("""
				INSERT INTO utilisateur (id, nom, cree_le, mis_a_jour_le) VALUES (:id, 'Juré', :t, :t)
				""").param("id", id).param("t", t).update();
		return new UtilisateurConnecte(id, Set.of(Role.MEMBRE));
	}

	private MvcTestResult entrer(UtilisateurConnecte moi, String code) {
		return mvc.post()
			.uri("/api/systalink/code-jury")
			.with(oauth2Login().oauth2User(moi))
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"code\":\"" + code + "\"}")
			.exchange();
	}

	@Test
	void le_bon_code_ouvre_l_espace_systalink_et_l_acces_reste() {
		UtilisateurConnecte moi = jure();
		assertThat(mvc.get().uri("/api/systalink/acces").with(oauth2Login().oauth2User(moi))).bodyJson()
			.extractingPath("$.codeJury")
			.isEqualTo(true);
		assertThat(mvc.get().uri("/api/systalink/tableau-de-bord").with(oauth2Login().oauth2User(moi)))
			.hasStatus(HttpStatus.FORBIDDEN);

		assertThat(entrer(moi, "  code-du-jury-2026 ")).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(mvc.get().uri("/api/systalink/acces").with(oauth2Login().oauth2User(moi))).bodyJson()
			.extractingPath("$.acces")
			.isEqualTo(true);
		assertThat(mvc.get().uri("/api/systalink/tableau-de-bord").with(oauth2Login().oauth2User(moi)))
			.hasStatus(HttpStatus.OK);
		assertThat(mvc.get().uri("/api/systalink/clients-suivis").with(oauth2Login().oauth2User(moi)))
			.hasStatus(HttpStatus.OK);
		// Une deuxième fois ne change rien.
		assertThat(entrer(moi, "code-du-jury-2026")).hasStatus(HttpStatus.NO_CONTENT);
	}

	@Test
	void un_mauvais_code_est_refuse_puis_bloque_apres_cinq_essais() {
		UtilisateurConnecte moi = jure();
		for (int i = 0; i < AccesSystalink.ESSAIS_MAX; i++) {
			MvcTestResult reponse = entrer(moi, "code-du-jury-2025");
			assertThat(reponse).hasStatus(HttpStatus.BAD_REQUEST);
			assertThat(reponse).bodyJson().extractingPath("$.erreurs[0].code").isEqualTo("code_faux");
		}
		// Même le bon code ne passe plus : on ne devine pas le code en essayant.
		assertThat(entrer(moi, "code-du-jury-2026")).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(mvc.get().uri("/api/systalink/tableau-de-bord").with(oauth2Login().oauth2User(moi)))
			.hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void il_faut_etre_connecte() {
		assertThat(mvc.post()
			.uri("/api/systalink/code-jury")
			.with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"code\":\"code-du-jury-2026\"}")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

}
