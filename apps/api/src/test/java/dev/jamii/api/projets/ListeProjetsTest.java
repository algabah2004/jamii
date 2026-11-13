package dev.jamii.api.projets;

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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** GET /api/projets (contracts/projets.yaml), sur un vrai PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ListeProjetsTest {

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

	private MvcTestResult ecrire(String uri, UtilisateurConnecte qui, String corps) {
		var requete = mvc.post().uri(uri);
		if (corps != null) {
			requete = requete.contentType(MediaType.APPLICATION_JSON).content(corps);
		}
		return requete.with(csrf()).with(oauth2Login().oauth2User(qui)).exchange();
	}

	private MvcTestResult lire(String uri, UtilisateurConnecte qui) {
		return mvc.get().uri(uri).with(oauth2Login().oauth2User(qui)).exchange();
	}

	private String projet(UtilisateurConnecte porteur, String titre) {
		MvcTestResult r = ecrire("/api/projets", porteur,
				"{\"titre\":\"" + titre + "\",\"description\":\"Une description assez longue pour le projet.\",\"origine\":\"idee\"}");
		assertThat(r).hasStatus(HttpStatus.CREATED);
		return r.getResponse().getHeader("Location").substring("/api/projets/".length());
	}

	private String role(String projet, UtilisateurConnecte porteur, String titre, String... competences) {
		String liste = String.join("\",\"", competences);
		MvcTestResult r = ecrire("/api/projets/" + projet + "/roles", porteur, "{\"titre\":\"" + titre + "\",\"competences\":[\"" + liste + "\"]}");
		assertThat(r).hasStatus(HttpStatus.CREATED);
		return corps(r).get("id").asString();
	}

	private JsonNode corps(MvcTestResult r) {
		try {
			return json.readTree(r.getResponse().getContentAsString());
		}
		catch (java.io.UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
	}

	private List<String> ids(MvcTestResult r) {
		List<String> ids = new ArrayList<>();
		corps(r).get("elements").forEach(e -> ids.add(e.get("id").asString()));
		return ids;
	}

	// ---------------------------------------------------------------- lecture

	@Test
	void la_liste_montre_les_plus_recents_d_abord_avec_le_resume_de_chaque_projet() {
		UtilisateurConnecte awa = utilisateur("Awa");
		String premier = projet(awa, "Premier projet de la liste");
		String second = projet(awa, "Second projet de la liste");
		role(second, awa, "Développeur mobile", "flutter", "dart");

		MvcTestResult reponse = lire("/api/projets?membre=moi", awa);

		assertThat(reponse).hasStatusOk();
		assertThat(ids(reponse)).containsExactly(second, premier);
		JsonNode projet = corps(reponse).get("elements").get(0);
		assertThat(projet.get("titre").asString()).isEqualTo("Second projet de la liste");
		assertThat(projet.get("origine").asString()).isEqualTo("idee");
		assertThat(projet.get("statut").asString()).isEqualTo("recrutement");
		assertThat(projet.get("porteur").get("nom").asString()).isEqualTo("Awa");
		assertThat(projet.get("nombreMembres").asInt()).isEqualTo(1);
		assertThat(projet.get("competencesRecherchees")).extracting(JsonNode::asString).containsExactly("dart", "flutter");
		assertThat(projet.get("creeLe").asString()).isNotBlank();
		assertThat(corps(reponse).get("elements").get(1).get("competencesRecherchees")).isEmpty();
	}

	@Test
	void tout_utilisateur_connecte_voit_les_projets_des_autres_sans_jamais_voir_d_email() throws Exception {
		UtilisateurConnecte awa = utilisateur("Awa");
		String projet = projet(awa, "Un projet visible de tous");

		MvcTestResult reponse = lire("/api/projets?limite=100", utilisateur("Curieux"));

		assertThat(ids(reponse)).contains(projet);
		assertThat(reponse.getResponse().getContentAsString()).doesNotContain("secret.example");
	}

	@Test
	void sans_connexion_401() {
		assertThat(mvc.get().uri("/api/projets")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	// ---------------------------------------------------------------- filtres

	@Test
	void le_filtre_par_competence_ne_garde_que_les_projets_qui_recrutent_cette_competence() {
		UtilisateurConnecte awa = utilisateur("Awa");
		UtilisateurConnecte kofi = utilisateur("Kofi");
		String flutter = projet(awa, "Un projet qui cherche du Flutter");
		role(flutter, awa, "Développeur mobile", "flutter");
		String vue = projet(awa, "Un projet qui cherche du Vue");
		role(vue, awa, "Développeur web", "vue");
		String ferme = projet(awa, "Un projet dont le rôle est fermé");
		String roleFerme = role(ferme, awa, "Développeur mobile", "flutter");
		assertThat(mvc.patch().uri("/api/projets/" + ferme + "/roles/" + roleFerme)
			.contentType(new MediaType("application", "merge-patch+json")).content("{\"statut\":\"ferme\"}").with(csrf())
			.with(oauth2Login().oauth2User(awa)).exchange()).hasStatusOk();
		String pourvu = projet(awa, "Un projet dont le rôle est pourvu");
		String rolePourvu = role(pourvu, awa, "Développeur mobile", "flutter");
		String candidature = corps(ecrire("/api/projets/" + pourvu + "/roles/" + rolePourvu + "/candidatures", kofi,
				"{\"message\":\"Je fais du Flutter depuis trois ans.\"}")).get("id").asString();
		assertThat(ecrire("/api/projets/" + pourvu + "/candidatures/" + candidature + "/accepter", awa, null)).hasStatusOk();

		List<String> trouves = ids(lire("/api/projets?competence=Flutter&limite=100", kofi));

		assertThat(trouves).as("seul un rôle ouvert compte, et la casse n'importe pas").contains(flutter)
			.doesNotContain(vue, ferme, pourvu);
	}

	@Test
	void le_filtre_par_statut_et_par_appartenance() {
		UtilisateurConnecte awa = utilisateur("Awa");
		UtilisateurConnecte kofi = utilisateur("Kofi");
		String enCours = projet(awa, "Un projet déjà en cours");
		base.sql("UPDATE projet SET statut = 'en_cours' WHERE id = :p").param("p", UUID.fromString(enCours)).update();
		String enRecrutement = projet(awa, "Un projet qui recrute");
		String autre = projet(kofi, "Le projet de Kofi seulement");

		assertThat(ids(lire("/api/projets?statut=en_cours&membre=moi", awa))).containsExactly(enCours);
		assertThat(ids(lire("/api/projets?statut=recrutement&membre=moi", awa))).containsExactly(enRecrutement);
		assertThat(ids(lire("/api/projets?membre=moi", kofi))).containsExactly(autre);
		assertThat(ids(lire("/api/projets?membre=moi", utilisateur("Seul")))).isEmpty();
	}

	@Test
	void un_membre_parti_ne_voit_plus_le_projet_dans_les_siens() {
		UtilisateurConnecte awa = utilisateur("Awa");
		UtilisateurConnecte kofi = utilisateur("Kofi");
		String projet = projet(awa, "Un projet que Kofi a quitté");
		base.sql("INSERT INTO membre_equipe (projet_id, utilisateur_id, rejoint_le, parti_le) VALUES (:p, :u, now(), now())")
			.param("p", UUID.fromString(projet)).param("u", kofi.id()).update();

		assertThat(ids(lire("/api/projets?membre=moi", kofi))).isEmpty();
		assertThat(corps(lire("/api/projets?membre=moi", awa)).get("elements").get(0).get("nombreMembres").asInt())
			.as("le membre parti n'est pas compté").isEqualTo(1);
	}

	// ---------------------------------------------------------------- pagination

	@Test
	void la_pagination_par_curseur_ne_perd_ni_ne_repete_aucun_projet() {
		UtilisateurConnecte awa = utilisateur("Awa");
		List<String> crees = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			crees.add(projet(awa, "Projet numéro " + i + " de la pagination"));
		}

		MvcTestResult page1 = lire("/api/projets?membre=moi&limite=2", awa);
		String curseur1 = corps(page1).get("curseurSuivant").asString();
		MvcTestResult page2 = lire("/api/projets?membre=moi&limite=2&curseur=" + curseur1, awa);
		String curseur2 = corps(page2).get("curseurSuivant").asString();
		MvcTestResult page3 = lire("/api/projets?membre=moi&limite=2&curseur=" + curseur2, awa);

		assertThat(ids(page1)).containsExactly(crees.get(4), crees.get(3));
		assertThat(ids(page2)).containsExactly(crees.get(2), crees.get(1));
		assertThat(ids(page3)).containsExactly(crees.get(0));
		assertThat(corps(page3).get("curseurSuivant").isNull()).isTrue();
	}

	// ---------------------------------------------------------------- paramètres invalides

	@Test
	void parametres_invalides_repondent_400_au_format_du_contrat() {
		UtilisateurConnecte awa = utilisateur("Awa");

		for (String uri : List.of("/api/projets?statut=secret", "/api/projets?membre=tous", "/api/projets?limite=0",
				"/api/projets?limite=101", "/api/projets?curseur=nimporte", "/api/projets?competence=avec%20espace",
				"/api/projets?competence=")) {
			MvcTestResult reponse = lire(uri, awa);
			assertThat(reponse).as(uri).hasStatus(HttpStatus.BAD_REQUEST).hasContentType("application/problem+json");
			assertThat(reponse).bodyJson().extractingPath("$.code").isEqualTo("validation");
		}
	}

}
