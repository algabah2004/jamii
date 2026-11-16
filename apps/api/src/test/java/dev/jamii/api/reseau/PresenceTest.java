package dev.jamii.api.reseau;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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

import dev.jamii.api.TestcontainersConfiguration;
import dev.jamii.api.auth.Role;
import dev.jamii.api.auth.UtilisateurConnecte;

/** « En ligne » et « Vu il y a … », et le choix de cacher sa présence (#157). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class PresenceTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient base;

	@Autowired
	Presence presence;

	private UtilisateurConnecte utilisateur(String nom) {
		UUID id = UUID.randomUUID();
		Timestamp t = Timestamp.from(Instant.now());
		base.sql("INSERT INTO utilisateur (id, nom, login_github, avatar_url, cree_le, mis_a_jour_le) VALUES (:id, :nom, :login, NULL, :t, :t)")
			.param("id", id).param("nom", nom).param("login", "gh-" + id.toString().substring(0, 8)).param("t", t).update();
		return new UtilisateurConnecte(id, Set.of(Role.MEMBRE));
	}

	private void vu(UtilisateurConnecte qui, Instant le) {
		base.sql("INSERT INTO presence (utilisateur_id, vu_le) VALUES (:id, :le) ON CONFLICT (utilisateur_id) DO UPDATE SET vu_le = EXCLUDED.vu_le")
			.param("id", qui.id()).param("le", Timestamp.from(le)).update();
	}

	@Test
	void une_requete_me_met_en_ligne_aux_yeux_des_autres() {
		var awa = utilisateur("Awa");
		var kofi = utilisateur("Kofi");
		assertThat(mvc.get().uri("/api/me/activite").with(oauth2Login().oauth2User(kofi))).hasStatusOk();

		var r = mvc.get().uri("/api/presence?personnes=" + kofi.id()).with(oauth2Login().oauth2User(awa)).exchange();
		assertThat(r).hasStatusOk();
		assertThat(r).bodyJson().extractingPath("$.masquee").isEqualTo(false);
		assertThat(r).bodyJson().extractingPath("$.elements[0].enLigne").isEqualTo(true);
		assertThat(r).bodyJson().extractingPath("$.elements[0].vuLe").isNull();
	}

	@Test
	void vu_il_y_a_arrondi_a_la_minute_et_plus_de_date_apres_une_semaine() {
		var maintenant = Instant.parse("2026-11-16T12:00:00Z");
		var awa = utilisateur("Awa");
		var kofi = utilisateur("Kofi");
		var amina = utilisateur("Amina");
		var inconnu = utilisateur("Jamais vu");
		vu(kofi, maintenant.minus(Duration.ofMinutes(5)).plusSeconds(17));
		vu(amina, maintenant.minus(Duration.ofDays(8)));

		var lecture = presence.lire(awa.id(), List.of(kofi.id(), amina.id(), inconnu.id()), maintenant);
		assertThat(lecture.masquee()).isFalse();
		assertThat(lecture.elements()).extracting(Presence.Etat::enLigne).containsExactly(false, false, false);
		assertThat(lecture.elements().get(0).vuLe()).isEqualTo(maintenant.minus(Duration.ofMinutes(5)).truncatedTo(ChronoUnit.MINUTES));
		assertThat(lecture.elements().get(1).vuLe()).isNull();
		assertThat(lecture.elements().get(2).vuLe()).isNull();
	}

	@Test
	void cacher_sa_presence_est_reciproque() {
		var maintenant = Instant.now();
		var awa = utilisateur("Awa");
		var kofi = utilisateur("Kofi");
		vu(awa, maintenant);
		vu(kofi, maintenant);

		assertThat(mvc.get().uri("/api/me/presence").with(oauth2Login().oauth2User(kofi))).bodyJson().extractingPath("$.visible").isEqualTo(true);
		assertThat(mvc.put().uri("/api/me/presence").contentType(MediaType.APPLICATION_JSON).content("{\"visible\":false}")
			.with(csrf()).with(oauth2Login().oauth2User(kofi))).hasStatusOk();

		// Awa ne voit plus Kofi…
		var vueAwa = presence.lire(awa.id(), List.of(kofi.id()), maintenant);
		assertThat(vueAwa.elements().get(0).enLigne()).isFalse();
		assertThat(vueAwa.elements().get(0).vuLe()).isNull();
		// … et Kofi ne voit plus personne.
		var vueKofi = presence.lire(kofi.id(), List.of(awa.id()), maintenant);
		assertThat(vueKofi.masquee()).isTrue();
		assertThat(vueKofi.elements().get(0).enLigne()).isFalse();
	}

	@Test
	void requetes_invalides() {
		var awa = utilisateur("Awa");
		assertThat(mvc.get().uri("/api/presence?personnes=pas-un-uuid").with(oauth2Login().oauth2User(awa))).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(mvc.get().uri("/api/presence?personnes=").with(oauth2Login().oauth2User(awa))).hasStatus(HttpStatus.BAD_REQUEST);
		String trop = String.join(",", java.util.stream.Stream.generate(() -> UUID.randomUUID().toString()).limit(51).toList());
		assertThat(mvc.get().uri("/api/presence?personnes=" + trop).with(oauth2Login().oauth2User(awa))).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(mvc.put().uri("/api/me/presence").contentType(MediaType.APPLICATION_JSON).content("{}")
			.with(csrf()).with(oauth2Login().oauth2User(awa))).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(mvc.get().uri("/api/presence?personnes=" + awa.id())).hasStatus(HttpStatus.UNAUTHORIZED);
	}

}
