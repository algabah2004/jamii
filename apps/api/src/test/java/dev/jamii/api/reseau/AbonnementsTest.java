package dev.jamii.api.reseau;

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
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import dev.jamii.api.TestcontainersConfiguration;
import dev.jamii.api.auth.Role;
import dev.jamii.api.auth.UtilisateurConnecte;

/** Suivre quelqu'un, les compteurs du profil, les notifications des abonnés (#157). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AbonnementsTest {

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

	private MvcTestResult appel(String methode, String uri, UtilisateurConnecte qui) {
		var requete = switch (methode) {
			case "PUT" -> mvc.put().uri(uri).with(csrf());
			case "DELETE" -> mvc.delete().uri(uri).with(csrf());
			default -> mvc.get().uri(uri);
		};
		return requete.with(oauth2Login().oauth2User(qui)).exchange();
	}

	private long notifications(UtilisateurConnecte pour, String type) {
		return base.sql("SELECT count(*) FROM notification WHERE utilisateur_id = :u AND type = :t")
			.param("u", pour.id()).param("t", type).query(Long.class).single();
	}

	@Test
	void suivre_puis_ne_plus_suivre_met_a_jour_les_compteurs_et_previent_une_seule_fois() {
		var awa = utilisateur("Awa");
		var kofi = utilisateur("Kofi");
		var amina = utilisateur("Amina");
		String route = "/api/personnes/" + kofi.id() + "/abonnement";

		var r = appel("PUT", route, awa);
		assertThat(r).hasStatusOk();
		assertThat(r).bodyJson().extractingPath("$.abonnes").isEqualTo(1);
		assertThat(r).bodyJson().extractingPath("$.jeSuis").isEqualTo(true);
		assertThat(appel("PUT", route, amina)).hasStatusOk();
		// Suivre deux fois ne compte qu'une fois.
		assertThat(appel("PUT", route, awa)).bodyJson().extractingPath("$.abonnes").isEqualTo(2);
		assertThat(notifications(kofi, "nouvel_abonne")).isEqualTo(2);

		var liste = appel("GET", "/api/personnes/" + kofi.id() + "/abonnes", amina);
		assertThat(liste).hasStatusOk();
		assertThat(liste).bodyJson().extractingPath("$.total").isEqualTo(2);
		assertThat(liste).bodyJson().extractingPath("$.elements[0].personne.nom").isEqualTo("Amina");
		assertThat(appel("GET", "/api/personnes/" + awa.id() + "/abonnements", kofi)).bodyJson().extractingPath("$.elements[0].personne.nom").isEqualTo("Kofi");

		// Se désabonner puis se réabonner ne renvoie pas « Awa te suit ».
		assertThat(appel("DELETE", route, awa)).bodyJson().extractingPath("$.jeSuis").isEqualTo(false);
		assertThat(appel("GET", route, kofi)).bodyJson().extractingPath("$.abonnes").isEqualTo(1);
		appel("PUT", route, awa);
		assertThat(notifications(kofi, "nouvel_abonne")).isEqualTo(2);
		// Kofi voit qu'Awa le suit.
		assertThat(appel("GET", "/api/personnes/" + awa.id() + "/abonnement", kofi)).bodyJson().extractingPath("$.meSuit").isEqualTo(true);
	}

	@Test
	void les_abonnes_sont_prevenus_quand_la_personne_suivie_ouvre_un_cercle() {
		var awa = utilisateur("Awa");
		var kofi = utilisateur("Kofi");
		var tiers = utilisateur("Tiers");
		appel("PUT", "/api/personnes/" + kofi.id() + "/abonnement", awa);

		var cree = mvc.post().uri("/api/cercles").contentType(MediaType.APPLICATION_JSON)
			.content("{\"nom\":\"Cercle Flutter\",\"taille\":4}").with(csrf()).with(oauth2Login().oauth2User(kofi)).exchange();
		assertThat(cree).hasStatus(HttpStatus.CREATED);

		assertThat(notifications(awa, "suivi_cercle")).isEqualTo(1);
		assertThat(notifications(tiers, "suivi_cercle")).isZero();
		var n = appel("GET", "/api/me/notifications", awa);
		assertThat(n).bodyJson().extractingPath("$.elements[0].type").isEqualTo("suivi_cercle");
		assertThat(n).bodyJson().extractingPath("$.elements[0].personne.nom").isEqualTo("Kofi");
	}

	@Test
	void refus_soi_meme_inconnu_et_sans_session() {
		var awa = utilisateur("Awa");
		assertThat(appel("PUT", "/api/personnes/" + awa.id() + "/abonnement", awa)).hasStatus(HttpStatus.CONFLICT);
		assertThat(appel("PUT", "/api/personnes/" + UUID.randomUUID() + "/abonnement", awa)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(appel("GET", "/api/personnes/" + UUID.randomUUID() + "/abonnes", awa)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(appel("GET", "/api/personnes/" + awa.id() + "/abonnes?limite=500", awa)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(mvc.get().uri("/api/personnes/" + awa.id() + "/abonnement")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

}
