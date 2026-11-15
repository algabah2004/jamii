package dev.jamii.api.questionnaires;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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

/**
 * Les questionnaires personnalisés de bout en bout (#257) : posés à l'inscription d'un hackathon et dans une proposition
 * de mission, vérifiés par l'API, lus seulement par le créateur et l'auteur, figés une fois quelqu'un inscrit.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class QuestionnairesTest {

	private static final String QUESTIONNAIRE = """
			[{"id":"q1","intitule":"Ton lien GitHub ou ton portfolio","type":"texte_court","obligatoire":true},
			 {"id":"q2","intitule":"Ton niveau","type":"choix_unique","choix":["Débutant","Expert"],"obligatoire":true}]""";

	private static final String REPONSES = """
			[{"questionId":"q1","valeur":"https://github.com/kofi"},{"questionId":"q2","valeur":"Expert"}]""";

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
			.param("id", id).param("nom", nom).param("email", nom + "." + id + "@secret.example").param("t", t).update();
		return new UtilisateurConnecte(id, Set.of(Role.MEMBRE));
	}

	private static String date(long jours) {
		return Instant.now().plus(jours, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS).toString();
	}

	private MvcTestResult envoyer(String methode, String chemin, String corps, UtilisateurConnecte qui) {
		var requete = "PATCH".equals(methode) ? mvc.patch().uri(chemin).contentType(new MediaType("application", "merge-patch+json"))
				: mvc.post().uri(chemin).contentType(MediaType.APPLICATION_JSON);
		return requete.content(corps).with(csrf()).with(oauth2Login().oauth2User(qui)).exchange();
	}

	private MvcTestResult lire(String chemin, UtilisateurConnecte qui) {
		return mvc.get().uri(chemin).with(oauth2Login().oauth2User(qui)).exchange();
	}

	private JsonNode corps(MvcTestResult r) throws Exception {
		return json.readTree(r.getResponse().getContentAsString());
	}

	private static String erreurs(JsonNode probleme) {
		return probleme.get("erreurs").toString();
	}

	/** Un hackathon publié, inscriptions ouvertes, sur candidature, avec le questionnaire. */
	private JsonNode hackathon(UtilisateurConnecte orga) throws Exception {
		String corps = """
				{"titre":"Dakar Cloud Hack","theme":"Des services utiles aux commerçants","description":"Deux jours pour construire un service qui aide un commerçant.",
				 "lieu":"en_ligne",
				 "calendrier":{"inscriptionsOuvertesLe":"%s","inscriptionsFermeesLe":"%s","debutLe":"%s","finRendusLe":"%s","resultatsLe":"%s"},
				 "tailleEquipe":{"min":1,"max":4},"modeInscription":"candidature",
				 "codeDeConduite":"Respect de chacun, aucun harcèlement, le code de chaque équipe reste le sien.",
				 "criteres":[{"nom":"Utilité","poids":100}],"questionnaire":%s}
				""".formatted(date(-1), date(9), date(10), date(12), date(14), QUESTIONNAIRE);
		MvcTestResult cree = envoyer("POST", "/api/hackathons", corps, orga);
		assertThat(cree).hasStatus(HttpStatus.CREATED);
		JsonNode h = corps(cree);
		MvcTestResult publie = envoyer("POST", "/api/hackathons/" + h.get("id").asString() + "/publier", "{\"version\":" + h.get("version") + "}", orga);
		assertThat(publie).hasStatus(HttpStatus.OK);
		return corps(publie);
	}

	@Test
	void l_inscription_a_un_hackathon_exige_les_reponses_obligatoires_et_l_organisateur_les_lit() throws Exception {
		UtilisateurConnecte orga = utilisateur("Awa");
		UtilisateurConnecte kofi = utilisateur("Kofi");
		UtilisateurConnecte amina = utilisateur("Amina");
		JsonNode h = hackathon(orga);
		String id = h.get("id").asString();
		assertThat(h.get("questionnaire").toString()).contains("\"id\":\"q2\"", "\"choix\":[\"Débutant\",\"Expert\"]");

		// Sans réponse, ni l'inscription seule ni la création d'équipe ne passent : aucune porte de côté.
		MvcTestResult sans = envoyer("POST", "/api/hackathons/" + id + "/inscriptions", "{}", kofi);
		assertThat(sans).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(erreurs(corps(sans))).contains("reponses.q1", "reponses.q2", "obligatoire");
		MvcTestResult equipeSans = envoyer("POST", "/api/hackathons/" + id + "/equipes", "{\"nom\":\"Les Baobabs\"}", amina);
		assertThat(equipeSans).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(erreurs(corps(equipeSans))).contains("reponses.q1");

		MvcTestResult inscrit = envoyer("POST", "/api/hackathons/" + id + "/inscriptions", "{\"reponses\":" + REPONSES + "}", kofi);
		assertThat(inscrit).hasStatus(HttpStatus.CREATED);
		assertThat(corps(inscrit).get("reponses").toString()).contains("https://github.com/kofi", "Expert");
		assertThat(envoyer("POST", "/api/hackathons/" + id + "/equipes", "{\"nom\":\"Les Baobabs\",\"reponses\":" + REPONSES + "}", amina))
			.hasStatus(HttpStatus.CREATED);

		// L'organisateur lit les réponses pour trier.
		String liste = corps(lire("/api/hackathons/" + id + "/inscriptions", orga)).get("elements").toString();
		assertThat(liste).contains("https://github.com/kofi");

		// Une fois quelqu'un inscrit, les questions ne changent plus : les réponses gardent leur sens.
		JsonNode actuel = corps(lire("/api/hackathons/" + id, orga));
		MvcTestResult modifie = envoyer("PATCH", "/api/hackathons/" + id,
				"{\"version\":" + actuel.get("version") + ",\"questionnaire\":[]}", orga);
		assertThat(modifie).hasStatus(HttpStatus.CONFLICT);
		assertThat(corps(modifie).get("code").asString()).isEqualTo("questionnaire_fige");
	}

	@Test
	void les_autres_participants_ne_lisent_pas_les_reponses() throws Exception {
		UtilisateurConnecte orga = utilisateur("Awa");
		UtilisateurConnecte kofi = utilisateur("Kofi");
		UtilisateurConnecte amina = utilisateur("Amina");
		String id = hackathon(orga).get("id").asString();
		for (UtilisateurConnecte qui : new UtilisateurConnecte[] { kofi, amina }) {
			JsonNode i = corps(envoyer("POST", "/api/hackathons/" + id + "/inscriptions", "{\"reponses\":" + REPONSES + "}", qui));
			assertThat(envoyer("PATCH", "/api/hackathons/" + id + "/inscriptions/" + i.get("id").asString(), "{\"statut\":\"acceptee\"}", orga))
				.hasStatus(HttpStatus.OK);
		}
		MvcTestResult sansEquipe = lire("/api/hackathons/" + id + "/sans-equipe", amina);
		assertThat(sansEquipe).hasStatus(HttpStatus.OK);
		String texte = sansEquipe.getResponse().getContentAsString();
		assertThat(texte).contains("Kofi").doesNotContain("github.com/kofi");
	}

	@Test
	void une_proposition_repond_au_questionnaire_de_la_mission_et_le_client_lit_les_reponses() throws Exception {
		UtilisateurConnecte client = utilisateur("Awa");
		UtilisateurConnecte dev = utilisateur("Kofi");
		MvcTestResult cree = envoyer("POST", "/api/missions", """
				{"titre":"Boutique en ligne","besoin":"Montrer nos modèles avec photos et prix, recevoir les commandes.",
				 "competences":["vue"],"visibilite":"publique","questionnaire":%s}""".formatted(QUESTIONNAIRE), client);
		assertThat(cree).hasStatus(HttpStatus.CREATED);
		JsonNode m = corps(cree);
		String id = m.get("id").asString();
		assertThat(m.get("questionnaire").toString()).contains("Ton lien GitHub ou ton portfolio");
		assertThat(envoyer("POST", "/api/missions/" + id + "/publier", "{\"version\":" + m.get("version") + "}", client)).hasStatus(HttpStatus.OK);

		String offre = "\"message\":\"Je fais ce genre de boutique depuis deux ans, avec le paiement Wave.\",\"prix\":{\"centimes\":400000,\"devise\":\"XOF\"},\"delaiJours\":21";
		MvcTestResult incomplete = envoyer("POST", "/api/missions/" + id + "/propositions",
				"{" + offre + ",\"reponses\":[{\"questionId\":\"q2\",\"valeur\":\"Moyen\"}]}", dev);
		assertThat(incomplete).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(erreurs(corps(incomplete))).contains("reponses.q1", "obligatoire", "reponses.q2", "valeur_inconnue");

		assertThat(envoyer("POST", "/api/missions/" + id + "/propositions", "{" + offre + ",\"reponses\":" + REPONSES + "}", dev))
			.hasStatus(HttpStatus.CREATED);
		String propositions = corps(lire("/api/missions/" + id + "/propositions", client)).get("elements").toString();
		assertThat(propositions).contains("\"questionId\":\"q1\"", "https://github.com/kofi");
	}

}
