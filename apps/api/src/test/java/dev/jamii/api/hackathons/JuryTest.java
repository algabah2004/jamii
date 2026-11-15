package dev.jamii.api.hackathons;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;

import java.math.BigDecimal;
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

/** Le jury, les notes, le score pondéré et l'export (contracts/hackathons.yaml, tag « jury »), sur un vrai PostgreSQL. */
@SpringBootTest(properties = "jamii.verification-lien.fournisseur=simulee")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class JuryTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient base;

	@Autowired
	JsonMapper json;

	@Autowired
	Jury jury;

	/**
	 * Un hackathon aux critères pesant 50, 30 et 20, avec deux équipes qui ont rendu : celle de Kofi (Kofi, Fatou) et
	 * celle de Moussa. Awa organise ; Jo et Ami sont jurés, et Kofi aussi.
	 */
	private record Scene(String hackathon, String[] criteres, String renduKofi, String renduMoussa, UtilisateurConnecte awa,
			UtilisateurConnecte kofi, UtilisateurConnecte jo, UtilisateurConnecte ami) {
	}

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

	private Scene scene() throws Exception {
		UtilisateurConnecte awa = utilisateur("awa");
		UtilisateurConnecte kofi = utilisateur("kofi");
		UtilisateurConnecte fatou = utilisateur("fatou");
		UtilisateurConnecte moussa = utilisateur("moussa");
		UtilisateurConnecte jo = utilisateur("jo");
		UtilisateurConnecte ami = utilisateur("ami");
		JsonNode h = corpsDe(poster("/api/hackathons", """
				{"titre":"Dakar Cloud Hack","theme":"Des services utiles aux commerçants","description":"Deux jours pour construire un service qui aide un commerçant.",
				 "lieu":"en_ligne",
				 "calendrier":{"inscriptionsOuvertesLe":"%s","inscriptionsFermeesLe":"%s","debutLe":"%s","finRendusLe":"%s","resultatsLe":"%s"},
				 "tailleEquipe":{"min":1,"max":4},"modeInscription":"ouvert",
				 "codeDeConduite":"Respect de chacun, aucun harcèlement, le code de chaque équipe reste le sien.",
				 "criteres":[{"nom":"Utilité","poids":50},{"nom":"Qualité, technique","poids":30},{"nom":"=Présentation","poids":20}]}
				""".formatted(date(-1), date(9), date(10), date(12), date(14)), awa));
		String id = h.get("id").asString();
		String[] criteres = { h.at("/criteres/0/id").asString(), h.at("/criteres/1/id").asString(), h.at("/criteres/2/id").asString() };
		poster("/api/hackathons/" + id + "/publier", "{\"version\":" + h.get("version") + "}", awa);
		poster("/api/hackathons/" + id + "/inscriptions", "{}", fatou);
		String equipeKofi = corpsDe(poster("/api/hackathons/" + id + "/equipes", "{\"nom\":\"Les Griots\"}", kofi)).get("id").asString();
		poster("/api/hackathons/" + id + "/equipes/" + equipeKofi + "/membres", "{\"utilisateurId\":\"" + fatou.id() + "\"}", kofi);
		String equipeMoussa = corpsDe(poster("/api/hackathons/" + id + "/equipes", "{\"nom\":\"Teranga\"}", moussa)).get("id").asString();
		for (UtilisateurConnecte j : new UtilisateurConnecte[] { jo, ami, kofi }) {
			assertThat(poster("/api/hackathons/" + id + "/jures", "{\"utilisateurId\":\"" + j.id() + "\"}", awa)).hasStatus(HttpStatus.CREATED);
		}
		// Le hackathon a commencé : les deux équipes rendent.
		calendrier(id, -1, 1);
		String renduKofi = rendre(id, equipeKofi, kofi);
		String renduMoussa = rendre(id, equipeMoussa, moussa);
		return new Scene(id, criteres, renduKofi, renduMoussa, awa, kofi, jo, ami);
	}

	/** Les rendus sont clos : place à la notation. */
	private void evaluation(Scene s) {
		calendrier(s.hackathon(), -3, -1);
	}

	private void calendrier(String id, long debut, long fin) {
		Instant maintenant = Instant.now();
		base.sql("""
				UPDATE hackathon SET inscriptions_ouvertes_le = :o, inscriptions_fermees_le = :f, debut_le = :d, fin_rendus_le = :fr,
				  resultats_le = :r WHERE id = :id
				""")
			.param("o", Timestamp.from(maintenant.plus(debut - 5, ChronoUnit.DAYS))).param("f", Timestamp.from(maintenant.plus(debut - 1, ChronoUnit.DAYS)))
			.param("d", Timestamp.from(maintenant.plus(debut, ChronoUnit.DAYS))).param("fr", Timestamp.from(maintenant.plus(fin, ChronoUnit.DAYS)))
			.param("r", Timestamp.from(maintenant.plus(fin + 2, ChronoUnit.DAYS))).param("id", UUID.fromString(id)).update();
	}

	private String rendre(String hackathon, String equipe, UtilisateurConnecte qui) throws Exception {
		MvcTestResult r = mvc.put().uri("/api/hackathons/" + hackathon + "/equipes/" + equipe + "/rendu").contentType(MediaType.APPLICATION_JSON)
			.content("{\"titre\":\"Le carnet\",\"description\":\"Un carnet de ventes hors ligne pour les boutiques.\",\"lienProduit\":\"https://carnet.example.org\"}")
			.with(csrf()).with(oauth2Login().oauth2User(qui)).exchange();
		assertThat(r).hasStatus(HttpStatus.CREATED);
		return corpsDe(r).get("id").asString();
	}

	private MvcTestResult noter(Scene s, String rendu, UtilisateurConnecte qui, int a, int b, int c, String commentaire) {
		String corps = """
				{"notes":[{"critereId":"%s","valeur":%d},{"critereId":"%s","valeur":%d},{"critereId":"%s","valeur":%d}]%s}
				""".formatted(s.criteres()[0], a, s.criteres()[1], b, s.criteres()[2], c,
				commentaire == null ? "" : ",\"commentaire\":\"" + commentaire + "\"");
		return mvc.put().uri("/api/hackathons/" + s.hackathon() + "/rendus/" + rendu + "/notes").contentType(MediaType.APPLICATION_JSON)
			.content(corps).with(csrf()).with(oauth2Login().oauth2User(qui)).exchange();
	}

	private MvcTestResult poster(String chemin, String corps, UtilisateurConnecte qui) {
		return mvc.post().uri(chemin).contentType(MediaType.APPLICATION_JSON).content(corps)
			.with(csrf()).with(oauth2Login().oauth2User(qui)).exchange();
	}

	private MvcTestResult lire(String uri, UtilisateurConnecte qui) {
		return mvc.get().uri(uri).with(oauth2Login().oauth2User(qui)).exchange();
	}

	private JsonNode corpsDe(MvcTestResult r) throws Exception {
		return json.readTree(r.getResponse().getContentAsString());
	}

	// ---------------------------------------------------------------- noter

	@Test
	void un_jure_note_chaque_critere_et_une_valeur_hors_bornes_est_refusee() throws Exception {
		Scene s = scene();
		evaluation(s);
		assertThat(corpsDe(lire("/api/hackathons/" + s.hackathon(), s.jo())).at("/moi/actions").toString()).isEqualTo("[\"noter\"]");

		MvcTestResult ok = noter(s, s.renduMoussa(), s.jo(), 5, 3, 1, "Très utile");
		assertThat(ok).hasStatus(HttpStatus.OK);
		assertThat(corpsDe(ok).get("scorePondere").decimalValue()).isEqualByComparingTo("3.60");
		assertThat(noter(s, s.renduMoussa(), s.jo(), 0, 3, 1, null)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(noter(s, s.renduMoussa(), s.jo(), 5, 6, 1, null)).hasStatus(HttpStatus.BAD_REQUEST);

		MvcTestResult manquant = mvc.put().uri("/api/hackathons/" + s.hackathon() + "/rendus/" + s.renduMoussa() + "/notes")
			.contentType(MediaType.APPLICATION_JSON).content("{\"notes\":[{\"critereId\":\"" + s.criteres()[0] + "\",\"valeur\":4}]}")
			.with(csrf()).with(oauth2Login().oauth2User(s.jo())).exchange();
		assertThat(manquant).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(corpsDe(manquant).toString()).contains("critere_manquant");
	}

	@Test
	void un_jure_retrouve_ses_notes_et_lui_seul() throws Exception {
		Scene s = scene();
		evaluation(s);
		assertThat(corpsDe(lire("/api/hackathons/" + s.hackathon() + "/mes-notes", s.jo())).size()).isZero();
		assertThat(noter(s, s.renduMoussa(), s.jo(), 5, 3, 1, "Très utile")).hasStatus(HttpStatus.OK);
		assertThat(noter(s, s.renduMoussa(), s.ami(), 1, 1, 1, null)).hasStatus(HttpStatus.OK);

		JsonNode miennes = corpsDe(lire("/api/hackathons/" + s.hackathon() + "/mes-notes", s.jo()));
		assertThat(miennes.size()).isEqualTo(1);
		assertThat(miennes.at("/0/renduId").asString()).isEqualTo(s.renduMoussa());
		assertThat(miennes.at("/0/notes").size()).isEqualTo(3);
		assertThat(miennes.at("/0/commentaire").asString()).isEqualTo("Très utile");
		assertThat(miennes.at("/0/scorePondere").decimalValue()).isEqualByComparingTo("3.60");
		assertThat(lire("/api/hackathons/" + s.hackathon() + "/mes-notes", s.awa())).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void un_jure_ne_note_pas_son_equipe_et_ne_voit_pas_son_rendu() throws Exception {
		Scene s = scene();
		evaluation(s);
		MvcTestResult r = noter(s, s.renduKofi(), s.kofi(), 5, 5, 5, null);
		assertThat(r).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(corpsDe(r).get("code").asString()).isEqualTo("conflit_interets");

		JsonNode page = corpsDe(lire("/api/hackathons/" + s.hackathon() + "/rendus", s.kofi()));
		assertThat(page.get("elements")).hasSize(1);
		assertThat(page.at("/elements/0/id").asString()).isEqualTo(s.renduMoussa());
		assertThat(corpsDe(lire("/api/hackathons/" + s.hackathon() + "/rendus", s.jo())).get("elements")).hasSize(2);
		assertThat(lire("/api/hackathons/" + s.hackathon() + "/rendus", utilisateur("curieux"))).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void on_ne_note_pas_avant_la_fin_des_rendus_ni_sans_etre_jure() throws Exception {
		Scene s = scene();
		MvcTestResult tot = noter(s, s.renduMoussa(), s.jo(), 4, 4, 4, null);
		assertThat(tot).hasStatus(HttpStatus.CONFLICT);
		assertThat(corpsDe(tot).get("code").asString()).isEqualTo("etat_invalide");
		evaluation(s);
		assertThat(noter(s, s.renduMoussa(), s.awa(), 4, 4, 4, null)).hasStatus(HttpStatus.FORBIDDEN);
	}

	// ---------------------------------------------------------------- score, suivi et export

	@Test
	void le_score_pondere_est_la_moyenne_des_jures() throws Exception {
		Scene s = scene();
		evaluation(s);
		noter(s, s.renduMoussa(), s.jo(), 5, 3, 1, null);
		noter(s, s.renduMoussa(), s.ami(), 4, 4, 4, null);
		// Jo : (5×50 + 3×30 + 1×20) / 100 = 3,60 ; Ami : 4,00 ; le rendu : 3,80.
		assertThat(jury.scorePondere(UUID.fromString(s.renduMoussa()))).isEqualByComparingTo(new BigDecimal("3.80"));

		// Noter à nouveau remplace les notes du juré.
		noter(s, s.renduMoussa(), s.ami(), 2, 2, 2, null);
		assertThat(jury.scorePondere(UUID.fromString(s.renduMoussa()))).isEqualByComparingTo(new BigDecimal("2.80"));
	}

	@Test
	void l_organisateur_suit_la_notation_et_le_classement() throws Exception {
		Scene s = scene();
		evaluation(s);
		noter(s, s.renduMoussa(), s.jo(), 5, 5, 5, null);
		noter(s, s.renduKofi(), s.jo(), 2, 2, 2, null);
		noter(s, s.renduMoussa(), s.kofi(), 3, 3, 3, null);

		JsonNode notation = corpsDe(lire("/api/hackathons/" + s.hackathon() + "/notation", s.awa()));
		JsonNode jo = null;
		JsonNode kofi = null;
		for (JsonNode j : notation.get("jures")) {
			if (j.at("/personne/nom").asString().equals("jo")) {
				jo = j;
			}
			if (j.at("/personne/nom").asString().equals("kofi")) {
				kofi = j;
			}
		}
		assertThat(jo.get("rendusNotes").asInt()).isEqualTo(2);
		assertThat(jo.get("rendusATraiter").asInt()).isEqualTo(2);
		assertThat(kofi.get("rendusNotes").asInt()).isEqualTo(1);
		assertThat(kofi.get("rendusATraiter").asInt()).isEqualTo(1);

		assertThat(notation.at("/classement/0/renduId").asString()).isEqualTo(s.renduMoussa());
		assertThat(notation.at("/classement/0/rang").asInt()).isEqualTo(1);
		assertThat(notation.at("/classement/0/scorePondere").decimalValue()).isEqualByComparingTo("4.00");
		assertThat(notation.at("/classement/0/nombreDeJures").asInt()).isEqualTo(2);
		assertThat(notation.at("/classement/1/rang").asInt()).isEqualTo(2);
		assertThat(lire("/api/hackathons/" + s.hackathon() + "/notation", s.jo())).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void l_export_csv_echappe_les_cellules_et_bloque_les_formules() throws Exception {
		Scene s = scene();
		evaluation(s);
		noter(s, s.renduMoussa(), s.jo(), 5, 3, 1, "Bien, \\\"vraiment\\\"");

		MvcTestResult r = lire("/api/hackathons/" + s.hackathon() + "/notes.csv", s.awa());
		assertThat(r).hasStatus(HttpStatus.OK);
		assertThat(r.getResponse().getContentType()).startsWith("text/csv");
		String[] lignes = r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8).split("\r\n");
		assertThat(lignes[0]).isEqualTo("equipe,rendu,jure,critere,valeur,commentaire");
		assertThat(lignes).hasSize(4);
		assertThat(lignes[1]).isEqualTo("Teranga,Le carnet,jo,Utilité,5,\"Bien, \"\"vraiment\"\"\"");
		assertThat(lignes[2]).startsWith("Teranga,Le carnet,jo,\"Qualité, technique\",3,");
		assertThat(lignes[3]).startsWith("Teranga,Le carnet,jo,\"'=Présentation\",1,");

		assertThat(lire("/api/hackathons/" + s.hackathon() + "/notes.csv", s.jo())).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void la_cellule_csv_neutralise_les_formules() {
		assertThat(JuryController.cellule("=SOMME(A1)")).isEqualTo("\"'=SOMME(A1)\"");
		assertThat(JuryController.cellule("+33")).isEqualTo("\"'+33\"");
		assertThat(JuryController.cellule("-1")).isEqualTo("\"'-1\"");
		assertThat(JuryController.cellule("@lien")).isEqualTo("\"'@lien\"");
		assertThat(JuryController.cellule("simple")).isEqualTo("simple");
		assertThat(JuryController.cellule(null)).isEmpty();
	}

}
