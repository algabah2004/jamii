package dev.jamii.api.communautes;

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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Les salons d'une communauté (contracts/communautes.yaml v0.2, #155), sur un vrai PostgreSQL. */
@SpringBootTest(properties = "jamii.appels.url=https://appels.example/")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SalonsTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient base;

	@Autowired
	JsonMapper json;

	private UtilisateurConnecte utilisateur(String nom) {
		UUID id = UUID.randomUUID();
		Timestamp t = Timestamp.from(Instant.now());
		base.sql("INSERT INTO utilisateur (id, nom, email, cree_le, mis_a_jour_le) VALUES (:id, :nom, :email, :t, :t)")
			.param("id", id).param("nom", nom).param("email", id + "@example.org").param("t", t).update();
		return new UtilisateurConnecte(id, Set.of(Role.MEMBRE));
	}

	/** Une communauté propre à chaque test, avec un salon : les tests ne se gênent pas. */
	private String communaute() {
		String slug = "salons-" + UUID.randomUUID().toString().substring(0, 8);
		UUID id = UUID.randomUUID();
		base.sql("INSERT INTO communaute (id, slug, nom, description, theme, cree_le) VALUES (:id, :s, 'Test', 'Pour les tests.', 'test', now())")
			.param("id", id).param("s", slug).update();
		base.sql("""
				INSERT INTO salon (id, communaute_id, slug, nom, sujet, ordre, salle_appel)
				VALUES (gen_random_uuid(), :c, 'entraide', 'entraide', 'Une question rapide.', 2, :salle),
				       (gen_random_uuid(), :c, 'general', 'général', 'Parler de tout.', 1, :salle || '-g')
				""").param("c", id).param("salle", "jamii-" + slug + "-x").update();
		return slug;
	}

	private void rejoindre(UtilisateurConnecte qui, String slug) {
		assertThat(mvc.post().uri("/api/communautes/{s}/adhesion", slug).with(csrf()).with(oauth2Login().oauth2User(qui))
			.exchange()).hasStatus(HttpStatus.NO_CONTENT);
	}

	private MvcTestResult get(UtilisateurConnecte qui, String uri, Object... variables) {
		return mvc.get().uri(uri, variables).with(oauth2Login().oauth2User(qui)).exchange();
	}

	private MvcTestResult ecrire(UtilisateurConnecte qui, String slug, String texte) {
		return mvc.post().uri("/api/communautes/{s}/salons/entraide/messages", slug).contentType(MediaType.APPLICATION_JSON)
			.content("{\"texte\":" + json.writeValueAsString(texte) + "}").with(csrf()).with(oauth2Login().oauth2User(qui))
			.exchange();
	}

	private JsonNode corps(MvcTestResult r) throws Exception {
		return json.readTree(r.getResponse().getContentAsString());
	}

	@Test
	void chaque_communaute_de_depart_a_trois_salons() throws Exception {
		JsonNode salons = corps(get(utilisateur("Awa"), "/api/communautes/flutter/salons")).get("elements");
		assertThat(salons).hasSize(3);
		assertThat(salons.get(0).get("slug").asString()).isEqualTo("general");
		assertThat(salons.get(1).get("slug").asString()).isEqualTo("entraide");
		assertThat(salons.get(2).get("slug").asString()).isEqualTo("projets");
	}

	@Test
	void l_adresse_de_l_appel_n_est_donnee_qu_aux_membres() throws Exception {
		String slug = communaute();
		UtilisateurConnecte awa = utilisateur("Awa");

		assertThat(corps(get(awa, "/api/communautes/{s}/salons", slug)).get("elements").get(0).get("appelUrl").isNull()).isTrue();

		rejoindre(awa, slug);
		JsonNode general = corps(get(awa, "/api/communautes/{s}/salons", slug)).get("elements").get(0);
		assertThat(general.get("slug").asString()).as("dans l'ordre des salons").isEqualTo("general");
		assertThat(general.get("appelUrl").asString()).isEqualTo("https://appels.example/jamii-" + slug + "-x-g");
	}

	@Test
	void un_membre_ecrit_les_autres_lisent_et_relisent_seulement_les_nouveaux() throws Exception {
		String slug = communaute();
		UtilisateurConnecte awa = utilisateur("Awa Diop");
		UtilisateurConnecte kofi = utilisateur("Kofi");
		rejoindre(awa, slug);

		MvcTestResult envoye = ecrire(awa, slug, "  Quelqu'un connaît Hive ?  ");
		assertThat(envoye).hasStatus(HttpStatus.CREATED);
		assertThat(corps(envoye).get("texte").asString()).isEqualTo("Quelqu'un connaît Hive ?");
		assertThat(corps(envoye).get("auteur").get("nom").asString()).isEqualTo("Awa Diop");
		long premier = corps(envoye).get("numero").asLong();
		ecrire(awa, slug, "Pour garder les commandes hors ligne.");

		MvcTestResult lus = get(kofi, "/api/communautes/{s}/salons/entraide/messages", slug);
		assertThat(lus).hasStatusOk();
		assertThat(lus.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
		JsonNode tous = corps(lus).get("elements");
		assertThat(tous).hasSize(2);
		assertThat(tous.get(0).get("texte").asString()).as("du plus ancien au plus récent").isEqualTo("Quelqu'un connaît Hive ?");

		JsonNode nouveaux = corps(get(kofi, "/api/communautes/{s}/salons/entraide/messages?apres={n}", slug, premier)).get("elements");
		assertThat(nouveaux).hasSize(1);
		assertThat(nouveaux.get(0).get("texte").asString()).isEqualTo("Pour garder les commandes hors ligne.");
		assertThat(corps(get(kofi, "/api/communautes/{s}/salons/general/messages", slug)).get("elements")).isEmpty();
	}

	@Test
	void il_faut_etre_membre_pour_ecrire_et_le_texte_ne_peut_pas_etre_vide() throws Exception {
		String slug = communaute();
		UtilisateurConnecte awa = utilisateur("Awa");

		MvcTestResult refuse = ecrire(awa, slug, "Bonjour");
		assertThat(refuse).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(corps(refuse).get("code").asString()).isEqualTo("pas_membre");

		rejoindre(awa, slug);
		assertThat(ecrire(awa, slug, "   ")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(ecrire(awa, slug, "x".repeat(2001))).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(mvc.post().uri("/api/communautes/{s}/salons/entraide/messages", slug).contentType(MediaType.APPLICATION_JSON)
			.content("{\"texte\":\"sans jeton\"}").with(oauth2Login().oauth2User(awa)).exchange()).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void salon_ou_communaute_inconnus_404_et_parametres_invalides_400() {
		String slug = communaute();
		UtilisateurConnecte awa = utilisateur("Awa");
		assertThat(get(awa, "/api/communautes/{s}/salons/inconnu/messages", slug)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(get(awa, "/api/communautes/inconnue/salons")).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(get(awa, "/api/communautes/{s}/salons/entraide/messages?apres=-1", slug)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(get(awa, "/api/communautes/{s}/salons/entraide/messages?limite=500", slug)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(mvc.get().uri("/api/communautes/{s}/salons", slug).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void au_dela_de_vingt_messages_par_minute_429_avec_le_delai() throws Exception {
		String slug = communaute();
		UtilisateurConnecte awa = utilisateur("Awa");
		rejoindre(awa, slug);
		for (int i = 0; i < Salons.MESSAGES_PAR_MINUTE; i++) {
			assertThat(ecrire(awa, slug, "message " + i)).hasStatus(HttpStatus.CREATED);
		}
		MvcTestResult trop = ecrire(awa, slug, "un de trop");
		assertThat(trop).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(Long.parseLong(trop.getResponse().getHeader("Retry-After"))).isBetween(1L, 61L);
	}

	@Test
	void les_messages_partent_avec_le_compte_et_sont_dans_son_export() throws Exception {
		String slug = communaute();
		UtilisateurConnecte awa = utilisateur("Awa");
		rejoindre(awa, slug);
		ecrire(awa, slug, "Mon message");

		JsonNode export = corps(get(awa, "/api/me/export")).get("communautes").get("messagesSalons");
		assertThat(export).hasSize(1);
		assertThat(export.get(0).get("salon").asString()).isEqualTo("entraide");
		assertThat(export.get(0).get("communaute").asString()).isEqualTo(slug);

		assertThat(mvc.delete().uri("/api/me").with(csrf()).with(oauth2Login().oauth2User(awa)).exchange())
			.hasStatus(HttpStatus.ACCEPTED);
		assertThat(base.sql("SELECT count(*) FROM message_salon WHERE auteur_id = :u").param("u", awa.id())
			.query(Long.class).single()).isZero();
	}

}
