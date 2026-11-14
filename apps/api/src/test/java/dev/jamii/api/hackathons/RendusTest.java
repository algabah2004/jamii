package dev.jamii.api.hackathons;

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

/** Rendre le projet d'une équipe (contracts/hackathons.yaml, tag « rendus »), sur un vrai PostgreSQL, sans réseau. */
@SpringBootTest(properties = "jamii.verification-lien.fournisseur=simulee")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RendusTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient base;

	@Autowired
	JsonMapper json;

	private static final String RENDU = """
			{"titre":"Le carnet du commerçant","description":"Un carnet de ventes hors ligne pour les boutiques de quartier.",
			 "lienProduit":"%s","depot":"https://github.com/griots/carnet","reponses":["Les ventes notées sur papier se perdent."]%s}
			""";

	// ---------------------------------------------------------------- aides

	/** Un hackathon en cours, avec l'équipe de Kofi (Kofi et Fatou). */
	private record Scene(String hackathon, String equipe, UtilisateurConnecte awa, UtilisateurConnecte kofi, UtilisateurConnecte fatou) {
	}

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
		JsonNode h = corpsDe(poster("/api/hackathons", """
				{"titre":"Dakar Cloud Hack","theme":"Des services utiles aux commerçants","description":"Deux jours pour construire un service qui aide un commerçant.",
				 "lieu":"en_ligne",
				 "calendrier":{"inscriptionsOuvertesLe":"%s","inscriptionsFermeesLe":"%s","debutLe":"%s","finRendusLe":"%s","resultatsLe":"%s"},
				 "tailleEquipe":{"min":1,"max":4},"modeInscription":"ouvert",
				 "codeDeConduite":"Respect de chacun, aucun harcèlement, le code de chaque équipe reste le sien.",
				 "questions":["Quel problème résolvez-vous ?"],
				 "criteres":[{"nom":"Utilité","poids":100}]}
				""".formatted(date(-1), date(9), date(10), date(12), date(14)), awa));
		String id = h.get("id").asString();
		poster("/api/hackathons/" + id + "/publier", "{\"version\":" + h.get("version") + "}", awa);
		poster("/api/hackathons/" + id + "/inscriptions", "{}", fatou);
		String equipe = corpsDe(poster("/api/hackathons/" + id + "/equipes", "{\"nom\":\"Les Griots\"}", kofi)).get("id").asString();
		assertThat(poster("/api/hackathons/" + id + "/equipes/" + equipe + "/membres", "{\"utilisateurId\":\"" + fatou.id() + "\"}", kofi))
			.hasStatus(HttpStatus.OK);
		// Le hackathon a commencé hier et les rendus ferment demain.
		recaler(id, -1, 1);
		return new Scene(id, equipe, awa, kofi, fatou);
	}

	private void recaler(String id, long debut, long fin) {
		Instant maintenant = Instant.now();
		base.sql("""
				UPDATE hackathon SET inscriptions_ouvertes_le = :o, inscriptions_fermees_le = :f, debut_le = :d, fin_rendus_le = :fr,
				  resultats_le = :r WHERE id = :id
				""")
			.param("o", Timestamp.from(maintenant.plus(debut - 5, ChronoUnit.DAYS))).param("f", Timestamp.from(maintenant.plus(debut - 1, ChronoUnit.DAYS)))
			.param("d", Timestamp.from(maintenant.plus(debut, ChronoUnit.DAYS))).param("fr", Timestamp.from(maintenant.plus(fin, ChronoUnit.DAYS)))
			.param("r", Timestamp.from(maintenant.plus(fin + 2, ChronoUnit.DAYS))).param("id", UUID.fromString(id)).update();
	}

	private MvcTestResult poster(String chemin, String corps, UtilisateurConnecte qui) {
		return mvc.post().uri(chemin).contentType(MediaType.APPLICATION_JSON).content(corps)
			.with(csrf()).with(oauth2Login().oauth2User(qui)).exchange();
	}

	private MvcTestResult rendre(Scene s, String lien, String version, UtilisateurConnecte qui) {
		return mvc.put().uri("/api/hackathons/" + s.hackathon() + "/equipes/" + s.equipe() + "/rendu").contentType(MediaType.APPLICATION_JSON)
			.content(RENDU.formatted(lien, version == null ? "" : ",\"version\":" + version))
			.with(csrf()).with(oauth2Login().oauth2User(qui)).exchange();
	}

	private MvcTestResult lire(Scene s, UtilisateurConnecte qui) {
		return mvc.get().uri("/api/hackathons/" + s.hackathon() + "/equipes/" + s.equipe() + "/rendu").with(oauth2Login().oauth2User(qui)).exchange();
	}

	private JsonNode corpsDe(MvcTestResult r) throws Exception {
		return json.readTree(r.getResponse().getContentAsString());
	}

	// ---------------------------------------------------------------- rendre

	@Test
	void un_membre_rend_puis_modifie_avant_l_heure() throws Exception {
		Scene s = scene();
		MvcTestResult premier = rendre(s, "https://carnet.example.org", null, s.kofi());
		assertThat(premier).hasStatus(HttpStatus.CREATED);
		JsonNode r = corpsDe(premier);
		JsonNode vue = corpsDe(mvc.get().uri("/api/hackathons/" + s.hackathon()).with(oauth2Login().oauth2User(s.fatou())).exchange());
		assertThat(vue.at("/moi/actions").toString()).isEqualTo("[\"rendre\"]");
		assertThat(r.get("lienRepond").asBoolean()).isTrue();
		assertThat(r.get("lienVerifieLe").isNull()).isFalse();
		assertThat(r.get("reponses")).hasSize(1);

		MvcTestResult modifie = rendre(s, "https://carnet.example.org", r.get("version").toString(), s.fatou());
		assertThat(modifie).hasStatus(HttpStatus.OK);
		assertThat(corpsDe(modifie).get("version").asInt()).isEqualTo(r.get("version").asInt() + 1);

		MvcTestResult perime = rendre(s, "https://carnet.example.org", r.get("version").toString(), s.kofi());
		assertThat(perime).hasStatus(HttpStatus.CONFLICT);
		assertThat(corpsDe(perime).get("code").asString()).isEqualTo("modification_concurrente");
	}

	@Test
	void un_non_membre_ne_peut_pas_rendre_et_ne_voit_pas_le_rendu() throws Exception {
		Scene s = scene();
		UtilisateurConnecte curieux = utilisateur("curieux");
		assertThat(rendre(s, "https://carnet.example.org", null, curieux)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(rendre(s, "https://carnet.example.org", null, s.awa())).hasStatus(HttpStatus.FORBIDDEN);
		rendre(s, "https://carnet.example.org", null, s.kofi());

		assertThat(lire(s, curieux)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(lire(s, s.awa())).hasStatus(HttpStatus.OK);
		assertThat(lire(s, s.fatou())).hasStatus(HttpStatus.OK);
	}

	@Test
	void apres_la_fin_des_rendus_le_delai_est_depasse() throws Exception {
		Scene s = scene();
		base.sql("UPDATE hackathon SET fin_rendus_le = :t WHERE id = :id")
			.param("t", Timestamp.from(Instant.now().minusSeconds(1))).param("id", UUID.fromString(s.hackathon())).update();
		MvcTestResult r = rendre(s, "https://carnet.example.org", null, s.kofi());
		assertThat(r).hasStatus(HttpStatus.CONFLICT);
		assertThat(corpsDe(r).get("code").asString()).isEqualTo("delai_depasse");
	}

	@Test
	void avant_le_debut_on_ne_rend_pas() throws Exception {
		Scene s = scene();
		base.sql("UPDATE hackathon SET debut_le = :t WHERE id = :id")
			.param("t", Timestamp.from(Instant.now().plus(1, ChronoUnit.HOURS))).param("id", UUID.fromString(s.hackathon())).update();
		MvcTestResult r = rendre(s, "https://carnet.example.org", null, s.kofi());
		assertThat(r).hasStatus(HttpStatus.CONFLICT);
		assertThat(corpsDe(r).get("code").asString()).isEqualTo("etat_invalide");
	}

	@Test
	void un_lien_qui_ne_repond_pas_est_signale_et_le_rendu_est_garde() throws Exception {
		Scene s = scene();
		MvcTestResult r = rendre(s, "https://carnet.invalid", null, s.kofi());
		assertThat(r).hasStatus(HttpStatus.CREATED);
		assertThat(corpsDe(r).get("lienRepond").asBoolean()).isFalse();
		assertThat(corpsDe(lire(s, s.kofi())).get("titre").asString()).isEqualTo("Le carnet du commerçant");
	}

	@Test
	void un_lien_vers_une_adresse_interne_ou_sans_https_est_refuse() throws Exception {
		Scene s = scene();
		for (String lien : new String[] { "http://carnet.example.org", "https://127.0.0.1", "https://10.0.0.1/admin", "file:///etc/passwd",
				"https://localhost", "https://[::1]/", "https://metadata.internal", "https://user:mdp@carnet.example.org",
				"https://carnet.example.org:8443" }) {
			MvcTestResult r = rendre(s, lien, null, s.kofi());
			assertThat(r).as(lien).hasStatus(HttpStatus.BAD_REQUEST);
			assertThat(corpsDe(r).toString()).as(lien).contains("lienProduit");
		}
		assertThat(base.sql("SELECT count(*) FROM rendu WHERE equipe_id = :e").param("e", UUID.fromString(s.equipe())).query(Integer.class).single())
			.isZero();
	}

	@Test
	void un_rendu_sans_lien_du_produit_est_refuse() throws Exception {
		Scene s = scene();
		MvcTestResult r = mvc.put().uri("/api/hackathons/" + s.hackathon() + "/equipes/" + s.equipe() + "/rendu")
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"titre\":\"Le carnet\",\"description\":\"Un carnet de ventes hors ligne pour les boutiques.\"}")
			.with(csrf()).with(oauth2Login().oauth2User(s.kofi())).exchange();
		assertThat(r).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(corpsDe(r).toString()).contains("lienProduit");
	}

}
