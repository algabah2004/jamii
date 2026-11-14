package dev.jamii.api.hackathons;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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

/** S'inscrire, former les équipes, trier les candidatures (contracts/hackathons.yaml, tag « inscriptions »), sur un vrai PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class InscriptionsTest {

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

	private static String date(long jours) {
		return Instant.now().plus(jours, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS).toString();
	}

	/** Un hackathon publié, inscriptions ouvertes depuis la veille, équipes de 2 à `tailleMax`. */
	private String publie(UtilisateurConnecte orga, String mode, int tailleMax) throws Exception {
		String corps = """
				{"titre":"Dakar Cloud Hack","theme":"Des services utiles aux commerçants","description":"Deux jours pour construire un service qui aide un commerçant.",
				 "lieu":"en_ligne",
				 "calendrier":{"inscriptionsOuvertesLe":"%s","inscriptionsFermeesLe":"%s","debutLe":"%s","finRendusLe":"%s","resultatsLe":"%s"},
				 "tailleEquipe":{"min":1,"max":%d},"modeInscription":"%s",
				 "codeDeConduite":"Respect de chacun, aucun harcèlement, le code de chaque équipe reste le sien.",
				 "criteres":[{"nom":"Utilité","poids":100}]}
				""".formatted(date(-1), date(9), date(10), date(12), date(14), tailleMax, mode);
		MvcTestResult cree = poster("/api/hackathons", corps, orga);
		assertThat(cree).hasStatus(HttpStatus.CREATED);
		JsonNode h = corpsDe(cree);
		String id = h.get("id").asString();
		assertThat(poster("/api/hackathons/" + id + "/publier", "{\"version\":" + h.get("version") + "}", orga)).hasStatus(HttpStatus.OK);
		return id;
	}

	private MvcTestResult poster(String chemin, String corps, UtilisateurConnecte qui) {
		return mvc.post().uri(chemin).contentType(MediaType.APPLICATION_JSON).content(corps)
			.with(csrf()).with(oauth2Login().oauth2User(qui)).exchange();
	}

	private MvcTestResult trier(String hackathon, String inscription, String statut, UtilisateurConnecte qui) {
		return mvc.patch().uri("/api/hackathons/" + hackathon + "/inscriptions/" + inscription)
			.contentType(new MediaType("application", "merge-patch+json")).content("{\"statut\":\"" + statut + "\"}")
			.with(csrf()).with(oauth2Login().oauth2User(qui)).exchange();
	}

	private MvcTestResult lire(String uri, UtilisateurConnecte qui) {
		return qui == null ? mvc.get().uri(uri).exchange() : mvc.get().uri(uri).with(oauth2Login().oauth2User(qui)).exchange();
	}

	private JsonNode corpsDe(MvcTestResult r) throws Exception {
		return json.readTree(r.getResponse().getContentAsString());
	}

	private JsonNode inscrire(String hackathon, UtilisateurConnecte qui) throws Exception {
		MvcTestResult r = poster("/api/hackathons/" + hackathon + "/inscriptions", "{\"competences\":[\"flutter\",\"go\"]}", qui);
		assertThat(r).hasStatus(HttpStatus.CREATED);
		return corpsDe(r);
	}

	private JsonNode equipe(String hackathon, String nom, UtilisateurConnecte chef) throws Exception {
		MvcTestResult r = poster("/api/hackathons/" + hackathon + "/equipes", "{\"nom\":\"" + nom + "\"}", chef);
		assertThat(r).hasStatus(HttpStatus.CREATED);
		return corpsDe(r);
	}

	private MvcTestResult inviter(String hackathon, String equipe, UtilisateurConnecte invite, UtilisateurConnecte chef) {
		return poster("/api/hackathons/" + hackathon + "/equipes/" + equipe + "/membres", "{\"utilisateurId\":\"" + invite.id() + "\"}", chef);
	}

	private static String code(JsonNode erreur) {
		return erreur.get("code").asString();
	}

	// ---------------------------------------------------------------- s'inscrire

	@Test
	void en_mode_ouvert_l_inscription_est_acceptee_et_le_participant_le_voit() throws Exception {
		String h = publie(utilisateur("awa"), "ouvert", 4);
		UtilisateurConnecte kofi = utilisateur("kofi");
		JsonNode i = inscrire(h, kofi);
		assertThat(i.get("statut").asString()).isEqualTo("acceptee");
		assertThat(i.at("/personne/nom").asString()).isEqualTo("kofi");
		assertThat(i.get("competences").toString()).isEqualTo("[\"flutter\",\"go\"]");
		assertThat(i.toString()).doesNotContain("@secret.example");

		JsonNode vue = corpsDe(lire("/api/hackathons/" + h, kofi));
		assertThat(vue.at("/moi/role").asString()).isEqualTo("participant");
		assertThat(vue.at("/moi/actions").toString()).contains("creer_equipe");
		assertThat(vue.get("nombreInscrits").asInt()).isEqualTo(1);
	}

	@Test
	void on_ne_s_inscrit_qu_une_fois_meme_avec_deux_requetes_simultanees() throws Exception {
		String h = publie(utilisateur("awa"), "ouvert", 4);
		UtilisateurConnecte kofi = utilisateur("kofi");
		List<Callable<Integer>> envois = new ArrayList<>();
		for (int i = 0; i < 2; i++) {
			envois.add(() -> poster("/api/hackathons/" + h + "/inscriptions", "{}", kofi).getResponse().getStatus());
		}
		List<Integer> statuts = new ArrayList<>();
		try (var executeur = Executors.newFixedThreadPool(2)) {
			for (Future<Integer> f : executeur.invokeAll(envois)) {
				statuts.add(f.get());
			}
		}
		assertThat(statuts).containsExactlyInAnyOrder(201, 409);
		assertThat(base.sql("SELECT count(*) FROM inscription WHERE utilisateur_id = :u").param("u", kofi.id()).query(Integer.class).single())
			.isEqualTo(1);
	}

	@Test
	void l_organisateur_ne_s_inscrit_pas_a_son_hackathon() throws Exception {
		UtilisateurConnecte awa = utilisateur("awa");
		String h = publie(awa, "ouvert", 4);
		assertThat(poster("/api/hackathons/" + h + "/inscriptions", "{}", awa)).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void une_competence_hors_format_est_refusee() throws Exception {
		String h = publie(utilisateur("awa"), "ouvert", 4);
		MvcTestResult r = poster("/api/hackathons/" + h + "/inscriptions", "{\"competences\":[\"Flutter avec espace\"]}", utilisateur("kofi"));
		assertThat(r).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(corpsDe(r).toString()).contains("competences");
	}

	@Test
	void a_la_fermeture_des_inscriptions_les_equipes_sont_verrouillees() throws Exception {
		String h = publie(utilisateur("awa"), "ouvert", 4);
		UtilisateurConnecte kofi = utilisateur("kofi");
		String e = equipe(h, "Les Griots", kofi).get("id").asString();
		UtilisateurConnecte fatou = utilisateur("fatou");
		inscrire(h, fatou);
		base.sql("UPDATE hackathon SET inscriptions_fermees_le = :t WHERE id = :id")
			.param("t", Timestamp.from(Instant.now().minusSeconds(60))).param("id", UUID.fromString(h)).update();

		MvcTestResult inscription = poster("/api/hackathons/" + h + "/inscriptions", "{}", utilisateur("moussa"));
		assertThat(inscription).hasStatus(HttpStatus.CONFLICT);
		assertThat(code(corpsDe(inscription))).isEqualTo("equipes_verrouillees");
		MvcTestResult invitation = inviter(h, e, fatou, kofi);
		assertThat(invitation).hasStatus(HttpStatus.CONFLICT);
		assertThat(code(corpsDe(invitation))).isEqualTo("equipes_verrouillees");
	}

	// ---------------------------------------------------------------- les équipes

	@Test
	void creer_une_equipe_inscrit_son_chef_et_la_liste_est_publique() throws Exception {
		String h = publie(utilisateur("awa"), "ouvert", 4);
		UtilisateurConnecte kofi = utilisateur("kofi");
		JsonNode e = equipe(h, "Les Griots", kofi);
		assertThat(e.at("/chef/nom").asString()).isEqualTo("kofi");
		assertThat(e.get("membres")).hasSize(1);
		assertThat(e.get("complete").asBoolean()).isFalse();

		JsonNode vue = corpsDe(lire("/api/hackathons/" + h, kofi));
		assertThat(vue.at("/moi/equipeId").asString()).isEqualTo(e.get("id").asString());
		assertThat(vue.at("/moi/actions").toString()).contains("inviter");

		MvcTestResult anonyme = lire("/api/hackathons/" + h + "/equipes", null);
		assertThat(anonyme).hasStatus(HttpStatus.OK);
		assertThat(corpsDe(anonyme).at("/elements/0/nom").asString()).isEqualTo("Les Griots");

		MvcTestResult deuxieme = poster("/api/hackathons/" + h + "/equipes", "{\"nom\":\"Autre\"}", kofi);
		assertThat(deuxieme).hasStatus(HttpStatus.CONFLICT);
		assertThat(code(corpsDe(deuxieme))).isEqualTo("deja_inscrit");
	}

	@Test
	void le_chef_invite_jusqu_a_la_taille_maximale() throws Exception {
		String h = publie(utilisateur("awa"), "ouvert", 2);
		UtilisateurConnecte kofi = utilisateur("kofi");
		UtilisateurConnecte fatou = utilisateur("fatou");
		UtilisateurConnecte moussa = utilisateur("moussa");
		inscrire(h, fatou);
		inscrire(h, moussa);
		String e = equipe(h, "Les Griots", kofi).get("id").asString();

		assertThat(inviter(h, e, fatou, moussa)).hasStatus(HttpStatus.FORBIDDEN);
		MvcTestResult ok = inviter(h, e, fatou, kofi);
		assertThat(ok).hasStatus(HttpStatus.OK);
		JsonNode equipe = corpsDe(ok);
		assertThat(equipe.at("/membres/0/nom").asString()).isEqualTo("kofi");
		assertThat(equipe.at("/membres/1/nom").asString()).isEqualTo("fatou");
		assertThat(equipe.get("complete").asBoolean()).isTrue();

		MvcTestResult pleine = inviter(h, e, moussa, kofi);
		assertThat(pleine).hasStatus(HttpStatus.CONFLICT);
		assertThat(code(corpsDe(pleine))).isEqualTo("equipe_complete");
	}

	@Test
	void je_cherche_une_equipe_montre_les_autres_participants_seuls_aux_participants_seulement() throws Exception {
		String h = publie(utilisateur("awa"), "ouvert", 4);
		UtilisateurConnecte kofi = utilisateur("kofi");
		UtilisateurConnecte fatou = utilisateur("fatou");
		inscrire(h, kofi);
		inscrire(h, fatou);
		poster("/api/hackathons/" + h + "/inscriptions", "{\"chercheEquipe\":false}", utilisateur("moussa"));

		JsonNode page = corpsDe(lire("/api/hackathons/" + h + "/sans-equipe", kofi));
		assertThat(page.get("elements")).hasSize(1);
		assertThat(page.at("/elements/0/personne/nom").asString()).isEqualTo("fatou");
		assertThat(lire("/api/hackathons/" + h + "/sans-equipe", utilisateur("curieux"))).hasStatus(HttpStatus.FORBIDDEN);
	}

	// ---------------------------------------------------------------- trier les candidatures

	@Test
	void en_mode_candidature_l_organisateur_trie_et_un_refus_defait_l_equipe() throws Exception {
		UtilisateurConnecte awa = utilisateur("awa");
		String h = publie(awa, "candidature", 4);
		UtilisateurConnecte kofi = utilisateur("kofi");
		UtilisateurConnecte fatou = utilisateur("fatou");
		JsonNode iKofi = inscrire(h, kofi);
		JsonNode iFatou = inscrire(h, fatou);
		assertThat(iKofi.get("statut").asString()).isEqualTo("en_attente");
		assertThat(corpsDe(lire("/api/hackathons/" + h, awa)).at("/moi/actions").toString()).contains("trier_inscriptions");

		assertThat(trier(h, iFatou.get("id").asString(), "acceptee", kofi)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(trier(h, iFatou.get("id").asString(), "acceptee", awa)).hasStatus(HttpStatus.OK);
		String e = equipe(h, "Les Griots", kofi).get("id").asString();
		assertThat(inviter(h, e, fatou, kofi)).hasStatus(HttpStatus.OK);

		MvcTestResult refus = trier(h, iKofi.get("id").asString(), "refusee", awa);
		assertThat(refus).hasStatus(HttpStatus.OK);
		assertThat(corpsDe(refus).get("equipeId").isNull()).isTrue();
		assertThat(base.sql("SELECT count(*) FROM equipe WHERE id = :e").param("e", UUID.fromString(e)).query(Integer.class).single()).isZero();
		assertThat(base.sql("SELECT count(*) FROM inscription WHERE id = :i AND equipe_id IS NULL")
			.param("i", UUID.fromString(iFatou.get("id").asString())).query(Integer.class).single()).isEqualTo(1);

		JsonNode liste = corpsDe(lire("/api/hackathons/" + h + "/inscriptions?statut=refusee", awa));
		assertThat(liste.get("elements")).hasSize(1);
		assertThat(lire("/api/hackathons/" + h + "/inscriptions", kofi)).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void en_mode_ouvert_il_n_y_a_rien_a_trier() throws Exception {
		UtilisateurConnecte awa = utilisateur("awa");
		String h = publie(awa, "ouvert", 4);
		JsonNode i = inscrire(h, utilisateur("kofi"));
		MvcTestResult r = trier(h, i.get("id").asString(), "refusee", awa);
		assertThat(r).hasStatus(HttpStatus.CONFLICT);
		assertThat(code(corpsDe(r))).isEqualTo("etat_invalide");
	}

}
