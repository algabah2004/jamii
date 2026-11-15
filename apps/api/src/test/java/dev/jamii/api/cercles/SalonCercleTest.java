package dev.jamii.api.cercles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
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
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import dev.jamii.api.TestcontainersConfiguration;
import dev.jamii.api.auth.Role;
import dev.jamii.api.auth.UtilisateurConnecte;

/** Le salon du cercle (contracts/cercles.yaml v0.2), sur un vrai PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, CerclesTest.DepotSimule.class })
class SalonCercleTest {

	private static final String IDEE = "{\"titre\":\"Une caisse hors ligne\",\"description\":\"Une caisse qui marche sans internet.\"}";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient base;

	// ---------------------------------------------------------------- aides

	private UtilisateurConnecte utilisateur(String nom) {
		UUID id = UUID.randomUUID();
		Timestamp t = Timestamp.from(Instant.now());
		base.sql("INSERT INTO utilisateur (id, nom, login_github, avatar_url, cree_le, mis_a_jour_le) VALUES (:id, :nom, :login, 'https://avatars.example/a', :t, :t)")
			.param("id", id).param("nom", nom).param("login", "gh-" + id.toString().substring(0, 8)).param("t", t).update();
		return new UtilisateurConnecte(id, Set.of(Role.MEMBRE));
	}

	private MvcTestResult envoyer(String methode, String uri, UtilisateurConnecte qui, String corps) {
		var requete = switch (methode) {
			case "PUT" -> mvc.put().uri(uri);
			case "DELETE" -> mvc.delete().uri(uri);
			default -> mvc.post().uri(uri);
		};
		if (corps != null) {
			requete = requete.contentType(MediaType.APPLICATION_JSON).content(corps);
		}
		return requete.with(csrf()).with(oauth2Login().oauth2User(qui)).exchange();
	}

	private MvcTestResult lire(String uri, UtilisateurConnecte qui) {
		return mvc.get().uri(uri).with(oauth2Login().oauth2User(qui)).exchange();
	}

	private record Cercle(String id, UtilisateurConnecte createur, List<UtilisateurConnecte> membres) {
	}

	/** Un cercle de 4, complet, idées déposées, ordre tiré. */
	private Cercle cercleTire() {
		UtilisateurConnecte createur = utilisateur("Awa");
		var creation = envoyer("POST", "/api/cercles", createur, "{\"nom\":\"Cercle du salon\",\"taille\":4}");
		assertThat(creation).hasStatus(HttpStatus.CREATED);
		String id = creation.getResponse().getHeader("Location").substring("/api/cercles/".length());
		List<UtilisateurConnecte> membres = new ArrayList<>(List.of(createur));
		for (String nom : List.of("Kofi", "Fatou", "Moussa")) {
			UtilisateurConnecte m = utilisateur(nom);
			assertThat(envoyer("POST", "/api/cercles/" + id + "/membres", m, null)).hasStatusOk();
			membres.add(m);
		}
		membres.forEach(m -> assertThat(envoyer("PUT", "/api/cercles/" + id + "/membres/moi/idee", m, IDEE)).hasStatusOk());
		assertThat(envoyer("POST", "/api/cercles/" + id + "/tirage", membres.get(2), null)).hasStatusOk();
		return new Cercle(id, createur, membres);
	}

	private String salon(Cercle c) {
		return "/api/cercles/" + c.id() + "/salon";
	}

	// ---------------------------------------------------------------- tests

	@Test
	void le_salon_raconte_les_etapes_du_cercle_dans_l_ordre() {
		Cercle c = cercleTire();
		var reponse = lire(salon(c), c.createur());
		assertThat(reponse).hasStatusOk();
		assertThat(reponse).bodyJson().extractingPath("$.elements[*].evenement.code").asArray()
			.containsExactly("cercle_cree", "membre_rejoint", "membre_rejoint", "membre_rejoint", "tirage", "tour_ouvert");
		assertThat(reponse).bodyJson().extractingPath("$.elements[0].auteur").isNull();
		assertThat(reponse).bodyJson().extractingPath("$.elements[4].evenement.personne.nom").isEqualTo("Fatou");
		assertThat(reponse).bodyJson().extractingPath("$.elements[5].evenement.tour").isEqualTo(1);
	}

	@Test
	void le_salon_est_reserve_aux_membres() {
		Cercle c = cercleTire();
		UtilisateurConnecte autre = utilisateur("Curieux");
		assertThat(lire(salon(c), autre)).hasStatus(HttpStatus.FORBIDDEN).bodyJson().extractingPath("$.code").isEqualTo("pas_membre");
		assertThat(envoyer("POST", salon(c) + "/messages", autre, "{\"type\":\"texte\",\"texte\":\"Bonjour\"}"))
			.hasStatus(HttpStatus.FORBIDDEN);
		assertThat(lire("/api/cercles/" + UUID.randomUUID() + "/salon", autre)).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void un_membre_ecrit_et_on_relit_seulement_les_nouveaux_messages() throws Exception {
		Cercle c = cercleTire();
		var envoi = envoyer("POST", salon(c) + "/messages", c.membres().get(1), "{\"type\":\"texte\",\"texte\":\"  On se partage les écrans ?  \"}");
		assertThat(envoi).hasStatus(HttpStatus.CREATED);
		assertThat(envoi).bodyJson().extractingPath("$.texte").isEqualTo("On se partage les écrans ?");
		assertThat(envoi).bodyJson().extractingPath("$.auteur.nom").isEqualTo("Kofi");
		long numero = ((Number) com.jayway.jsonpath.JsonPath.read(envoi.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8), "$.numero")).longValue();

		envoyer("POST", salon(c) + "/messages", c.membres().get(2), "{\"type\":\"texte\",\"texte\":\"Je prends la connexion\"}");
		var nouveaux = lire(salon(c) + "?apres=" + numero, c.createur());
		assertThat(nouveaux).bodyJson().extractingPath("$.elements.length()").isEqualTo(1);
		assertThat(nouveaux).bodyJson().extractingPath("$.elements[0].texte").isEqualTo("Je prends la connexion");

		assertThat(envoyer("POST", salon(c) + "/messages", c.createur(), "{\"type\":\"texte\",\"texte\":\"   \"}"))
			.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(envoyer("POST", salon(c) + "/messages", c.createur(), "{\"type\":\"evenement\",\"texte\":\"triche\"}"))
			.hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void un_point_par_jour_et_le_salon_dit_qui_l_a_fait() {
		Cercle c = cercleTire();
		String point = "{\"type\":\"point\",\"point\":{\"fait\":\"Le menu\",\"prevu\":\"La caisse\",\"bloque\":\"\"}}";
		var envoi = envoyer("POST", salon(c) + "/messages", c.membres().get(1), point);
		assertThat(envoi).hasStatus(HttpStatus.CREATED);
		assertThat(envoi).bodyJson().extractingPath("$.point.fait").isEqualTo("Le menu");
		assertThat(envoi).bodyJson().extractingPath("$.point.bloque").isNull();

		assertThat(envoyer("POST", salon(c) + "/messages", c.membres().get(1), point))
			.hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.code").isEqualTo("point_deja_fait");
		assertThat(envoyer("POST", salon(c) + "/messages", c.membres().get(2), "{\"type\":\"point\",\"point\":{\"fait\":\"x\"}}"))
			.hasStatus(HttpStatus.BAD_REQUEST);

		assertThat(lire(salon(c), c.createur())).bodyJson().extractingPath("$.pointsDuJour").asArray()
			.containsExactly(c.membres().get(1).id().toString());
	}

	@Test
	void un_coup_de_main_reste_en_haut_jusqu_a_ce_qu_un_membre_le_resolve() throws Exception {
		Cercle c = cercleTire();
		var demande = envoyer("POST", salon(c) + "/messages", c.membres().get(3), "{\"type\":\"coup_de_main\",\"texte\":\"Le build Android échoue\"}");
		assertThat(demande).hasStatus(HttpStatus.CREATED);
		assertThat(demande).bodyJson().extractingPath("$.coupDeMain.resoluLe").isNull();
		String id = com.jayway.jsonpath.JsonPath.read(demande.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8), "$.id");

		assertThat(lire(salon(c), c.createur())).bodyJson().extractingPath("$.coupsDeMain[0].texte").isEqualTo("Le build Android échoue");

		var resolu = envoyer("POST", salon(c) + "/messages/" + id + "/resolu", c.membres().get(1), null);
		assertThat(resolu).hasStatusOk();
		assertThat(resolu).bodyJson().extractingPath("$.coupDeMain.resoluPar.nom").isEqualTo("Kofi");
		// Idempotent : le premier qui a aidé reste.
		assertThat(envoyer("POST", salon(c) + "/messages/" + id + "/resolu", c.createur(), null))
			.bodyJson().extractingPath("$.coupDeMain.resoluPar.nom").isEqualTo("Kofi");
		assertThat(lire(salon(c), c.createur())).bodyJson().extractingPath("$.coupsDeMain.length()").isEqualTo(0);
	}

	@Test
	void un_seul_appel_a_la_fois_annonce_dans_le_salon_et_termine_par_son_lanceur() {
		Cercle c = cercleTire();
		var lance = envoyer("POST", salon(c) + "/appel", c.membres().get(1), null);
		assertThat(lance).hasStatus(HttpStatus.CREATED);
		assertThat(lance).bodyJson().extractingPath("$.url").asString().contains("jamii-cercle-");
		assertThat(envoyer("POST", salon(c) + "/appel", c.membres().get(2), null)).hasStatusOk()
			.bodyJson().extractingPath("$.lancePar.nom").isEqualTo("Kofi");

		var salonLu = lire(salon(c), c.createur());
		assertThat(salonLu).bodyJson().extractingPath("$.appel.lancePar.nom").isEqualTo("Kofi");
		assertThat(salonLu).bodyJson().extractingPath("$.elements[-1].type").isEqualTo("appel");
		assertThat(salonLu).bodyJson().extractingPath("$.elements[-1].appel.enCours").isEqualTo(true);

		assertThat(envoyer("DELETE", salon(c) + "/appel", c.membres().get(2), null))
			.hasStatus(HttpStatus.FORBIDDEN).bodyJson().extractingPath("$.code").isEqualTo("pas_lanceur");
		assertThat(envoyer("DELETE", salon(c) + "/appel", c.createur(), null)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(lire(salon(c), c.createur())).bodyJson().extractingPath("$.appel").isNull();
	}

	@Test
	void livrer_un_tour_l_annonce_et_ouvre_le_suivant() {
		Cercle c = cercleTire();
		UUID premier = base.sql("SELECT utilisateur_id FROM cercle_membre WHERE cercle_id = :c AND rang = 1")
			.param("c", UUID.fromString(c.id())).query(UUID.class).single();
		assertThat(envoyer("POST", "/api/cercles/" + c.id() + "/tours/1/livrer", c.createur(), null)).hasStatusOk();
		var reponse = lire(salon(c), c.createur());
		assertThat(reponse).bodyJson().extractingPath("$.elements[-2].evenement.code").isEqualTo("tour_livre");
		assertThat(reponse).bodyJson().extractingPath("$.elements[-2].evenement.personne.id").isEqualTo(premier.toString());
		assertThat(reponse).bodyJson().extractingPath("$.elements[-1].evenement.code").isEqualTo("tour_ouvert");
		assertThat(reponse).bodyJson().extractingPath("$.elements[-1].evenement.tour").isEqualTo(2);
	}

	@Test
	void une_decision_est_consignee_et_relue_a_part() {
		Cercle c = cercleTire();
		assertThat(envoyer("POST", salon(c) + "/messages", c.membres().get(1), "{\"type\":\"decision\",\"texte\":\"On part sur Flutter\"}"))
			.hasStatus(HttpStatus.CREATED).bodyJson().extractingPath("$.type").isEqualTo("decision");
		envoyer("POST", salon(c) + "/messages", c.membres().get(2), "{\"type\":\"decision\",\"texte\":\"La démo est vendredi\"}");
		var lu = lire(salon(c), c.createur());
		assertThat(lu).bodyJson().extractingPath("$.decisions[*].texte").asArray().containsExactly("La démo est vendredi", "On part sur Flutter");
		assertThat(lu).bodyJson().extractingPath("$.decisions[0].auteur.nom").isEqualTo("Fatou");
	}

}
