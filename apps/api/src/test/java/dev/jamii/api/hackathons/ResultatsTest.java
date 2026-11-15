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

/** Confirmer les gagnants, publier les résultats, export et suppression du compte (contracts/hackathons.yaml), sur un vrai PostgreSQL. */
@SpringBootTest(properties = "jamii.verification-lien.fournisseur=simulee")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ResultatsTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient base;

	@Autowired
	JsonMapper json;

	@Autowired
	Jury jury;

	/**
	 * Un hackathon en évaluation, un seul critère et deux prix. Jo a noté : l'équipe de Moussa (5) devant celle de Kofi
	 * (2). Le classement propose donc le premier prix à Moussa et le second à Kofi.
	 */
	private record Scene(String hackathon, String premierPrix, String secondPrix, String equipeKofi, String equipeMoussa, String renduMoussa,
			UtilisateurConnecte awa, UtilisateurConnecte kofi, UtilisateurConnecte fatou, UtilisateurConnecte jo) {
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
		JsonNode h = corpsDe(poster("/api/hackathons", """
				{"titre":"Dakar Cloud Hack","theme":"Des services utiles aux commerçants","description":"Deux jours pour construire un service qui aide un commerçant.",
				 "lieu":"en_ligne",
				 "calendrier":{"inscriptionsOuvertesLe":"%s","inscriptionsFermeesLe":"%s","debutLe":"%s","finRendusLe":"%s","resultatsLe":"%s"},
				 "tailleEquipe":{"min":1,"max":4},"modeInscription":"ouvert",
				 "codeDeConduite":"Respect de chacun, aucun harcèlement, le code de chaque équipe reste le sien.",
				 "criteres":[{"nom":"Utilité","poids":100}],
				 "prix":[{"nom":"Prix du jury"},{"nom":"Meilleur projet sur DataCloud","sponsor":"Systalink"}]}
				""".formatted(date(-1), date(9), date(10), date(12), date(14)), awa));
		String id = h.get("id").asString();
		String critere = h.at("/criteres/0/id").asString();
		poster("/api/hackathons/" + id + "/publier", "{\"version\":" + h.get("version") + "}", awa);
		poster("/api/hackathons/" + id + "/inscriptions", "{}", fatou);
		String equipeKofi = corpsDe(poster("/api/hackathons/" + id + "/equipes", "{\"nom\":\"Les Griots\"}", kofi)).get("id").asString();
		poster("/api/hackathons/" + id + "/equipes/" + equipeKofi + "/membres", "{\"utilisateurId\":\"" + fatou.id() + "\"}", kofi);
		String equipeMoussa = corpsDe(poster("/api/hackathons/" + id + "/equipes", "{\"nom\":\"Teranga\"}", moussa)).get("id").asString();
		poster("/api/hackathons/" + id + "/jures", "{\"utilisateurId\":\"" + jo.id() + "\"}", awa);
		calendrier(id, -1, 1);
		String renduKofi = rendre(id, equipeKofi, kofi);
		String renduMoussa = rendre(id, equipeMoussa, moussa);
		calendrier(id, -3, -1);
		assertThat(noter(id, renduMoussa, critere, 5, "Utile dès demain", jo)).hasStatus(HttpStatus.OK);
		assertThat(noter(id, renduKofi, critere, 2, null, jo)).hasStatus(HttpStatus.OK);
		JsonNode vue = corpsDe(mvc.get().uri("/api/hackathons/" + id).with(oauth2Login().oauth2User(awa)).exchange());
		return new Scene(id, vue.at("/prix/0/id").asString(), vue.at("/prix/1/id").asString(), equipeKofi, equipeMoussa, renduMoussa,
				awa, kofi, fatou, jo);
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

	private MvcTestResult noter(String hackathon, String rendu, String critere, int valeur, String commentaire, UtilisateurConnecte qui) {
		return mvc.put().uri("/api/hackathons/" + hackathon + "/rendus/" + rendu + "/notes").contentType(MediaType.APPLICATION_JSON)
			.content("{\"notes\":[{\"critereId\":\"" + critere + "\",\"valeur\":" + valeur + "}]"
					+ (commentaire == null ? "" : ",\"commentaire\":\"" + commentaire + "\"") + "}")
			.with(csrf()).with(oauth2Login().oauth2User(qui)).exchange();
	}

	/** `raison`, si elle est donnée, accompagne les deux gagnants : inverser le classement écarte les deux prix. */
	private MvcTestResult confirmer(Scene s, String premier, String second, String raison) throws Exception {
		int version = corpsDe(mvc.get().uri("/api/hackathons/" + s.hackathon()).with(oauth2Login().oauth2User(s.awa())).exchange()).get("version").asInt();
		String r = raison == null ? "" : ",\"raison\":\"" + raison + "\"";
		String corps = """
				{"version":%d,"gagnants":[{"prixId":"%s","equipeId":"%s"%s},{"prixId":"%s","equipeId":"%s"%s}]}
				""".formatted(version, s.premierPrix(), premier, r, s.secondPrix(), second, r);
		return poster("/api/hackathons/" + s.hackathon() + "/resultats", corps, s.awa());
	}

	private MvcTestResult poster(String chemin, String corps, UtilisateurConnecte qui) {
		return mvc.post().uri(chemin).contentType(MediaType.APPLICATION_JSON).content(corps)
			.with(csrf()).with(oauth2Login().oauth2User(qui)).exchange();
	}

	private JsonNode corpsDe(MvcTestResult r) throws Exception {
		return json.readTree(r.getResponse().getContentAsString());
	}

	// ---------------------------------------------------------------- publier

	@Test
	void confirmer_les_gagnants_publie_les_resultats_pour_tous() throws Exception {
		Scene s = scene();
		assertThat(mvc.get().uri("/api/hackathons/" + s.hackathon() + "/resultats").exchange()).hasStatus(HttpStatus.NOT_FOUND);
		JsonNode avant = corpsDe(mvc.get().uri("/api/hackathons/" + s.hackathon()).with(oauth2Login().oauth2User(s.awa())).exchange());
		assertThat(avant.at("/moi/actions").toString()).contains("confirmer_resultats");

		MvcTestResult r = confirmer(s, s.equipeMoussa(), s.equipeKofi(), null);
		assertThat(r).hasStatus(HttpStatus.OK);

		MvcTestResult publics = mvc.get().uri("/api/hackathons/" + s.hackathon() + "/resultats").exchange();
		assertThat(publics).hasStatus(HttpStatus.OK);
		JsonNode res = corpsDe(publics);
		assertThat(res.at("/gagnants/0/prix/nom").asString()).isEqualTo("Prix du jury");
		assertThat(res.at("/gagnants/0/equipe/nom").asString()).isEqualTo("Teranga");
		assertThat(res.at("/classement/0/rang").asInt()).isEqualTo(1);
		assertThat(res.at("/classement/0/rendu/id").asString()).isEqualTo(s.renduMoussa());
		assertThat(res.at("/classement/0/retourDuJury/0").asString()).isEqualTo("Utile dès demain");
		assertThat(res.at("/classement/0/scorePondere").isNumber()).isTrue();
		assertThat(res.at("/classement/0/scorePondere").asDouble()).isGreaterThanOrEqualTo(res.at("/classement/1/scorePondere").asDouble());
		assertThat(res.toString()).doesNotContain("\"nom\":\"jo\"").doesNotContain("@secret.example");
		assertThat(corpsDe(mvc.get().uri("/api/hackathons/" + s.hackathon()).exchange()).get("statut").asString()).isEqualTo("resultats");
	}

	@Test
	void un_gagnant_different_du_classement_demande_une_raison() throws Exception {
		Scene s = scene();
		MvcTestResult sansRaison = confirmer(s, s.equipeKofi(), s.equipeMoussa(), null);
		assertThat(sansRaison).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(corpsDe(sansRaison).toString()).contains("gagnants[0].raison");

		assertThat(confirmer(s, s.equipeKofi(), s.equipeMoussa(), "Le seul projet déjà utilisé par une vraie boutique")).hasStatus(HttpStatus.OK);
		assertThat(base.sql("SELECT raison_ajustement FROM gagnant WHERE prix_id = :p").param("p", UUID.fromString(s.premierPrix()))
			.query(String.class).single()).startsWith("Le seul projet");
	}

	@Test
	void apres_publication_les_notes_sont_verrouillees_et_on_ne_publie_qu_une_fois() throws Exception {
		Scene s = scene();
		assertThat(confirmer(s, s.equipeMoussa(), s.equipeKofi(), null)).hasStatus(HttpStatus.OK);
		JsonNode h = corpsDe(mvc.get().uri("/api/hackathons/" + s.hackathon()).with(oauth2Login().oauth2User(s.awa())).exchange());
		MvcTestResult note = noter(s.hackathon(), s.renduMoussa(), h.at("/criteres/0/id").asString(), 1, null, s.jo());
		assertThat(note).hasStatus(HttpStatus.CONFLICT);
		assertThat(corpsDe(note).get("code").asString()).isEqualTo("etat_invalide");
		assertThat(confirmer(s, s.equipeMoussa(), s.equipeKofi(), null)).hasStatus(HttpStatus.CONFLICT);
	}

	@Test
	void seul_l_organisateur_publie() throws Exception {
		Scene s = scene();
		MvcTestResult r = poster("/api/hackathons/" + s.hackathon() + "/resultats",
				"{\"version\":1,\"gagnants\":[{\"prixId\":\"" + s.premierPrix() + "\",\"equipeId\":\"" + s.equipeMoussa() + "\"}]}", s.jo());
		assertThat(r).hasStatus(HttpStatus.FORBIDDEN);
	}

	// ---------------------------------------------------------------- données personnelles

	@Test
	void l_export_contient_inscriptions_rendus_et_notes() throws Exception {
		Scene s = scene();
		JsonNode kofi = corpsDe(mvc.get().uri("/api/me/export").with(oauth2Login().oauth2User(s.kofi())).exchange()).get("hackathons");
		assertThat(kofi.get("inscriptions")).hasSize(1);
		assertThat(kofi.get("rendus")).hasSize(1);
		JsonNode jo = corpsDe(mvc.get().uri("/api/me/export").with(oauth2Login().oauth2User(s.jo())).exchange()).get("hackathons");
		assertThat(jo.get("notes")).hasSize(2);
		JsonNode awa = corpsDe(mvc.get().uri("/api/me/export").with(oauth2Login().oauth2User(s.awa())).exchange()).get("hackathons");
		assertThat(awa.get("organises")).hasSize(1);
	}

	@Test
	void supprimer_son_compte_anonymise_ses_notes_et_confie_son_equipe() throws Exception {
		Scene s = scene();
		BigDecimal avant = jury.scorePondere(UUID.fromString(s.renduMoussa()));
		assertThat(mvc.delete().uri("/api/me").with(csrf()).with(oauth2Login().oauth2User(s.jo())).exchange()).hasStatus(HttpStatus.ACCEPTED);
		assertThat(mvc.delete().uri("/api/me").with(csrf()).with(oauth2Login().oauth2User(s.kofi())).exchange()).hasStatus(HttpStatus.ACCEPTED);

		assertThat(jury.scorePondere(UUID.fromString(s.renduMoussa()))).isEqualByComparingTo(avant);
		assertThat(base.sql("SELECT count(*) FROM note WHERE utilisateur_id = :u").param("u", s.jo().id()).query(Integer.class).single()).isZero();
		assertThat(base.sql("SELECT chef_id FROM equipe WHERE id = :e").param("e", UUID.fromString(s.equipeKofi())).query(UUID.class).single())
			.isEqualTo(s.fatou().id());
		assertThat(base.sql("SELECT count(*) FROM rendu WHERE equipe_id = :e").param("e", UUID.fromString(s.equipeKofi())).query(Integer.class).single())
			.isEqualTo(1);
	}

}
