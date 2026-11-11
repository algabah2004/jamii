package dev.jamii.api.systalink;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import dev.jamii.api.TestcontainersConfiguration;
import dev.jamii.api.auth.Annuaire;

/** Les routes de contracts/systalink.yaml, sur un vrai PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, SystalinkTest.ModulesSimules.class })
class SystalinkTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	MomentsSystalink moments;

	@Autowired
	CalculTableauDeBord calcul;

	@Autowired
	JdbcClient base;

	@Autowired
	Annuaire annuaire;

	/** Ce que les modules profils et deploiement répondront une fois branchés (projets reste absent). */
	@TestConfiguration(proxyBeanMethods = false)
	static class ModulesSimules {

		@Bean
		ChiffresDesModules.PaysDesInscrits pays() {
			return () -> Map.of("SN", 6L, "CI", 3L);
		}

		@Bean
		ChiffresDesModules.Deploiements deploiements() {
			return new ChiffresDesModules.Deploiements() {
				@Override
				public long projetsEnLigne() {
					return 9;
				}

				@Override
				public long projetsSurDatacloud() {
					return 7;
				}
			};
		}

	}

	@BeforeEach
	void viderLesMoments() {
		base.sql("DELETE FROM moment_systalink").update();
		calcul.viderCache();
	}

	@Test
	void le_tableau_agrege_les_modules_et_les_moments() {
		var propose = moments.proposer(ServiceSystalink.DATACLOUD, ContexteMoment.PROJET, UUID.randomUUID());
		moments.proposer(ServiceSystalink.DATACLOUD, ContexteMoment.PROJET, UUID.randomUUID());
		moments.proposer(ServiceSystalink.ORION, ContexteMoment.DEMANDE, UUID.randomUUID());
		accepter(propose.momentId());

		MvcTestResult reponse = mvc.get().uri("/api/systalink/tableau-de-bord?periode=semaine").with(user("awa")).exchange();

		assertThat(reponse).hasStatusOk();
		assertThat(reponse.getResponse().getHeader("Cache-Control")).isEqualTo("max-age=300, public");
		var json = assertThat(reponse).bodyJson();
		json.extractingPath("$.periode").isEqualTo("semaine");
		// Le total vient du vrai module auth (Annuaire), dont la table est partagée avec les autres tests.
		long total = annuaire.compterInscrits(null);
		json.extractingPath("$.inscrits.total").isEqualTo((int) total);
		json.extractingPath("$.inscrits.parPays[0].pays").isEqualTo("SN");
		json.extractingPath("$.inscrits.parPays.length()").as("la Côte d'Ivoire (3) passe dans autres").isEqualTo(1);
		json.extractingPath("$.inscrits.autres").isEqualTo((int) Math.max(0, total - 6));
		json.extractingPath("$.projets.crees").as("module projets pas encore branché : 0, pas un chiffre inventé")
			.isEqualTo(0);
		json.extractingPath("$.projets.surDatacloud").isEqualTo(7);
		json.extractingPath("$.moments[0].service").isEqualTo("datacloud");
		json.extractingPath("$.moments[0].proposes").isEqualTo(2);
		json.extractingPath("$.moments[0].acceptes").isEqualTo(1);
		json.extractingPath("$.moments[1].service").isEqualTo("orion");
	}

	@Test
	void la_proposition_porte_un_lien_https_vers_systalink() {
		var proposition = moments.proposer(ServiceSystalink.YOKK, ContexteMoment.COMMUNAUTE, UUID.randomUUID());

		assertThat(proposition.lien().getScheme()).isEqualTo("https");
		assertThat(proposition.lien().getHost()).endsWith("systalink.com");
	}

	@Test
	void accepter_deux_fois_ne_compte_qu_une_acceptation() {
		var proposition = moments.proposer(ServiceSystalink.DATACLOUD, ContexteMoment.PROJET, UUID.randomUUID());

		assertThat(accepter(proposition.momentId())).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(accepter(proposition.momentId())).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(base.sql("SELECT count(accepte_le) FROM moment_systalink").query(Long.class).single()).isEqualTo(1);
	}

	@Test
	void accepter_un_moment_inconnu_repond_404_au_format_du_contrat() {
		MvcTestResult reponse = accepter(UUID.randomUUID());

		assertThat(reponse).hasStatus(HttpStatus.NOT_FOUND).hasContentType("application/problem+json");
		assertThat(reponse).bodyJson().extractingPath("$.code").isEqualTo("introuvable");
	}

	@Test
	void accepter_exige_le_jeton_csrf() {
		var proposition = moments.proposer(ServiceSystalink.DATACLOUD, ContexteMoment.PROJET, UUID.randomUUID());

		assertThat(mvc.post().uri("/api/systalink/moments/{id}/accepter", proposition.momentId()).with(user("awa")))
			.hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void une_periode_inconnue_repond_400_validation() {
		MvcTestResult reponse = mvc.get().uri("/api/systalink/tableau-de-bord?periode=annee").with(user("awa")).exchange();

		assertThat(reponse).hasStatus(HttpStatus.BAD_REQUEST).hasContentType("application/problem+json");
		assertThat(reponse).bodyJson().extractingPath("$.code").isEqualTo("validation");
		assertThat(reponse).bodyJson().extractingPath("$.erreurs[0].champ").isEqualTo("periode");
	}

	private MvcTestResult accepter(UUID momentId) {
		return mvc.post()
			.uri("/api/systalink/moments/{id}/accepter", momentId)
			.with(user("awa"))
			.with(csrf())
			.exchange();
	}

}
