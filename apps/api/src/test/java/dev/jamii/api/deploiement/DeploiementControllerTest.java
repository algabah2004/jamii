package dev.jamii.api.deploiement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;

import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import dev.jamii.api.TestcontainersConfiguration;
import dev.jamii.api.auth.Role;
import dev.jamii.api.auth.UtilisateurConnecte;

/** Les routes de contracts/deploiement.yaml : codes HTTP, format des erreurs, pagination. Sur un vrai PostgreSQL. */
@SpringBootTest(properties = { "DATACLOUD_DOMAINES=datacloud.test", "jamii.deploiement.premiere-verification=PT1H" })
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, MiseEnLigneTest.Simulations.class })
class DeploiementControllerTest {

	static final UtilisateurConnecte AWA = new UtilisateurConnecte(MiseEnLigneTest.AWA, Set.of(Role.MEMBRE));

	static final UtilisateurConnecte KOFI = new UtilisateurConnecte(MiseEnLigneTest.KOFI, Set.of(Role.MEMBRE));

	@Autowired
	MockMvcTester mvc;

	@Autowired
	MiseEnLigne miseEnLigne;

	@Autowired
	JdbcClient base;

	UUID projet;

	@BeforeEach
	void unProjetNeuf() {
		base.sql("DELETE FROM deploiement").update();
		miseEnLigne.oublierLimites();
		projet = UUID.randomUUID();
		MiseEnLigneTest.PROJETS.add(projet);
		MiseEnLigneTest.REPONSE.set(Verificateur.Resultat.REPOND);
	}

	private String chemin() {
		return "/api/projets/" + projet + "/deploiements";
	}

	private MvcTestResult declarer(UtilisateurConnecte qui, String url) {
		return mvc.post().uri(chemin()).with(oauth2Login().oauth2User(qui)).with(csrf())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"url\":\"" + url + "\"}")
			.exchange();
	}

	@Test
	void declarer_repond_202_avec_location_et_statut_en_cours() throws Exception {
		MvcTestResult reponse = declarer(AWA, "https://boutique.datacloud.test");

		assertThat(reponse).hasStatus(HttpStatus.ACCEPTED);
		var json = assertThat(reponse).bodyJson();
		json.extractingPath("$.statut").isEqualTo("en_cours");
		json.extractingPath("$.hebergeur").isEqualTo("datacloud");
		json.extractingPath("$.projetId").isEqualTo(projet.toString());
		assertThat(reponse.getResponse().getHeader("Location")).startsWith(chemin() + "/");
		assertThat(reponse.getResponse().getContentAsString()).as("champ inconnu absent, pas null")
			.doesNotContain("verifieLe");
	}

	@Test
	void une_adresse_refusee_repond_400_au_format_du_contrat() {
		MvcTestResult reponse = declarer(AWA, "https://interne.example.test");

		assertThat(reponse).hasStatus(HttpStatus.BAD_REQUEST).hasContentType("application/problem+json");
		var json = assertThat(reponse).bodyJson();
		json.extractingPath("$.code").isEqualTo("validation");
		json.extractingPath("$.erreurs[0].champ").isEqualTo("url");
		json.extractingPath("$.erreurs[0].code").isEqualTo("adresse_refusee");
	}

	@Test
	void hors_de_l_equipe_403_et_projet_inconnu_404() {
		assertThat(declarer(KOFI, "https://site.example.test")).hasStatus(HttpStatus.FORBIDDEN)
			.bodyJson()
			.extractingPath("$.code")
			.isEqualTo("interdit");

		MvcTestResult inconnu = mvc.get().uri("/api/projets/" + UUID.randomUUID() + "/deploiements")
			.with(oauth2Login().oauth2User(AWA))
			.exchange();
		assertThat(inconnu).hasStatus(HttpStatus.NOT_FOUND).bodyJson().extractingPath("$.code").isEqualTo("introuvable");
	}

	@Test
	void il_faut_etre_connecte_et_envoyer_le_jeton_csrf() {
		assertThat(mvc.get().uri(chemin())).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.post().uri(chemin()).with(oauth2Login().oauth2User(AWA))
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"url\":\"https://site.example.test\"}")).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void la_liste_est_paginee_par_curseur() throws Exception {
		for (int i = 0; i < 3; i++) {
			assertThat(declarer(AWA, "https://site" + i + ".example.test")).hasStatus(HttpStatus.ACCEPTED);
		}

		MvcTestResult premiere = mvc.get().uri(chemin() + "?limite=2").with(oauth2Login().oauth2User(KOFI)).exchange();
		assertThat(premiere).hasStatusOk().bodyJson().extractingPath("$.elements.length()").isEqualTo(2);
		String curseur = com.jayway.jsonpath.JsonPath.read(premiere.getResponse().getContentAsString(),
				"$.curseurSuivant");

		MvcTestResult seconde = mvc.get().uri(chemin() + "?limite=2&curseur=" + curseur)
			.with(oauth2Login().oauth2User(KOFI))
			.exchange();
		var json = assertThat(seconde).hasStatusOk().bodyJson();
		json.extractingPath("$.elements.length()").isEqualTo(1);
		json.extractingPath("$.curseurSuivant").isNull();

		assertThat(mvc.get().uri(chemin() + "?limite=0").with(oauth2Login().oauth2User(AWA)))
			.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(mvc.get().uri(chemin() + "?curseur=n-importe-quoi").with(oauth2Login().oauth2User(AWA)))
			.hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void relancer_trop_vite_repond_429_avec_retry_after() throws Exception {
		MvcTestResult cree = declarer(AWA, "https://relance.example.test");
		String id = com.jayway.jsonpath.JsonPath.read(cree.getResponse().getContentAsString(), "$.id");
		org.awaitility.Awaitility.await()
			.until(() -> base.sql("SELECT dernier_essai_le IS NOT NULL FROM deploiement WHERE id = CAST(:id AS uuid)")
				.param("id", id)
				.query(Boolean.class)
				.single());

		MvcTestResult reponse = mvc.post().uri(chemin() + "/" + id + "/verifier")
			.with(oauth2Login().oauth2User(AWA))
			.with(csrf())
			.exchange();

		assertThat(reponse).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(reponse.getResponse().getHeader("Retry-After")).isEqualTo("60");
	}

	@Test
	void retirer_puis_proposer_datacloud() throws Exception {
		String id = com.jayway.jsonpath.JsonPath.read(
				declarer(AWA, "https://retiree.example.test").getResponse().getContentAsString(), "$.id");

		assertThat(mvc.delete().uri(chemin() + "/" + id).with(oauth2Login().oauth2User(AWA)).with(csrf()))
			.hasStatus(HttpStatus.NO_CONTENT);

		MvcTestResult proposition = mvc.post().uri(chemin() + "/proposer-datacloud")
			.with(oauth2Login().oauth2User(AWA))
			.with(csrf())
			.exchange();
		var json = assertThat(proposition).hasStatusOk().bodyJson();
		json.extractingPath("$.service").isEqualTo("datacloud");
		json.extractingPath("$.lien").asString().startsWith("https://");
	}

}
