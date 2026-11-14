package dev.jamii.api.missions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;

import java.sql.Timestamp;
import java.time.Duration;
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

/** Annuler une mission, ouvrir et trancher un désaccord (contracts/missions.yaml, tag « fin »), sur un vrai PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class FinTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient base;

	@Autowired
	JsonMapper json;

	@Autowired
	Livraisons livraisons;

	/** Une mission attribuée à Kofi par Awa, sur un devis d'un jalon de 10 jours. */
	private record Scene(String mission, String jalon, UtilisateurConnecte awa, UtilisateurConnecte kofi) {
	}

	private static final String RAISON = "{\"version\":%d,\"raison\":\"%s\"}";

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

	private MvcTestResult annuler(Scene s, UtilisateurConnecte qui) throws Exception {
		return post("/api/missions/" + s.mission() + "/annuler", RAISON.formatted(version(s.mission(), s.awa()), "On arrête ici, désolé."), qui);
	}

	private MvcTestResult desaccord(Scene s, UtilisateurConnecte qui) throws Exception {
		return post("/api/missions/" + s.mission() + "/desaccord",
				RAISON.formatted(version(s.mission(), s.awa()), "Le catalogue livré ne correspond pas au devis."), qui);
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

	private void reculer(String sql, Scene s, Duration duree) {
		base.sql(sql).param("t", Timestamp.from(Instant.now().minus(duree))).param("m", UUID.fromString(s.mission())).update();
	}

	// ---------------------------------------------------------------- annuler

	@Test
	void annulation_d_un_commun_accord() throws Exception {
		Scene s = scene();
		MvcTestResult demande = annuler(s, s.awa());
		assertThat(demande).hasStatus(HttpStatus.OK);
		assertThat(corps(demande).get("statut").asString()).isEqualTo("attribuee");
		assertThat(corps(demande).at("/travail/annulationDemandeePar").asString()).isEqualTo("client");

		MvcTestResult accord = annuler(s, s.kofi());
		assertThat(accord).hasStatus(HttpStatus.OK);
		assertThat(corps(accord).get("statut").asString()).isEqualTo("annulee");
	}

	@Test
	void le_developpeur_annule_seul_sans_informations_de_depart_apres_7_jours() throws Exception {
		Scene s = scene();
		// Avant 7 jours : sa demande attend le client.
		assertThat(corps(annuler(s, s.kofi())).get("statut").asString()).isEqualTo("attribuee");

		Scene t = scene();
		reculer("UPDATE devis SET accepte_le = :t WHERE mission_id = :m", t, Duration.ofDays(8));
		assertThat(corps(annuler(t, t.kofi())).get("statut").asString()).isEqualTo("annulee");
	}

	@Test
	void le_client_annule_seul_si_un_jalon_a_plus_de_7_jours_de_retard() throws Exception {
		Scene s = scene();
		demarrer(s);
		// Démarré il y a 16 jours, échéance à 10 jours : 6 jours de retard, pas assez.
		reculer("UPDATE mission SET demarre_le = :t WHERE id = :m", s, Duration.ofDays(16));
		assertThat(corps(annuler(s, s.awa())).get("statut").asString()).isEqualTo("en_cours");

		Scene t = scene();
		demarrer(t);
		reculer("UPDATE mission SET demarre_le = :t WHERE id = :m", t, Duration.ofDays(18));
		assertThat(corps(annuler(t, t.awa())).get("statut").asString()).isEqualTo("annulee");
	}

	@Test
	void un_jalon_livre_ne_s_annule_pas_il_faut_un_desaccord() throws Exception {
		Scene s = scene();
		demarrer(s);
		livrer(s);
		MvcTestResult r = annuler(s, s.awa());
		assertThat(r).hasStatus(HttpStatus.CONFLICT);
		assertThat(corps(r).get("code").asString()).isEqualTo("annulation_non_permise");
		assertThat(annuler(s, utilisateur("curieux"))).hasStatus(HttpStatus.NOT_FOUND);
	}

	// ---------------------------------------------------------------- désaccord

	@Test
	void ouvrir_un_desaccord_gele_les_livraisons_et_l_acceptation_automatique() throws Exception {
		Scene s = scene();
		demarrer(s);
		livrer(s);
		MvcTestResult ouvert = desaccord(s, s.awa());
		assertThat(ouvert).hasStatus(HttpStatus.OK);
		assertThat(corps(ouvert).get("statut").asString()).isEqualTo("desaccord");
		assertThat(corps(ouvert).at("/travail/desaccord/ouvertPar").asString()).isEqualTo("client");

		reculer("UPDATE jalon_mission SET livre_le = :t WHERE devis_id IN (SELECT id FROM devis WHERE mission_id = :m)", s, Duration.ofHours(80));
		livraisons.accepterAutomatiquement();
		assertThat(corps(lire("/api/missions/" + s.mission(), s.awa())).at("/travail/devis/jalons/0/statut").asString()).isEqualTo("livre");

		MvcTestResult retouche = post("/api/missions/" + s.mission() + "/jalons/" + s.jalon() + "/demander-retouche",
				"{\"version\":" + version(s.mission(), s.awa()) + ",\"raison\":\"Les prix n'apparaissent pas.\"}", s.awa());
		assertThat(retouche).hasStatus(HttpStatus.CONFLICT);
	}

	@Test
	void seul_un_moderateur_tranche() throws Exception {
		Scene s = scene();
		demarrer(s);
		desaccord(s, s.kofi());
		UtilisateurConnecte modo = utilisateur("modo", Role.MEMBRE, Role.MODERATEUR);
		String decision = "{\"version\":%d,\"issue\":\"annulee\",\"explication\":\"Le travail livré ne suit pas le devis accepté.\"}";

		assertThat(post("/api/missions/" + s.mission() + "/desaccord/trancher", decision.formatted(version(s.mission(), s.awa())), s.awa()))
			.hasStatus(HttpStatus.FORBIDDEN);

		JsonNode vue = corps(lire("/api/missions/" + s.mission(), modo));
		assertThat(vue.at("/moi/role").asString()).isEqualTo("moderateur");
		assertThat(vue.at("/moi/actions").toString()).isEqualTo("[\"trancher\"]");
		assertThat(vue.at("/travail/desaccord/raison").asString()).startsWith("Le catalogue");

		MvcTestResult tranche = post("/api/missions/" + s.mission() + "/desaccord/trancher", decision.formatted(vue.get("version").asInt()), modo);
		assertThat(tranche).hasStatus(HttpStatus.OK);
		JsonNode m = corps(lire("/api/missions/" + s.mission(), s.kofi()));
		assertThat(m.get("statut").asString()).isEqualTo("annulee");
		assertThat(m.at("/travail/desaccord/issue").asString()).isEqualTo("annulee");
		assertThat(m.at("/travail/desaccord/explication").asString()).startsWith("Le travail livré");
	}

	@Test
	void un_moderateur_ne_voit_pas_une_mission_sans_desaccord() throws Exception {
		Scene s = scene();
		UtilisateurConnecte modo = utilisateur("modo", Role.MEMBRE, Role.MODERATEUR);
		assertThat(lire("/api/missions/" + s.mission(), modo)).hasStatus(HttpStatus.NOT_FOUND);
	}

}
