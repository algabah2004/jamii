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

	private MvcTestResult lancer(UtilisateurConnecte qui, String slug, String salon) {
		return mvc.post().uri("/api/communautes/{s}/salons/{salon}/appel", slug, salon).with(csrf()).with(oauth2Login().oauth2User(qui)).exchange();
	}

	private MvcTestResult terminer(UtilisateurConnecte qui, String slug, String salon) {
		return mvc.delete().uri("/api/communautes/{s}/salons/{salon}/appel", slug, salon).with(csrf()).with(oauth2Login().oauth2User(qui)).exchange();
	}

	@Test
	void sans_appel_lance_aucun_salon_n_a_d_appel_et_les_salons_restent_dans_l_ordre() throws Exception {
		String slug = communaute();
		UtilisateurConnecte awa = utilisateur("Awa");
		rejoindre(awa, slug);
		JsonNode salons = corps(get(awa, "/api/communautes/{s}/salons", slug)).get("elements");
		assertThat(salons.get(0).get("slug").asString()).as("dans l'ordre des salons").isEqualTo("general");
		assertThat(salons.get(0).get("appel").isNull()).isTrue();
	}

	@Test
	void lancer_un_appel_cree_une_salle_neuve_l_annonce_dans_le_salon_et_un_second_lancement_le_rejoint() throws Exception {
		String slug = communaute();
		UtilisateurConnecte awa = utilisateur("Awa Diop");
		UtilisateurConnecte kofi = utilisateur("Kofi");
		rejoindre(awa, slug);
		rejoindre(kofi, slug);

		MvcTestResult lance = lancer(awa, slug, "entraide");
		assertThat(lance).hasStatus(HttpStatus.CREATED);
		JsonNode appel = corps(lance);
		assertThat(appel.get("url").asString()).startsWith("https://appels.example/jamii-" + slug + "-entraide-");
		assertThat(appel.get("lancePar").get("nom").asString()).isEqualTo("Awa Diop");

		// Un seul appel à la fois : Kofi rejoint celui d'Awa.
		MvcTestResult rejoint = lancer(kofi, slug, "entraide");
		assertThat(rejoint).hasStatus(HttpStatus.OK);
		assertThat(corps(rejoint).get("id").asString()).isEqualTo(appel.get("id").asString());

		JsonNode entraide = corps(get(kofi, "/api/communautes/{s}/salons", slug)).get("elements").get(1);
		assertThat(entraide.get("appel").get("id").asString()).isEqualTo(appel.get("id").asString());
		JsonNode messages = corps(get(kofi, "/api/communautes/{s}/salons/entraide/messages", slug)).get("elements");
		assertThat(messages).hasSize(1);
		assertThat(messages.get(0).get("appel").get("enCours").asBoolean()).isTrue();
		assertThat(messages.get(0).get("auteur").get("nom").asString()).isEqualTo("Awa Diop");
	}

	@Test
	void seuls_le_lanceur_et_les_moderateurs_terminent_un_appel_et_la_salle_suivante_est_neuve() throws Exception {
		String slug = communaute();
		UtilisateurConnecte awa = utilisateur("Awa");
		UtilisateurConnecte kofi = utilisateur("Kofi");
		rejoindre(awa, slug);
		rejoindre(kofi, slug);
		String premiere = corps(lancer(awa, slug, "general")).get("url").asString();

		MvcTestResult refuse = terminer(kofi, slug, "general");
		assertThat(refuse).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(corps(refuse).get("code").asString()).isEqualTo("pas_lanceur");

		assertThat(terminer(awa, slug, "general")).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(terminer(awa, slug, "general")).as("plus d'appel en cours").hasStatus(HttpStatus.NOT_FOUND);
		assertThat(corps(get(kofi, "/api/communautes/{s}/salons", slug)).get("elements").get(0).get("appel").isNull()).isTrue();
		assertThat(corps(get(kofi, "/api/communautes/{s}/salons/general/messages", slug)).get("elements").get(0).get("appel").get("enCours").asBoolean())
			.as("l'annonce dit que l'appel est terminé").isFalse();

		// Kofi en lance un autre, dans une salle neuve ; un modérateur peut le terminer.
		String seconde = corps(lancer(kofi, slug, "general")).get("url").asString();
		assertThat(seconde).isNotEqualTo(premiere);
		base.sql("UPDATE adhesion SET role = 'moderateur' WHERE utilisateur_id = :u").param("u", awa.id()).update();
		assertThat(terminer(awa, slug, "general")).hasStatus(HttpStatus.NO_CONTENT);
	}

	@Test
	void un_appel_se_termine_tout_seul_apres_une_heure_et_un_non_membre_ne_peut_ni_lancer_ni_voir_la_salle() throws Exception {
		String slug = communaute();
		UtilisateurConnecte awa = utilisateur("Awa");
		UtilisateurConnecte passant = utilisateur("Passant");
		rejoindre(awa, slug);
		lancer(awa, slug, "general");

		JsonNode vu = corps(get(passant, "/api/communautes/{s}/salons", slug)).get("elements").get(0).get("appel");
		assertThat(vu.get("url").isNull()).as("pas d'adresse pour un non-membre").isTrue();
		MvcTestResult refuse = lancer(passant, slug, "general");
		assertThat(refuse).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(corps(refuse).get("code").asString()).isEqualTo("pas_membre");

		base.sql("UPDATE appel_salon SET lance_le = lance_le - interval '61 minutes'").update();
		assertThat(corps(get(awa, "/api/communautes/{s}/salons", slug)).get("elements").get(0).get("appel").isNull()).isTrue();
		assertThat(lancer(awa, slug, "general")).as("un nouvel appel peut partir").hasStatus(HttpStatus.CREATED);
		assertThat(lancer(awa, slug, "inconnu")).hasStatus(HttpStatus.NOT_FOUND);
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
		// `apres` : la plus grande version connue (#157).
		long premier = corps(envoye).get("version").asLong();
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

	private MvcTestResult agir(String methode, UtilisateurConnecte qui, String uri, String corps) {
		var requete = switch (methode) {
			case "PATCH" -> mvc.patch().uri(uri);
			case "DELETE" -> mvc.delete().uri(uri);
			default -> mvc.post().uri(uri);
		};
		if (corps != null) {
			requete = requete.contentType(MediaType.APPLICATION_JSON).content(corps);
		}
		return requete.with(csrf()).with(oauth2Login().oauth2User(qui)).exchange();
	}

	@Test
	void repondre_corriger_reagir_et_un_moderateur_supprime_le_message_d_un_autre() throws Exception {
		String slug = communaute();
		UtilisateurConnecte awa = utilisateur("Awa");
		UtilisateurConnecte kofi = utilisateur("Kofi");
		UtilisateurConnecte fatou = utilisateur("Fatou");
		UtilisateurConnecte curieux = utilisateur("Curieux");
		rejoindre(awa, slug);
		rejoindre(kofi, slug);
		rejoindre(fatou, slug);
		String salon = "/api/communautes/" + slug + "/salons/entraide/messages";

		String question = corps(ecrire(awa, slug, "Qui connaît Hive ?")).get("id").asString();
		MvcTestResult reponse = agir("POST", kofi, salon, "{\"texte\":\"Moi\",\"reponseA\":\"" + question + "\"}");
		assertThat(reponse).hasStatus(HttpStatus.CREATED);
		assertThat(corps(reponse).get("reponseA").get("auteur").get("nom").asString()).isEqualTo("Awa");
		assertThat(corps(reponse).get("reponseA").get("texte").asString()).isEqualTo("Qui connaît Hive ?");
		long vu = corps(reponse).get("version").asLong();

		// Awa corrige, Kofi réagit : Fatou, qui relit depuis `vu`, reçoit la question à jour.
		assertThat(agir("PATCH", awa, salon + "/" + question, "{\"texte\":\"Qui connaît bien Hive ?\"}")).hasStatusOk();
		assertThat(agir("POST", kofi, salon + "/" + question + "/reactions", "{\"emoji\":\"🙏\",\"active\":true}")).hasStatusOk();
		JsonNode relus = corps(get(fatou, salon + "?apres={n}", vu)).get("elements");
		assertThat(relus).hasSize(1);
		assertThat(relus.get(0).get("texte").asString()).isEqualTo("Qui connaît bien Hive ?");
		assertThat(relus.get(0).get("reactions").get(0).get("noms").get(0).asString()).isEqualTo("Kofi");

		// Ni corriger ni supprimer le message d'un autre ; il faut être membre pour réagir.
		assertThat(agir("PATCH", kofi, salon + "/" + question, "{\"texte\":\"pirate\"}")).hasStatus(HttpStatus.CONFLICT);
		assertThat(agir("DELETE", kofi, salon + "/" + question, null)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(agir("POST", curieux, salon + "/" + question + "/reactions", "{\"emoji\":\"👍\",\"active\":true}"))
			.hasStatus(HttpStatus.FORBIDDEN);

		// Un modérateur supprime le message de Kofi : tout le monde voit qu'il a été modéré.
		base.sql("UPDATE adhesion SET role = 'moderateur' WHERE utilisateur_id = :u").param("u", fatou.id()).update();
		String idReponse = corps(reponse).get("id").asString();
		assertThat(agir("DELETE", fatou, salon + "/" + idReponse, null)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(agir("DELETE", awa, salon + "/" + question, null)).hasStatus(HttpStatus.NO_CONTENT);
		JsonNode tous = corps(get(kofi, salon)).get("elements");
		assertThat(tous.get(0).get("supprime").asString()).isEqualTo("auteur");
		assertThat(tous.get(0).get("reactions")).isEmpty();
		assertThat(tous.get(1).get("supprime").asString()).isEqualTo("moderation");
		assertThat(tous.get(1).get("texte").asString()).isEmpty();
		assertThat(agir("POST", kofi, salon + "/" + question + "/reactions", "{\"emoji\":\"👍\",\"active\":true}"))
			.hasStatus(HttpStatus.CONFLICT);
		assertThat(agir("POST", kofi, salon + "/" + question + "/reactions", "{\"emoji\":\"bonjour\",\"active\":true}"))
			.hasStatus(HttpStatus.BAD_REQUEST);
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
	void les_membres_moderateurs_puis_actifs_puis_les_autres_sans_email() throws Exception {
		String slug = communaute();
		UtilisateurConnecte awa = utilisateur("Awa");
		UtilisateurConnecte kofi = utilisateur("Kofi");
		UtilisateurConnecte nia = utilisateur("Nia");
		rejoindre(awa, slug);
		rejoindre(kofi, slug);
		rejoindre(nia, slug);
		base.sql("UPDATE adhesion SET role = 'moderateur' WHERE utilisateur_id = :u").param("u", nia.id()).update();
		ecrire(awa, slug, "Bonjour");

		MvcTestResult reponse = get(kofi, "/api/communautes/{s}/membres", slug);
		assertThat(reponse).hasStatusOk();
		JsonNode corps = corps(reponse);
		assertThat(corps.get("total").asLong()).isEqualTo(3);
		JsonNode membres = corps.get("elements");
		assertThat(membres.get(0).get("personne").get("nom").asString()).isEqualTo("Nia");
		assertThat(membres.get(0).get("role").asString()).isEqualTo("moderateur");
		assertThat(membres.get(1).get("personne").get("nom").asString()).isEqualTo("Awa");
		assertThat(membres.get(1).get("actif").asBoolean()).isTrue();
		assertThat(membres.get(2).get("actif").asBoolean()).isFalse();
		assertThat(reponse.getResponse().getContentAsString()).doesNotContain("@example.org");

		assertThat(get(kofi, "/api/communautes/{s}/membres?limite=0", slug)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(get(kofi, "/api/communautes/inconnue/membres")).hasStatus(HttpStatus.NOT_FOUND);
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
