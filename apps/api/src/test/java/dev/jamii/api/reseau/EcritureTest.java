package dev.jamii.api.reseau;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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

/** « … est en train d'écrire » (#157). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EcritureTest {

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

	private boolean ecrit(UtilisateurConnecte qui, UtilisateurConnecte lecteur) throws Exception {
		var r = mvc.get().uri("/api/conversations/" + qui.id() + "/ecriture").with(oauth2Login().oauth2User(lecteur)).exchange();
		assertThat(r.getResponse().getHeader("Cache-Control")).contains("no-store");
		return r.getResponse().getContentAsString().contains("\"ecrit\":true");
	}

	@Test
	void l_autre_voit_que_j_ecris_et_plus_rien_quand_mon_message_part() throws Exception {
		UtilisateurConnecte awa = utilisateur("Awa");
		UtilisateurConnecte kofi = utilisateur("Kofi");
		UtilisateurConnecte curieux = utilisateur("Curieux");
		assertThat(ecrit(awa, kofi)).isFalse();

		assertThat(mvc.post().uri("/api/conversations/" + kofi.id() + "/ecriture").with(csrf()).with(oauth2Login().oauth2User(awa)).exchange())
			.hasStatus(HttpStatus.NO_CONTENT);
		assertThat(ecrit(awa, kofi)).isTrue();
		// Seul Kofi le voit : ni Awa elle-même, ni une autre personne.
		assertThat(ecrit(kofi, awa)).isFalse();
		assertThat(ecrit(awa, curieux)).isFalse();

		mvc.post().uri("/api/conversations/" + kofi.id() + "/messages").with(csrf()).with(oauth2Login().oauth2User(awa))
			.contentType(MediaType.APPLICATION_JSON).content("{\"texte\":\"Salut\"}").exchange();
		assertThat(ecrit(awa, kofi)).isFalse();
	}

	@Test
	void pas_a_soi_meme_ni_a_une_personne_inconnue() throws Exception {
		UtilisateurConnecte awa = utilisateur("Awa");
		assertThat(mvc.post().uri("/api/conversations/" + awa.id() + "/ecriture").with(csrf()).with(oauth2Login().oauth2User(awa)).exchange())
			.hasStatus(HttpStatus.NOT_FOUND);
		assertThat(mvc.post().uri("/api/conversations/" + UUID.randomUUID() + "/ecriture").with(csrf()).with(oauth2Login().oauth2User(awa)).exchange())
			.hasStatus(HttpStatus.NOT_FOUND);
		assertThat(mvc.post().uri("/api/conversations/pas-un-uuid/ecriture").with(csrf()).with(oauth2Login().oauth2User(awa)).exchange())
			.hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void le_signal_expire_apres_six_secondes_et_la_memoire_se_vide() {
		var horloge = new Clock() {
			Instant maintenant = Instant.parse("2026-11-16T10:00:00Z");

			@Override
			public Instant instant() {
				return maintenant;
			}

			@Override
			public java.time.ZoneId getZone() {
				return ZoneOffset.UTC;
			}

			@Override
			public Clock withZone(java.time.ZoneId zone) {
				return this;
			}
		};
		var ecriture = new Ecriture(horloge);
		UUID awa = UUID.randomUUID();
		UUID kofi = UUID.randomUUID();
		ecriture.signaler(awa, kofi);
		horloge.maintenant = horloge.maintenant.plusSeconds(5);
		assertThat(ecriture.ecrit(awa, kofi)).isTrue();
		horloge.maintenant = horloge.maintenant.plusSeconds(2);
		assertThat(ecriture.ecrit(awa, kofi)).isFalse();

		// Le ménage : les signaux expirés partent avant qu'on dépasse la taille maximale.
		for (int i = 0; i < Ecriture.TAILLE_MAX; i++) {
			ecriture.signaler(UUID.randomUUID(), kofi);
		}
		horloge.maintenant = horloge.maintenant.plusSeconds(10);
		ecriture.signaler(awa, kofi);
		assertThat(ecriture.taille()).isEqualTo(1);
	}

}
