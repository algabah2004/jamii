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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import dev.jamii.api.TestcontainersConfiguration;
import dev.jamii.api.auth.Role;
import dev.jamii.api.auth.UtilisateurConnecte;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Créer une communauté et ses salons (contracts/communautes.yaml v0.4), sur un vrai PostgreSQL. */
@SpringBootTest(properties = "jamii.appels.url=https://appels.example")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CreationCommunautesTest {

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

	private MvcTestResult post(UtilisateurConnecte qui, String uri, String corps, Object... variables) {
		return mvc.post().uri(uri, variables).contentType(MediaType.APPLICATION_JSON).content(corps).with(csrf())
			.with(oauth2Login().oauth2User(qui)).exchange();
	}

	private MvcTestResult creer(UtilisateurConnecte qui, String nom) {
		return post(qui, "/api/communautes", """
				{"nom":%s,"description":"Des devs qui s'entraident au Sahel.","theme":"web"}""".formatted(json.writeValueAsString(nom)));
	}

	private JsonNode corps(MvcTestResult r) throws Exception {
		return json.readTree(r.getResponse().getContentAsString());
	}

	private String unique(String prefixe) {
		return prefixe + " " + UUID.randomUUID().toString().substring(0, 6);
	}

	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D };

	private String image(UtilisateurConnecte qui, byte[] octets, String nom, String type) throws Exception {
		var r = mvc.post().uri("/api/fichiers").multipart().file(new MockMultipartFile("fichier", nom, type, octets))
			.with(csrf()).with(oauth2Login().oauth2User(qui)).exchange();
		return corps(r).get("id").asString();
	}

	private MvcTestResult put(UtilisateurConnecte qui, String uri, String corps, Object... variables) {
		return mvc.put().uri(uri, variables).contentType(MediaType.APPLICATION_JSON).content(corps).with(csrf())
			.with(oauth2Login().oauth2User(qui)).exchange();
	}

	@Test
	void une_couverture_choisie_a_la_creation_s_affiche_et_sans_elle_le_motif_reste() throws Exception {
		UtilisateurConnecte awa = utilisateur("Awa");
		String photo = image(awa, PNG, "couverture.png", "image/png");
		var avec = post(awa, "/api/communautes", """
				{"nom":%s,"description":"Des devs qui s'entraident au Sahel.","theme":"web","couvertureId":"%s"}""".formatted(json.writeValueAsString(unique("Couverture")), photo));
		assertThat(avec).hasStatus(HttpStatus.CREATED);
		assertThat(corps(avec).get("couverture").asString()).isEqualTo("/api/fichiers/" + photo);

		var sans = creer(awa, unique("Motif"));
		assertThat(corps(sans).get("couverture").isNull()).isTrue();
	}

	@Test
	void la_couverture_doit_etre_une_image_publique_envoyee_par_soi() throws Exception {
		UtilisateurConnecte awa = utilisateur("Awa");
		UtilisateurConnecte kofi = utilisateur("Kofi");
		String deKofi = image(kofi, PNG, "a.png", "image/png");
		String document = image(awa, "Un simple texte".getBytes(java.nio.charset.StandardCharsets.UTF_8), "notes.txt", "text/plain");
		for (String id : new String[] { deKofi, document, UUID.randomUUID().toString() }) {
			var r = post(awa, "/api/communautes", """
					{"nom":%s,"description":"Des devs qui s'entraident au Sahel.","theme":"web","couvertureId":"%s"}""".formatted(json.writeValueAsString(unique("Refus")), id));
			assertThat(r).as(id).hasStatus(HttpStatus.BAD_REQUEST);
			assertThat(r).bodyJson().extractingPath("$.erreurs[0].champ").isEqualTo("couvertureId");
		}
	}

	@Test
	void seul_un_moderateur_change_la_couverture_ou_revient_au_motif() throws Exception {
		UtilisateurConnecte awa = utilisateur("Awa");
		UtilisateurConnecte kofi = utilisateur("Kofi");
		String slug = corps(creer(awa, unique("Sahel"))).get("slug").asString();
		String photo = image(awa, PNG, "c.png", "image/png");

		assertThat(put(kofi, "/api/communautes/{slug}/couverture", "{\"fichierId\":\"%s\"}".formatted(image(kofi, PNG, "k.png", "image/png")), slug))
			.hasStatus(HttpStatus.FORBIDDEN);
		var change = put(awa, "/api/communautes/{slug}/couverture", "{\"fichierId\":\"%s\"}".formatted(photo), slug);
		assertThat(change).hasStatus(HttpStatus.OK);
		assertThat(corps(change).get("couverture").asString()).isEqualTo("/api/fichiers/" + photo);
		// Toute personne connectée voit la couverture (une image publique).
		assertThat(mvc.get().uri("/api/fichiers/" + photo).with(oauth2Login().oauth2User(kofi)).exchange()).hasStatus(HttpStatus.OK);

		var motif = put(awa, "/api/communautes/{slug}/couverture", "{\"fichierId\":null}", slug);
		assertThat(motif).hasStatus(HttpStatus.OK);
		assertThat(corps(motif).get("couverture").isNull()).isTrue();
		assertThat(put(awa, "/api/communautes/{slug}/couverture", "{}", "inconnue-" + UUID.randomUUID())).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void creer_une_communaute_en_fait_le_moderateur_avec_trois_salons() throws Exception {
		UtilisateurConnecte awa = utilisateur("Awa");
		String nom = unique("Devs du Sahel !");

		MvcTestResult reponse = creer(awa, nom);

		assertThat(reponse).hasStatus(HttpStatus.CREATED);
		JsonNode c = corps(reponse);
		String slug = c.get("slug").asString();
		assertThat(slug).startsWith("devs-du-sahel-");
		assertThat(reponse.getResponse().getHeader("Location")).isEqualTo("/api/communautes/" + slug);
		assertThat(c.get("membre").asBoolean()).isTrue();
		assertThat(c.get("moderateur").asBoolean()).isTrue();
		assertThat(c.get("nombreMembres").asInt()).isEqualTo(1);

		JsonNode salons = corps(mvc.get().uri("/api/communautes/{s}/salons", slug).with(oauth2Login().oauth2User(awa)).exchange())
			.get("elements");
		assertThat(salons).hasSize(3);
		assertThat(salons.get(0).get("appel").isNull()).as("pas d'appel tant que personne n'en lance").isTrue();
	}

	@Test
	void deux_communautes_du_meme_nom_ont_des_noms_courts_differents_et_les_accents_disparaissent() throws Exception {
		UtilisateurConnecte awa = utilisateur("Awa");
		String nom = unique("Équipe Fès");
		String premier = corps(creer(awa, nom)).get("slug").asString();
		String second = corps(creer(awa, nom)).get("slug").asString();
		assertThat(premier).startsWith("equipe-fes-");
		assertThat(second).isEqualTo(premier + "-2");
	}

	@Test
	void au_dela_de_trois_communautes_par_jour_429() {
		UtilisateurConnecte awa = utilisateur("Awa");
		for (int i = 0; i < CreationCommunautes.COMMUNAUTES_PAR_JOUR; i++) {
			assertThat(creer(awa, unique("Communauté"))).hasStatus(HttpStatus.CREATED);
		}
		MvcTestResult trop = creer(awa, unique("Une de trop"));
		assertThat(trop).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(trop.getResponse().getHeader("Retry-After")).isNotNull();
	}

	@Test
	void champs_invalides_400_et_sans_jeton_csrf_403() {
		UtilisateurConnecte awa = utilisateur("Awa");
		assertThat(post(awa, "/api/communautes", """
				{"nom":"ab","description":"court","theme":"inconnu","pays":"sn"}""")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(post(awa, "/api/communautes", """
				{"nom":"!!!","description":"Une description assez longue.","theme":"web"}""")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(mvc.post().uri("/api/communautes").contentType(MediaType.APPLICATION_JSON)
			.content("{\"nom\":\"Sans jeton\",\"description\":\"Une description assez longue.\",\"theme\":\"web\"}")
			.with(oauth2Login().oauth2User(awa)).exchange()).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void seul_un_moderateur_cree_un_salon_et_un_nom_deja_pris_409() throws Exception {
		UtilisateurConnecte awa = utilisateur("Awa");
		UtilisateurConnecte kofi = utilisateur("Kofi");
		String slug = corps(creer(awa, unique("Salons"))).get("slug").asString();
		mvc.post().uri("/api/communautes/{s}/adhesion", slug).with(csrf()).with(oauth2Login().oauth2User(kofi)).exchange();

		MvcTestResult refuse = post(kofi, "/api/communautes/{s}/salons", "{\"nom\":\"offres-emploi\",\"sujet\":\"Les offres d'emploi.\"}", slug);
		assertThat(refuse).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(corps(refuse).get("code").asString()).isEqualTo("pas_moderateur");

		MvcTestResult cree = post(awa, "/api/communautes/{s}/salons", "{\"nom\":\"# Offres d'emploi\",\"sujet\":\"Les offres d'emploi de la région.\"}", slug);
		assertThat(cree).hasStatus(HttpStatus.CREATED);
		assertThat(corps(cree).get("slug").asString()).isEqualTo("offres-d-emploi");
		assertThat(corps(cree).get("appel").isNull()).isTrue();

		MvcTestResult double_ = post(awa, "/api/communautes/{s}/salons", "{\"nom\":\"Offres d emploi\",\"sujet\":\"Encore les offres.\"}", slug);
		assertThat(double_).hasStatus(HttpStatus.CONFLICT);
		assertThat(corps(double_).get("code").asString()).isEqualTo("salon_existe");

		JsonNode salons = corps(mvc.get().uri("/api/communautes/{s}/salons", slug).with(oauth2Login().oauth2User(kofi)).exchange()).get("elements");
		assertThat(salons).hasSize(4);
		assertThat(salons.get(3).get("slug").asString()).as("le nouveau salon vient après les autres").isEqualTo("offres-d-emploi");
	}

	@Test
	void un_moderateur_supprime_un_salon_et_ses_messages_mais_jamais_general() throws Exception {
		UtilisateurConnecte awa = utilisateur("Awa");
		UtilisateurConnecte kofi = utilisateur("Kofi");
		String slug = corps(creer(awa, unique("Suppression"))).get("slug").asString();
		mvc.post().uri("/api/communautes/{s}/adhesion", slug).with(csrf()).with(oauth2Login().oauth2User(kofi)).exchange();
		post(awa, "/api/communautes/{s}/salons/projets/messages", "{\"texte\":\"Un message\"}", slug);

		MvcTestResult refuse = mvc.delete().uri("/api/communautes/{s}/salons/projets", slug).with(csrf())
			.with(oauth2Login().oauth2User(kofi)).exchange();
		assertThat(refuse).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(corps(refuse).get("code").asString()).isEqualTo("pas_moderateur");

		MvcTestResult general = mvc.delete().uri("/api/communautes/{s}/salons/general", slug).with(csrf())
			.with(oauth2Login().oauth2User(awa)).exchange();
		assertThat(general).hasStatus(HttpStatus.CONFLICT);
		assertThat(corps(general).get("code").asString()).isEqualTo("salon_protege");

		assertThat(mvc.delete().uri("/api/communautes/{s}/salons/projets", slug).with(oauth2Login().oauth2User(awa)).exchange())
			.as("sans jeton CSRF").hasStatus(HttpStatus.FORBIDDEN);
		assertThat(mvc.delete().uri("/api/communautes/{s}/salons/projets", slug).with(csrf()).with(oauth2Login().oauth2User(awa)).exchange())
			.hasStatus(HttpStatus.NO_CONTENT);
		assertThat(mvc.delete().uri("/api/communautes/{s}/salons/projets", slug).with(csrf()).with(oauth2Login().oauth2User(awa)).exchange())
			.as("déjà supprimé").hasStatus(HttpStatus.NOT_FOUND);

		JsonNode salons = corps(mvc.get().uri("/api/communautes/{s}/salons", slug).with(oauth2Login().oauth2User(kofi)).exchange()).get("elements");
		assertThat(salons).hasSize(2);
		assertThat(base.sql("""
				SELECT count(*) FROM message_salon m JOIN salon s ON s.id = m.salon_id JOIN communaute c ON c.id = s.communaute_id
				WHERE c.slug = :s
				""").param("s", slug).query(Long.class).single()).as("les messages partent avec le salon").isZero();
	}

	@Test
	void un_salon_dans_une_communaute_inconnue_404_et_nom_vide_400() throws Exception {
		UtilisateurConnecte awa = utilisateur("Awa");
		String slug = corps(creer(awa, unique("Vide"))).get("slug").asString();
		assertThat(post(awa, "/api/communautes/inconnue/salons", "{\"nom\":\"test\",\"sujet\":\"Un sujet valable.\"}")).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(post(awa, "/api/communautes/{s}/salons", "{\"nom\":\"#\",\"sujet\":\"Un sujet valable.\"}", slug)).hasStatus(HttpStatus.BAD_REQUEST);
	}

}
