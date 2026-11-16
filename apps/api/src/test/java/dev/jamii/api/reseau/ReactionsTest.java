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
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import com.jayway.jsonpath.JsonPath;

import dev.jamii.api.TestcontainersConfiguration;
import dev.jamii.api.auth.Role;
import dev.jamii.api.auth.UtilisateurConnecte;

/** Réagir à un message avec un emoji (#157). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ReactionsTest {

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

	private String envoyer(UtilisateurConnecte de, UtilisateurConnecte a) throws Exception {
		var r = mvc.post().uri("/api/conversations/" + a.id() + "/messages").contentType(MediaType.APPLICATION_JSON)
			.content("{\"texte\":\"On se voit demain ?\"}").with(csrf()).with(oauth2Login().oauth2User(de)).exchange();
		assertThat(r).hasStatus(HttpStatus.CREATED);
		return JsonPath.read(r.getResponse().getContentAsString(StandardCharsets.UTF_8), "$.id");
	}

	private MvcTestResult reagir(UtilisateurConnecte qui, UtilisateurConnecte avec, String message, String emoji, boolean active) {
		return mvc.post().uri("/api/conversations/" + avec.id() + "/messages/" + message + "/reactions")
			.contentType(MediaType.APPLICATION_JSON).content("{\"emoji\":\"" + emoji + "\",\"active\":" + active + "}")
			.with(csrf()).with(oauth2Login().oauth2User(qui)).exchange();
	}

	@Test
	void reconnait_un_emoji_et_seulement_un_emoji() {
		assertThat(Reactions.estUnEmoji("👍")).isTrue();
		assertThat(Reactions.estUnEmoji("❤️")).isTrue();
		assertThat(Reactions.estUnEmoji("👍🏾")).isTrue();
		assertThat(Reactions.estUnEmoji("👩🏾‍💻")).isTrue();
		assertThat(Reactions.estUnEmoji("🇸🇳")).isTrue();
		assertThat(Reactions.estUnEmoji("ok")).isFalse();
		assertThat(Reactions.estUnEmoji("👍 ok")).isFalse();
		assertThat(Reactions.estUnEmoji("<script>")).isFalse();
		assertThat(Reactions.estUnEmoji("")).isFalse();
		assertThat(Reactions.estUnEmoji("👍".repeat(9))).isFalse();
	}

	@Test
	void les_deux_personnes_reagissent_et_voient_les_memes_pastilles() throws Exception {
		var awa = utilisateur("Awa Diop");
		var kofi = utilisateur("Kofi Yao");
		String message = envoyer(kofi, awa);

		assertThat(reagir(awa, kofi, message, "👍", true)).hasStatusOk();
		var r = reagir(kofi, awa, message, "👍", true);
		assertThat(r).bodyJson().extractingPath("$[0].nombre").isEqualTo(2);
		assertThat(r).bodyJson().extractingPath("$[0].deMoi").isEqualTo(true);
		reagir(awa, kofi, message, "🔥", true);
		// Réagir deux fois avec le même emoji ne compte qu'une fois.
		reagir(awa, kofi, message, "👍", true);

		var fil = mvc.get().uri("/api/conversations/" + kofi.id() + "/messages").with(oauth2Login().oauth2User(awa)).exchange();
		assertThat(fil).bodyJson().extractingPath("$.elements[0].reactions[0].emoji").isEqualTo("👍");
		assertThat(fil).bodyJson().extractingPath("$.elements[0].reactions[0].nombre").isEqualTo(2);
		assertThat(fil).bodyJson().extractingPath("$.elements[0].reactions[0].noms").asArray().containsExactly("moi", "Kofi Yao");
		assertThat(fil).bodyJson().extractingPath("$.elements[0].reactions[1].emoji").isEqualTo("🔥");

		// Retirer la sienne.
		assertThat(reagir(awa, kofi, message, "🔥", false)).bodyJson().extractingPath("$.length()").isEqualTo(1);
	}

	@Test
	void refus_texte_tiers_message_supprime_et_trop_de_reactions() throws Exception {
		var awa = utilisateur("Awa");
		var kofi = utilisateur("Kofi");
		var tiers = utilisateur("Tiers");
		String message = envoyer(kofi, awa);

		assertThat(reagir(awa, kofi, message, "bonjour", true)).hasStatus(HttpStatus.BAD_REQUEST);
		// Un tiers ne voit pas ce message.
		assertThat(reagir(tiers, kofi, message, "👍", true)).hasStatus(HttpStatus.NOT_FOUND);
		String[] dix = { "😀", "😃", "😄", "😁", "😆", "😅", "🤣", "😂", "🙂", "🙃" };
		for (String e : dix) {
			assertThat(reagir(awa, kofi, message, e, true)).hasStatusOk();
		}
		assertThat(reagir(awa, kofi, message, "😉", true)).hasStatus(HttpStatus.CONFLICT);

		assertThat(mvc.delete().uri("/api/conversations/" + awa.id() + "/messages/" + message).with(csrf())
			.with(oauth2Login().oauth2User(kofi))).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(reagir(awa, kofi, message, "👍", true)).hasStatus(HttpStatus.CONFLICT);
		assertThat(base.sql("SELECT count(*) FROM reaction_message WHERE message_id = :m").param("m", UUID.fromString(message))
			.query(Long.class).single()).isZero();
	}

}
