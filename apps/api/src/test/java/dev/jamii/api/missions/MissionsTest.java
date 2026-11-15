package dev.jamii.api.missions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;

import java.net.URLEncoder;
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

import dev.jamii.api.TestcontainersConfiguration;
import dev.jamii.api.auth.Role;
import dev.jamii.api.auth.UtilisateurConnecte;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Publier une mission et la lire (contracts/missions.yaml, tags « missions »), sur un vrai PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MissionsTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient base;

	@Autowired
	JsonMapper json;

	// ---------------------------------------------------------------- aides

	private UtilisateurConnecte utilisateur(String nom) {
		UUID id = UUID.randomUUID();
		Timestamp t = Timestamp.from(Instant.now());
		base.sql("INSERT INTO utilisateur (id, nom, email, cree_le, mis_a_jour_le) VALUES (:id, :nom, :email, :t, :t)")
			.param("id", id).param("nom", nom).param("email", nom + "." + id + "@secret.example").param("t", t).update();
		return new UtilisateurConnecte(id, Set.of(Role.MEMBRE));
	}

	private static String corps(String visibilite) {
		return """
				{"titre":"Boutique en ligne pour un atelier","besoin":"Montrer nos modèles avec photos et prix, recevoir les commandes sur WhatsApp.",
				"competences":["vue","spring"],"visibilite":"%s","budget":{"centimes":450000,"devise":"XOF"},"pays":"SN"}
				""".formatted(visibilite);
	}

	private MvcTestResult creer(String corps, UtilisateurConnecte qui) {
		return mvc.post().uri("/api/missions").contentType(MediaType.APPLICATION_JSON).content(corps)
			.with(csrf()).with(oauth2Login().oauth2User(qui)).exchange();
	}

	private MvcTestResult action(String chemin, String corps, UtilisateurConnecte qui) {
		return mvc.post().uri(chemin).contentType(MediaType.APPLICATION_JSON).content(corps)
			.with(csrf()).with(oauth2Login().oauth2User(qui)).exchange();
	}

	private MvcTestResult lire(String uri, UtilisateurConnecte qui) {
		return qui == null ? mvc.get().uri(uri).exchange() : mvc.get().uri(uri).with(oauth2Login().oauth2User(qui)).exchange();
	}

	private JsonNode corpsDe(MvcTestResult r) throws Exception {
		return json.readTree(r.getResponse().getContentAsString());
	}

	private JsonNode mission(String visibilite, UtilisateurConnecte client) throws Exception {
		MvcTestResult r = creer(corps(visibilite), client);
		assertThat(r).hasStatus(HttpStatus.CREATED);
		return corpsDe(r);
	}

	private JsonNode publiee(String visibilite, UtilisateurConnecte client) throws Exception {
		JsonNode m = mission(visibilite, client);
		MvcTestResult r = action("/api/missions/" + m.get("id").asString() + "/publier", "{\"version\":" + m.get("version") + "}", client);
		assertThat(r).hasStatus(HttpStatus.OK);
		return corpsDe(r);
	}

	// ---------------------------------------------------------------- créer et publier

	@Test
	void un_brouillon_n_est_visible_que_de_son_client() throws Exception {
		UtilisateurConnecte awa = utilisateur("awa");
		MvcTestResult cree = creer(corps("publique"), awa);
		assertThat(cree).hasStatus(HttpStatus.CREATED);
		JsonNode m = corpsDe(cree);
		String id = m.get("id").asString();
		assertThat(cree.getResponse().getHeader("Location")).isEqualTo("/api/missions/" + id);
		assertThat(m.get("statut").asString()).isEqualTo("brouillon");
		assertThat(m.get("client").get("nom").asString()).isEqualTo("awa");
		assertThat(m.get("client").has("email")).isFalse();
		assertThat(m.get("moi").get("role").asString()).isEqualTo("client");

		assertThat(lire("/api/missions/" + id, awa)).hasStatus(HttpStatus.OK);
		assertThat(lire("/api/missions/" + id, utilisateur("kofi"))).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(lire("/api/missions/" + id, null)).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void publier_rend_une_mission_publique_visible_sans_connexion() throws Exception {
		UtilisateurConnecte awa = utilisateur("awa");
		JsonNode m = publiee("publique", awa);
		assertThat(m.get("statut").asString()).isEqualTo("publiee");
		assertThat(m.get("publieeLe").isNull()).isFalse();

		MvcTestResult anonyme = lire("/api/missions/" + m.get("id").asString(), null);
		assertThat(anonyme).hasStatus(HttpStatus.OK);
		JsonNode vue = corpsDe(anonyme);
		assertThat(vue.get("moi").get("role").asString()).isEqualTo("visiteur");
		assertThat(vue.get("moi").get("actions")).isEmpty();
		assertThat(vue.get("travail").isNull()).isTrue();
	}

	@Test
	void publier_deux_fois_ou_avec_une_version_perimee_est_refuse() throws Exception {
		UtilisateurConnecte awa = utilisateur("awa");
		JsonNode m = mission("publique", awa);
		String chemin = "/api/missions/" + m.get("id").asString() + "/publier";

		MvcTestResult perimee = action(chemin, "{\"version\":" + (m.get("version").asInt() + 5) + "}", awa);
		assertThat(perimee).hasStatus(HttpStatus.CONFLICT);
		assertThat(corpsDe(perimee).get("code").asString()).isEqualTo("modification_concurrente");

		JsonNode publiee = corpsDe(action(chemin, "{\"version\":" + m.get("version") + "}", awa));
		MvcTestResult encore = action(chemin, "{\"version\":" + publiee.get("version") + "}", awa);
		assertThat(encore).hasStatus(HttpStatus.CONFLICT);
		assertThat(corpsDe(encore).get("code").asString()).isEqualTo("etat_invalide");
	}

	@Test
	void seul_le_client_ferme_sa_mission() throws Exception {
		UtilisateurConnecte awa = utilisateur("awa");
		JsonNode m = publiee("publique", awa);
		MvcTestResult r = action("/api/missions/" + m.get("id").asString() + "/fermer", "{\"version\":" + m.get("version") + "}", utilisateur("kofi"));
		assertThat(r).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void fermer_une_mission_publiee() throws Exception {
		UtilisateurConnecte awa = utilisateur("awa");
		JsonNode m = publiee("publique", awa);
		MvcTestResult r = action("/api/missions/" + m.get("id").asString() + "/fermer", "{\"version\":" + m.get("version") + "}", awa);
		assertThat(r).hasStatus(HttpStatus.OK);
		assertThat(corpsDe(r).get("statut").asString()).isEqualTo("fermee");
		assertThat(corpsDe(lire("/api/missions?vue=publiques", null)).get("elements").toString()).doesNotContain(m.get("id").asString());
	}

	// ---------------------------------------------------------------- validation

	@Test
	void un_budget_negatif_ou_une_devise_inconnue_est_refuse_avec_le_champ() throws Exception {
		UtilisateurConnecte awa = utilisateur("awa");
		MvcTestResult negatif = creer(corps("publique").replace("450000", "-5"), awa);
		assertThat(negatif).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(corpsDe(negatif).get("erreurs").toString()).contains("budget");

		MvcTestResult devise = creer(corps("publique").replace("XOF", "xyz"), awa);
		assertThat(devise).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(corpsDe(devise).get("erreurs").toString()).contains("budget");
	}

	@Test
	void tous_les_champs_en_erreur_sont_signales_ensemble() throws Exception {
		MvcTestResult r = creer("""
				{"titre":"a","besoin":"court","competences":[],"visibilite":"secrete"}
				""", utilisateur("awa"));
		assertThat(r).hasStatus(HttpStatus.BAD_REQUEST);
		String erreurs = corpsDe(r).get("erreurs").toString();
		assertThat(erreurs).contains("titre", "besoin", "competences", "visibilite");
	}

	@Test
	void une_demande_qui_n_est_pas_la_mienne_est_introuvable() throws Exception {
		UtilisateurConnecte awa = utilisateur("awa");
		String avecDemande = """
				{"demandeId":"%s","titre":"Boutique en ligne","besoin":"Montrer nos modèles avec photos et prix, recevoir les commandes.",
				"competences":["vue"],"visibilite":"publique"}
				""".formatted(UUID.randomUUID());
		MvcTestResult r = creer(avecDemande, awa);
		assertThat(r).hasStatus(HttpStatus.NOT_FOUND);
	}

	// ---------------------------------------------------------------- visibilité

	@Test
	void une_mission_sur_invitation_n_est_visible_que_des_invites() throws Exception {
		UtilisateurConnecte awa = utilisateur("awa");
		UtilisateurConnecte kofi = utilisateur("kofi");
		JsonNode m = publiee("invitation", awa);
		String id = m.get("id").asString();

		assertThat(lire("/api/missions/" + id, kofi)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(lire("/api/missions/" + id, null)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(corpsDe(lire("/api/missions?vue=publiques", null)).get("elements").toString()).doesNotContain(id);

		MvcTestResult invite = action("/api/missions/" + id + "/invitations", "{\"utilisateurId\":\"" + kofi.id() + "\"}", awa);
		assertThat(invite).hasStatus(HttpStatus.OK);
		assertThat(action("/api/missions/" + id + "/invitations", "{\"utilisateurId\":\"" + kofi.id() + "\"}", awa)).hasStatus(HttpStatus.OK);

		MvcTestResult vue = lire("/api/missions/" + id, kofi);
		assertThat(vue).hasStatus(HttpStatus.OK);
		assertThat(corpsDe(vue).get("moi").get("role").asString()).isEqualTo("invite");
		assertThat(corpsDe(lire("/api/missions?vue=miennes", kofi)).get("elements").toString()).contains(id);
	}

	@Test
	void un_brouillon_invitee_reste_invisible_et_on_ne_s_invite_pas_soi_meme() throws Exception {
		UtilisateurConnecte awa = utilisateur("awa");
		UtilisateurConnecte kofi = utilisateur("kofi");
		JsonNode m = mission("invitation", awa);
		String id = m.get("id").asString();
		assertThat(action("/api/missions/" + id + "/invitations", "{\"utilisateurId\":\"" + kofi.id() + "\"}", awa)).hasStatus(HttpStatus.OK);
		assertThat(lire("/api/missions/" + id, kofi)).hasStatus(HttpStatus.NOT_FOUND);

		MvcTestResult soi = action("/api/missions/" + id + "/invitations", "{\"utilisateurId\":\"" + awa.id() + "\"}", awa);
		assertThat(soi).hasStatus(HttpStatus.CONFLICT);
		assertThat(corpsDe(soi).get("code").asString()).isEqualTo("propre_mission");
	}

	@Test
	void la_liste_publique_ne_montre_que_les_missions_publiees_et_publiques() throws Exception {
		UtilisateurConnecte awa = utilisateur("awa");
		String ouverte = publiee("publique", awa).get("id").asString();
		String brouillon = mission("publique", awa).get("id").asString();
		String invitation = publiee("invitation", awa).get("id").asString();

		// La base est partagée entre les classes de test : on parcourt toutes les pages au lieu de supposer qu'il n'y en a qu'une.
		StringBuilder elements = new StringBuilder();
		String curseur = null;
		do {
			String uri = "/api/missions?limite=100" + (curseur == null ? "" : "&curseur=" + URLEncoder.encode(curseur, StandardCharsets.UTF_8));
			JsonNode page = corpsDe(lire(uri, null));
			elements.append(page.get("elements"));
			curseur = page.get("curseurSuivant").isNull() ? null : page.get("curseurSuivant").asString();
		} while (curseur != null);
		assertThat(elements.toString()).contains(ouverte).doesNotContain(brouillon).doesNotContain(invitation);

		assertThat(corpsDe(lire("/api/missions?vue=miennes", awa)).get("elements").toString())
			.contains(ouverte, brouillon, invitation);
		assertThat(lire("/api/missions?vue=miennes", null)).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void la_liste_se_parcourt_par_curseur_sans_doublon() throws Exception {
		UtilisateurConnecte awa = utilisateur("awa");
		for (int i = 0; i < 3; i++) {
			mission("publique", awa);
		}
		JsonNode premiere = corpsDe(lire("/api/missions?vue=miennes&limite=2", awa));
		assertThat(premiere.get("elements")).hasSize(2);
		String curseur = premiere.get("curseurSuivant").asString();
		JsonNode seconde = corpsDe(lire("/api/missions?vue=miennes&limite=2&curseur=" + curseur, awa));
		assertThat(seconde.get("elements")).hasSize(1);
		assertThat(seconde.get("curseurSuivant").isNull()).isTrue();
		assertThat(seconde.get("elements").get(0).get("id").asString())
			.isNotEqualTo(premiere.get("elements").get(0).get("id").asString())
			.isNotEqualTo(premiere.get("elements").get(1).get("id").asString());
	}

	// ---------------------------------------------------------------- limite

	@Test
	void plus_de_cinq_publications_par_jour_repondent_429_avec_retry_after() throws Exception {
		UtilisateurConnecte awa = utilisateur("awa");
		for (int i = 0; i < 5; i++) {
			publiee("publique", awa);
		}
		JsonNode sixieme = mission("publique", awa);
		MvcTestResult r = action("/api/missions/" + sixieme.get("id").asString() + "/publier", "{\"version\":" + sixieme.get("version") + "}", awa);
		assertThat(r).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(Long.parseLong(r.getResponse().getHeader("Retry-After"))).isBetween(1L, 86_400L);
		assertThat(corpsDe(r).get("code").asString()).isEqualTo("limite_atteinte");

		// la mission reste un brouillon : rien n'a été publié à moitié
		assertThat(corpsDe(lire("/api/missions/" + sixieme.get("id").asString(), awa)).get("statut").asString()).isEqualTo("brouillon");
	}

	@Test
	void un_autre_compte_n_est_pas_limite_par_les_publications_d_un_client() throws Exception {
		UtilisateurConnecte awa = utilisateur("awa");
		for (int i = 0; i < 5; i++) {
			publiee("publique", awa);
		}
		assertThat(publiee("publique", utilisateur("kofi")).get("statut").asString()).isEqualTo("publiee");
	}

}
