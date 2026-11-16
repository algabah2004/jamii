package dev.jamii.api.reseau;

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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jayway.jsonpath.JsonPath;

import dev.jamii.api.TestcontainersConfiguration;
import dev.jamii.api.auth.Role;
import dev.jamii.api.auth.UtilisateurConnecte;

/** Répondre à un message, le modifier, le supprimer pour tout le monde, le transférer (#157). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ActionsMessagesTest {

	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D };

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient base;

	private UtilisateurConnecte utilisateur(String nom) {
		UUID id = UUID.randomUUID();
		Timestamp t = Timestamp.from(Instant.now());
		base.sql("INSERT INTO utilisateur (id, nom, login_github, avatar_url, cree_le, mis_a_jour_le) VALUES (:id, :nom, :login, NULL, :t, :t)")
			.param("id", id).param("nom", nom).param("login", "gh-" + id.toString().substring(0, 8)).param("t", t).update();
		return new UtilisateurConnecte(id, Set.of(Role.MEMBRE));
	}

	private MvcTestResult appel(String methode, String uri, UtilisateurConnecte qui, String corps) {
		var requete = switch (methode) {
			case "PATCH" -> mvc.patch().uri(uri);
			case "DELETE" -> mvc.delete().uri(uri);
			default -> mvc.post().uri(uri);
		};
		if (corps != null) requete = requete.contentType(MediaType.APPLICATION_JSON).content(corps);
		return requete.with(csrf()).with(oauth2Login().oauth2User(qui)).exchange();
	}

	private String envoyer(UtilisateurConnecte de, UtilisateurConnecte a, String corps) throws Exception {
		var r = appel("POST", "/api/conversations/" + a.id() + "/messages", de, corps);
		assertThat(r).hasStatus(HttpStatus.CREATED);
		return JsonPath.read(r.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
	}

	private MvcTestResult fil(UtilisateurConnecte qui, UtilisateurConnecte avec) {
		return mvc.get().uri("/api/conversations/" + avec.id() + "/messages").with(oauth2Login().oauth2User(qui)).exchange();
	}

	@Test
	void repondre_cite_le_message_d_origine_de_la_meme_conversation() throws Exception {
		var awa = utilisateur("Awa");
		var kofi = utilisateur("Kofi");
		var tiers = utilisateur("Tiers");
		String question = envoyer(kofi, awa, "{\"texte\":\"Tu passes demain ?\"}");
		String reponse = envoyer(awa, kofi, "{\"texte\":\"Oui, vers 10 h\",\"reponseA\":\"" + question + "\"}");

		var lu = fil(kofi, awa);
		assertThat(lu).bodyJson().extractingPath("$.elements[0].id").isEqualTo(reponse);
		assertThat(lu).bodyJson().extractingPath("$.elements[0].reponseA.id").isEqualTo(question);
		assertThat(lu).bodyJson().extractingPath("$.elements[0].reponseA.texte").isEqualTo("Tu passes demain ?");
		assertThat(lu).bodyJson().extractingPath("$.elements[0].reponseA.deMoi").isEqualTo(true);

		// On ne cite pas un message d'une autre conversation.
		String ailleurs = envoyer(tiers, awa, "{\"texte\":\"Bonjour\"}");
		assertThat(appel("POST", "/api/conversations/" + kofi.id() + "/messages", awa, "{\"texte\":\"x\",\"reponseA\":\"" + ailleurs + "\"}"))
			.hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void on_ne_modifie_que_son_message_et_pendant_15_minutes() throws Exception {
		var awa = utilisateur("Awa");
		var kofi = utilisateur("Kofi");
		String id = envoyer(awa, kofi, "{\"texte\":\"Rendez-vous à 10 h\"}");
		String uri = "/api/conversations/" + kofi.id() + "/messages/" + id;

		var modifie = appel("PATCH", uri, awa, "{\"texte\":\"Rendez-vous à 11 h\"}");
		assertThat(modifie).hasStatusOk().bodyJson().extractingPath("$.texte").isEqualTo("Rendez-vous à 11 h");
		assertThat(modifie).bodyJson().extractingPath("$.modifieLe").isNotNull();
		assertThat(fil(kofi, awa)).bodyJson().extractingPath("$.elements[0].texte").isEqualTo("Rendez-vous à 11 h");

		// Kofi ne modifie pas le message d'Awa ; un texte vide est refusé.
		assertThat(appel("PATCH", "/api/conversations/" + awa.id() + "/messages/" + id, kofi, "{\"texte\":\"pirate\"}")).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(appel("PATCH", uri, awa, "{\"texte\":\"  \"}")).hasStatus(HttpStatus.BAD_REQUEST);

		base.sql("UPDATE message SET envoye_le = envoye_le - make_interval(mins => 16) WHERE id = :id").param("id", UUID.fromString(id)).update();
		assertThat(appel("PATCH", uri, awa, "{\"texte\":\"Trop tard\"}")).hasStatus(HttpStatus.CONFLICT)
			.bodyJson().extractingPath("$.code").isEqualTo("delai_depasse");
	}

	@Test
	void supprimer_pour_tout_le_monde_efface_le_texte_et_les_fichiers() throws Exception {
		var awa = utilisateur("Awa");
		var kofi = utilisateur("Kofi");
		var envoi = mvc.post().uri("/api/fichiers").multipart().file(new MockMultipartFile("fichier", "photo.png", "image/png", PNG))
			.param("destinataireId", kofi.id().toString()).with(csrf()).with(oauth2Login().oauth2User(awa)).exchange();
		String fichier = JsonPath.read(envoi.getResponse().getContentAsString(), "$.id");
		String id = envoyer(awa, kofi, "{\"texte\":\"Mon numéro : 77 000 00 00\",\"pieces\":[\"" + fichier + "\"]}");
		String reponse = envoyer(kofi, awa, "{\"texte\":\"Noté\",\"reponseA\":\"" + id + "\"}");
		String uri = "/api/conversations/" + kofi.id() + "/messages/" + id;

		assertThat(appel("DELETE", "/api/conversations/" + awa.id() + "/messages/" + id, kofi, null)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(appel("DELETE", uri, awa, null)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(appel("DELETE", uri, awa, null)).hasStatus(HttpStatus.NO_CONTENT);

		var lu = fil(kofi, awa);
		assertThat(lu).bodyJson().extractingPath("$.elements[1].supprime").isEqualTo(true);
		assertThat(lu).bodyJson().extractingPath("$.elements[1].texte").isEqualTo("");
		assertThat(lu).bodyJson().extractingPath("$.elements[1].piecesJointes.length()").isEqualTo(0);
		// La réponse garde sa place, mais ne montre plus le texte supprimé.
		assertThat(lu).bodyJson().extractingPath("$.elements[0].id").isEqualTo(reponse);
		assertThat(lu).bodyJson().extractingPath("$.elements[0].reponseA.supprime").isEqualTo(true);
		assertThat(lu).bodyJson().extractingPath("$.elements[0].reponseA.texte").isEqualTo("");
		assertThat(base.sql("SELECT count(*) FROM fichier WHERE id = :id").param("id", UUID.fromString(fichier)).query(Integer.class).single()).isZero();
		assertThat(base.sql("SELECT texte FROM message WHERE id = :id").param("id", UUID.fromString(id)).query(String.class).single()).isEmpty();
		assertThat(appel("PATCH", uri, awa, "{\"texte\":\"retour\"}")).hasStatus(HttpStatus.CONFLICT);
	}

	@Test
	void transferer_copie_le_message_et_ses_fichiers_vers_cinq_conversations_au_plus() throws Exception {
		var awa = utilisateur("Awa");
		var kofi = utilisateur("Kofi");
		var fatou = utilisateur("Fatou");
		var moussa = utilisateur("Moussa");
		// Kofi m'envoie une image ; je la transfère à Fatou et Moussa.
		var envoi = mvc.post().uri("/api/fichiers").multipart().file(new MockMultipartFile("fichier", "plan.png", "image/png", PNG))
			.param("destinataireId", awa.id().toString()).with(csrf()).with(oauth2Login().oauth2User(kofi)).exchange();
		String fichier = JsonPath.read(envoi.getResponse().getContentAsString(), "$.id");
		String id = envoyer(kofi, awa, "{\"texte\":\"Le plan du stand\",\"pieces\":[\"" + fichier + "\"]}");
		String uri = "/api/conversations/" + kofi.id() + "/messages/" + id + "/transfert";

		assertThat(appel("POST", uri, awa, "{\"destinataires\":[\"" + fatou.id() + "\",\"" + moussa.id() + "\"]}")).hasStatus(HttpStatus.NO_CONTENT);
		var chezFatou = fil(fatou, awa);
		assertThat(chezFatou).bodyJson().extractingPath("$.elements[0].texte").isEqualTo("Le plan du stand");
		assertThat(chezFatou).bodyJson().extractingPath("$.elements[0].transfere").isEqualTo(true);
		String copie = JsonPath.read(chezFatou.getResponse().getContentAsString(), "$.elements[0].piecesJointes[0].id");
		assertThat(copie).isNotEqualTo(fichier);
		// Fatou lit sa copie ; elle ne lit pas l'original de Kofi.
		assertThat(mvc.get().uri("/api/fichiers/" + copie).with(oauth2Login().oauth2User(fatou)).exchange()).hasStatusOk();
		assertThat(mvc.get().uri("/api/fichiers/" + fichier).with(oauth2Login().oauth2User(fatou)).exchange()).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(fil(moussa, awa)).bodyJson().extractingPath("$.elements[0].transfere").isEqualTo(true);

		// Un inconnu ne transfère pas une conversation qui n'est pas la sienne ; six destinataires, c'est trop.
		assertThat(appel("POST", "/api/conversations/" + kofi.id() + "/messages/" + id + "/transfert", fatou,
				"{\"destinataires\":[\"" + moussa.id() + "\"]}")).hasStatus(HttpStatus.NOT_FOUND);
		StringBuilder six = new StringBuilder();
		for (int i = 0; i < 6; i++) six.append(i == 0 ? "" : ",").append('"').append(utilisateur("P" + i).id()).append('"');
		assertThat(appel("POST", uri, awa, "{\"destinataires\":[" + six + "]}")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(appel("POST", uri, awa, "{\"destinataires\":[]}")).hasStatus(HttpStatus.BAD_REQUEST);
	}

}
