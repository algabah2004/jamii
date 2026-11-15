package dev.jamii.api.fichiers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;

import java.nio.charset.StandardCharsets;
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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jayway.jsonpath.JsonPath;

import dev.jamii.api.TestcontainersConfiguration;
import dev.jamii.api.auth.Role;
import dev.jamii.api.auth.UtilisateurConnecte;

/** Les fichiers joints (contracts/fichiers.yaml), sur un vrai PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FichiersTest {

	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D };

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient base;

	@Autowired
	Fichiers fichiers;

	private UtilisateurConnecte utilisateur(String nom) {
		UUID id = UUID.randomUUID();
		Timestamp t = Timestamp.from(Instant.now());
		base.sql("INSERT INTO utilisateur (id, nom, login_github, avatar_url, cree_le, mis_a_jour_le) VALUES (:id, :nom, :login, NULL, :t, :t)")
			.param("id", id).param("nom", nom).param("login", "gh-" + id.toString().substring(0, 8)).param("t", t).update();
		return new UtilisateurConnecte(id, Set.of(Role.MEMBRE));
	}

	private MvcTestResult envoyer(UtilisateurConnecte qui, String nom, String type, byte[] octets) {
		return mvc.post().uri("/api/fichiers").multipart().file(new MockMultipartFile("fichier", nom, type, octets))
			.with(csrf()).with(oauth2Login().oauth2User(qui)).exchange();
	}

	@Test
	void une_image_est_reconnue_a_ses_octets_et_relue_telle_quelle() throws Exception {
		var awa = utilisateur("Awa");
		// Le nom et le type annoncés mentent : c'est le contenu qui décide.
		var reponse = envoyer(awa, "../../maquette.txt", "text/html", PNG);
		assertThat(reponse).hasStatus(HttpStatus.CREATED);
		assertThat(reponse).bodyJson().extractingPath("$.sorte").isEqualTo("image");
		assertThat(reponse).bodyJson().extractingPath("$.type").isEqualTo("image/png");
		assertThat(reponse).bodyJson().extractingPath("$.nom").isEqualTo("maquette.txt");
		String url = JsonPath.read(reponse.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.url");

		var lu = mvc.get().uri(url).with(oauth2Login().oauth2User(utilisateur("Kofi"))).exchange();
		assertThat(lu).hasStatusOk();
		assertThat(lu.getResponse().getContentType()).isEqualTo("image/png");
		assertThat(lu.getResponse().getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
		assertThat(lu.getResponse().getContentAsByteArray()).isEqualTo(PNG);
		assertThat(mvc.get().uri(url).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void ce_qui_pourrait_s_executer_est_refuse() {
		var awa = utilisateur("Awa");
		assertThat(envoyer(awa, "page.html", "text/html", "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8)))
			.hasStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE).bodyJson().extractingPath("$.code").isEqualTo("type_refuse");
		assertThat(envoyer(awa, "logo.svg", "image/svg+xml", "<svg onload=alert(1)>".getBytes(StandardCharsets.UTF_8)))
			.hasStatus(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
		assertThat(envoyer(awa, "notes.md", "text/markdown", "# Le plan\n- écran de connexion".getBytes(StandardCharsets.UTF_8)))
			.hasStatus(HttpStatus.CREATED).bodyJson().extractingPath("$.sorte").isEqualTo("document");
	}

	@Test
	void on_ne_joint_que_ses_propres_fichiers_et_six_au_plus() throws Exception {
		var awa = utilisateur("Awa");
		var kofi = utilisateur("Kofi");
		String id = JsonPath.read(envoyer(kofi, "a.png", "image/png", PNG).getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
		var erreur = org.assertj.core.api.Assertions.catchThrowableOfType(ErreurFichier.class,
				() -> fichiers.verifierPieces(java.util.List.of(UUID.fromString(id)), awa.id()));
		assertThat(erreur.code()).isEqualTo("piece_inconnue");
		assertThat(fichiers.verifierPieces(java.util.List.of(UUID.fromString(id), UUID.fromString(id)), kofi.id())).hasSize(1);
		var sept = java.util.stream.IntStream.range(0, 7).mapToObj(i -> UUID.randomUUID()).toList();
		assertThat(org.assertj.core.api.Assertions.catchThrowableOfType(ErreurFichier.class, () -> fichiers.verifierPieces(sept, kofi.id())).code())
			.isEqualTo("trop_de_pieces");
	}

	private MvcTestResult envoyerPrive(UtilisateurConnecte qui, UUID a) {
		return mvc.post().uri("/api/fichiers").multipart().file(new MockMultipartFile("fichier", "maquette.png", "image/png", PNG))
			.param("destinataireId", a.toString()).with(csrf()).with(oauth2Login().oauth2User(qui)).exchange();
	}

	private MvcTestResult message(UtilisateurConnecte qui, UUID a, String pieces) {
		return mvc.post().uri("/api/conversations/" + a + "/messages")
			.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
			.content("{\"texte\":\"\",\"pieces\":[" + pieces + "]}")
			.with(csrf()).with(oauth2Login().oauth2User(qui)).exchange();
	}

	@Test
	void un_fichier_prive_ne_se_lit_que_par_son_auteur_puis_son_destinataire_apres_envoi() throws Exception {
		var awa = utilisateur("Awa");
		var kofi = utilisateur("Kofi");
		var tiers = utilisateur("Tiers");
		var envoi = envoyerPrive(awa, kofi.id());
		assertThat(envoi).hasStatus(HttpStatus.CREATED);
		String id = JsonPath.read(envoi.getResponse().getContentAsString(), "$.id");
		String url = "/api/fichiers/" + id;
		var apercu = mvc.get().uri(url).with(oauth2Login().oauth2User(awa)).exchange();
		assertThat(apercu).hasStatusOk();
		assertThat(apercu.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
		assertThat(mvc.get().uri(url).with(oauth2Login().oauth2User(kofi)).exchange()).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(message(awa, kofi.id(), "\"" + id + "\"")).hasStatus(HttpStatus.CREATED)
			.bodyJson().extractingPath("$.piecesJointes[0].nom").isEqualTo("maquette.png");
		assertThat(mvc.get().uri(url).with(oauth2Login().oauth2User(kofi)).exchange()).hasStatusOk();
		assertThat(mvc.get().uri(url).with(oauth2Login().oauth2User(tiers)).exchange()).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(mvc.get().uri("/api/conversations/" + awa.id() + "/messages").with(oauth2Login().oauth2User(kofi)).exchange())
			.hasStatusOk().bodyJson().extractingPath("$.elements[0].piecesJointes[0].id").isEqualTo(id);
		assertThat(message(awa, tiers.id(), "\"" + id + "\"")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(message(tiers, kofi.id(), "\"" + id + "\"")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(org.assertj.core.api.Assertions.catchThrowableOfType(ErreurFichier.class,
			() -> fichiers.verifierPieces(java.util.List.of(UUID.fromString(id)), awa.id())).code()).isEqualTo("piece_inconnue");
	}

	@Test
	void les_fichiers_prives_sont_exportes_et_effaces_avec_les_comptes() throws Exception {
		for (boolean auteur : java.util.List.of(true, false)) {
			var awa = utilisateur("Awa");
			var kofi = utilisateur("Kofi");
			String id = JsonPath.read(envoyerPrive(awa, kofi.id()).getResponse().getContentAsString(), "$.id");
			assertThat(message(awa, kofi.id(), "\"" + id + "\"")).hasStatus(HttpStatus.CREATED);
			assertThat(mvc.get().uri("/api/me/export").with(oauth2Login().oauth2User(awa)).exchange()).hasStatusOk()
				.bodyJson().extractingPath("$.fichiers[0].id").isEqualTo(id);
			assertThat(mvc.delete().uri("/api/me").with(csrf()).with(oauth2Login().oauth2User(auteur ? awa : kofi)).exchange())
				.hasStatus(HttpStatus.ACCEPTED);
			assertThat(base.sql("SELECT count(*) FROM fichier_contenu WHERE fichier_id = :id").param("id", UUID.fromString(id)).query(Integer.class).single()).isZero();
			assertThat(base.sql("SELECT count(*) FROM fichier WHERE id = :id").param("id", UUID.fromString(id)).query(Integer.class).single()).isZero();
		}
	}

	@Test
	void une_piece_inconnue_ou_un_message_vide_ne_sont_pas_acceptes() {
		var awa = utilisateur("Awa");
		var kofi = utilisateur("Kofi");
		assertThat(message(awa, kofi.id(), "")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(message(awa, kofi.id(), "\"" + UUID.randomUUID() + "\"")).hasStatus(HttpStatus.BAD_REQUEST);
	}

}
