package dev.jamii.api.missions;

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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import dev.jamii.api.TestcontainersConfiguration;
import dev.jamii.api.auth.Role;
import dev.jamii.api.auth.UtilisateurConnecte;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Les avis d'une mission et ses données personnelles (export, suppression du compte), sur un vrai PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class AvisTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient base;

	@Autowired
	JsonMapper json;

	@Autowired
	ApplicationEvents evenements;

	/** Une mission attribuée à Kofi par Awa, sur un devis d'un jalon de 10 jours. */
	private record Scene(String mission, String jalon, UtilisateurConnecte awa, UtilisateurConnecte kofi) {
	}

	// ---------------------------------------------------------------- aides

	private UtilisateurConnecte utilisateur(String nom, Role... roles) {
		UUID id = UUID.randomUUID();
		Timestamp t = Timestamp.from(Instant.now());
		base.sql("INSERT INTO utilisateur (id, nom, email, cree_le, mis_a_jour_le) VALUES (:id, :nom, :email, :t, :t)")
			.param("id", id).param("nom", nom).param("email", nom + "." + id + "@secret.example").param("t", t).update();
		return new UtilisateurConnecte(id, roles.length == 0 ? Set.of(Role.MEMBRE) : Set.of(roles));
	}

	private Scene scene() throws Exception {
		UtilisateurConnecte awa = utilisateur("awa");
		UtilisateurConnecte kofi = utilisateur("kofi");
		UUID id = UUID.randomUUID();
		Timestamp t = Timestamp.from(Instant.now());
		base.sql("""
				INSERT INTO mission (id, client_id, titre, besoin, competences, visibilite, statut, cree_le, publiee_le)
				VALUES (:id, :c, 'Boutique en ligne', 'Montrer nos modèles avec photos et prix, recevoir les commandes.',
				        ARRAY['vue'], 'publique', 'publiee', :t, :t)
				""").param("id", id).param("c", awa.id()).param("t", t).update();
		String m = id.toString();
		String proposition = corps(post("/api/missions/" + m + "/propositions", """
				{"message":"Je fais ce genre de boutique depuis deux ans, avec le paiement Wave.","prix":{"centimes":400000,"devise":"XOF"},"delaiJours":21}""",
				kofi)).get("id").asString();
		post("/api/missions/" + m + "/propositions/" + proposition + "/retenir", "{\"version\":" + version(m, awa) + "}", awa);
		JsonNode devis = corps(post("/api/missions/" + m + "/devis", """
				{"total":{"centimes":400000,"devise":"XOF"},"retouchesComprises":1,
				 "jalons":[{"titre":"La boutique","montant":{"centimes":400000,"devise":"XOF"},"delaiJours":10}]}""", kofi));
		assertThat(post("/api/missions/" + m + "/devis/" + devis.get("id").asString() + "/accepter", "{\"version\":" + version(m, awa) + "}", awa))
			.hasStatus(HttpStatus.OK);
		return new Scene(m, devis.at("/jalons/0/id").asString(), awa, kofi);
	}

	private void demarrer(Scene s) throws Exception {
		assertThat(post("/api/missions/" + s.mission() + "/demarrer",
				"{\"version\":" + version(s.mission(), s.awa()) + ",\"informations\":\"Les photos sont dans le dossier partagé.\"}", s.awa()))
			.hasStatus(HttpStatus.OK);
	}

	private MvcTestResult livrer(Scene s) throws Exception {
		return post("/api/missions/" + s.mission() + "/jalons/" + s.jalon() + "/livrer",
				"{\"version\":" + version(s.mission(), s.awa()) + ",\"message\":\"La boutique est en ligne, à tester.\"}", s.kofi());
	}

	private int version(String mission, UtilisateurConnecte qui) throws Exception {
		return corps(lire("/api/missions/" + mission, qui)).get("version").asInt();
	}

	private MvcTestResult post(String chemin, String corps, UtilisateurConnecte qui) {
		return mvc.post().uri(chemin).contentType(MediaType.APPLICATION_JSON).content(corps)
			.with(csrf()).with(oauth2Login().oauth2User(qui)).exchange();
	}

	private MvcTestResult lire(String uri, UtilisateurConnecte qui) {
		return mvc.get().uri(uri).with(oauth2Login().oauth2User(qui)).exchange();
	}

	private JsonNode corps(MvcTestResult r) throws Exception {
		return json.readTree(r.getResponse().getContentAsString());
	}

	/** Démarrée, livrée, acceptée : la mission est terminée. */
	private Scene terminee() throws Exception {
		Scene s = scene();
		demarrer(s);
		assertThat(livrer(s)).hasStatus(HttpStatus.OK);
		assertThat(post("/api/missions/" + s.mission() + "/jalons/" + s.jalon() + "/accepter",
				"{\"version\":" + version(s.mission(), s.awa()) + "}", s.awa()))
			.hasStatus(HttpStatus.OK);
		assertThat(corps(lire("/api/missions/" + s.mission(), s.awa())).get("statut").asString()).isEqualTo("terminee");
		return s;
	}

	private MvcTestResult avis(Scene s, String corps, UtilisateurConnecte qui) {
		return post("/api/missions/" + s.mission() + "/avis", corps, qui);
	}

	private MvcTestResult supprimerCompte(UtilisateurConnecte qui) {
		return mvc.delete().uri("/api/me").with(csrf()).with(oauth2Login().oauth2User(qui)).exchange();
	}

	// ---------------------------------------------------------------- avis

	@Test
	void la_mission_terminee_est_annoncee_une_fois_pour_le_profil_du_developpeur() throws Exception {
		Scene s = terminee();
		assertThat(evenements.stream(MissionTerminee.class).filter(e -> e.missionId().toString().equals(s.mission())))
			.singleElement()
			.satisfies(e -> {
				assertThat(e.developpeurId()).isEqualTo(s.kofi().id());
				assertThat(e.clientId()).isEqualTo(s.awa().id());
				assertThat(e.titre()).isEqualTo("Boutique en ligne");
			});
	}

	@Test
	void chaque_partie_donne_son_avis_une_fois_la_mission_terminee() throws Exception {
		Scene s = terminee();
		assertThat(corps(lire("/api/missions/" + s.mission(), s.awa())).at("/moi/actions").toString()).contains("donner_avis");

		MvcTestResult donne = avis(s, "{\"note\":5,\"texte\":\"Travail soigné, livré à temps.\"}", s.awa());
		assertThat(donne).hasStatus(HttpStatus.CREATED);
		assertThat(corps(donne).get("auteur").asString()).isEqualTo("client");
		assertThat(avis(s, "{\"note\":4}", s.kofi())).hasStatus(HttpStatus.CREATED);

		MvcTestResult encore = avis(s, "{\"note\":1}", s.awa());
		assertThat(encore).hasStatus(HttpStatus.CONFLICT);
		assertThat(corps(encore).get("code").asString()).isEqualTo("avis_deja_donne");

		JsonNode vue = corps(lire("/api/missions/" + s.mission(), s.kofi()));
		assertThat(vue.at("/moi/actions").toString()).doesNotContain("donner_avis");
		assertThat(vue.at("/travail/avis/0/auteur").asString()).isEqualTo("client");
		assertThat(vue.at("/travail/avis/0/note").asInt()).isEqualTo(5);
		assertThat(vue.at("/travail/avis/1/auteur").asString()).isEqualTo("developpeur");
	}

	@Test
	void pas_d_avis_avant_la_fin_ni_par_un_tiers_ni_hors_bornes() throws Exception {
		Scene s = scene();
		MvcTestResult tot = avis(s, "{\"note\":5}", s.awa());
		assertThat(tot).hasStatus(HttpStatus.CONFLICT);
		assertThat(corps(tot).get("code").asString()).isEqualTo("etat_invalide");

		Scene t = terminee();
		assertThat(avis(t, "{\"note\":5}", utilisateur("curieux"))).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(avis(t, "{\"note\":6}", t.awa())).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(avis(t, "{\"note\":0}", t.awa())).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(avis(t, "{\"note\":3,\"texte\":\"" + "a".repeat(1001) + "\"}", t.awa())).hasStatus(HttpStatus.BAD_REQUEST);
	}

	// ---------------------------------------------------------------- données personnelles

	@Test
	void mes_missions_propositions_livraisons_et_avis_sont_dans_mon_export() throws Exception {
		Scene s = terminee();
		avis(s, "{\"note\":5}", s.kofi());

		JsonNode kofi = corps(lire("/api/me/export", s.kofi())).get("missions");
		assertThat(kofi.at("/propositions/0/missionId").asString()).isEqualTo(s.mission());
		assertThat(kofi.at("/livraisons/0/message").asString()).startsWith("La boutique est en ligne");
		assertThat(kofi.at("/avis/0/auteur").asString()).isEqualTo("developpeur");
		assertThat(kofi.get("publiees").size()).isZero();

		JsonNode awa = corps(lire("/api/me/export", s.awa())).get("missions");
		assertThat(awa.at("/publiees/0/id").asString()).isEqualTo(s.mission());
		assertThat(awa.get("propositions").size()).isZero();
	}

	@Test
	void supprimer_le_compte_du_developpeur_annule_le_travail_en_cours_mais_garde_l_avis_et_l_historique() throws Exception {
		Scene enCours = scene();
		demarrer(enCours);
		Scene finie = terminee();
		avis(finie, "{\"note\":4,\"texte\":\"Client clair et réactif.\"}", finie.kofi());

		assertThat(supprimerCompte(enCours.kofi())).hasStatus(HttpStatus.ACCEPTED);
		assertThat(supprimerCompte(finie.kofi())).hasStatus(HttpStatus.ACCEPTED);

		assertThat(corps(lire("/api/missions/" + enCours.mission(), enCours.awa())).get("statut").asString()).isEqualTo("annulee");

		MvcTestResult historique = lire("/api/missions/" + finie.mission(), finie.awa());
		assertThat(historique).hasStatus(HttpStatus.OK);
		JsonNode vue = corps(historique);
		assertThat(vue.get("statut").asString()).isEqualTo("terminee");
		assertThat(vue.at("/travail/avis/0/texte").asString()).isEqualTo("Client clair et réactif.");
		assertThat(base.sql("SELECT count(*) FROM avis_mission WHERE mission_id = :m AND auteur_id IS NULL")
			.param("m", UUID.fromString(finie.mission())).query(Long.class).single()).isEqualTo(1L);
	}

	@Test
	void supprimer_le_compte_du_client_efface_ses_brouillons_et_ferme_ses_missions_publiees() throws Exception {
		UtilisateurConnecte awa = utilisateur("awa");
		UUID brouillon = UUID.randomUUID();
		UUID publiee = UUID.randomUUID();
		Timestamp t = Timestamp.from(Instant.now());
		for (var m : new Object[][] { { brouillon, "brouillon" }, { publiee, "publiee" } }) {
			base.sql("""
					INSERT INTO mission (id, client_id, titre, besoin, competences, visibilite, statut, cree_le, publiee_le)
					VALUES (:id, :c, 'Site vitrine', 'Présenter la boutique et ses horaires sur une page simple.', ARRAY['vue'], 'publique', :s, :t,
					        CASE WHEN :s = 'brouillon' THEN NULL ELSE :t END)
					""").param("id", m[0]).param("c", awa.id()).param("s", m[1]).param("t", t).update();
		}

		assertThat(supprimerCompte(awa)).hasStatus(HttpStatus.ACCEPTED);

		assertThat(base.sql("SELECT count(*) FROM mission WHERE id = :m").param("m", brouillon).query(Long.class).single()).isZero();
		assertThat(base.sql("SELECT statut FROM mission WHERE id = :m").param("m", publiee).query(String.class).single()).isEqualTo("fermee");
	}

}
