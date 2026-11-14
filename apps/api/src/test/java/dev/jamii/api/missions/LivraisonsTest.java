package dev.jamii.api.missions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
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

/** Démarrer, livrer, accepter, retoucher et déclarer un paiement (contracts/missions.yaml, tag « suivi »), sur un vrai PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class LivraisonsTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient base;

	@Autowired
	JsonMapper json;

	@Autowired
	Livraisons livraisons;

	/** Une mission attribuée à Kofi par Awa, sur un devis de deux jalons (10 et 21 jours) et une retouche comprise. */
	private record Scene(String mission, String premier, String second, UtilisateurConnecte awa, UtilisateurConnecte kofi) {
	}

	// ---------------------------------------------------------------- aides

	private UtilisateurConnecte utilisateur(String nom) {
		UUID id = UUID.randomUUID();
		Timestamp t = Timestamp.from(Instant.now());
		base.sql("INSERT INTO utilisateur (id, nom, email, cree_le, mis_a_jour_le) VALUES (:id, :nom, :email, :t, :t)")
			.param("id", id).param("nom", nom).param("email", nom + "." + id + "@secret.example").param("t", t).update();
		return new UtilisateurConnecte(id, Set.of(Role.MEMBRE));
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
		assertThat(post("/api/missions/" + m + "/propositions/" + proposition + "/retenir", version(m, awa), awa)).hasStatus(HttpStatus.OK);
		JsonNode devis = corps(post("/api/missions/" + m + "/devis", """
				{"total":{"centimes":400000,"devise":"XOF"},"retouchesComprises":1,
				 "jalons":[{"titre":"Le catalogue","montant":{"centimes":150000,"devise":"XOF"},"delaiJours":10},
				           {"titre":"Les commandes","montant":{"centimes":250000,"devise":"XOF"},"delaiJours":21}]}""", kofi));
		assertThat(post("/api/missions/" + m + "/devis/" + devis.get("id").asString() + "/accepter", version(m, awa), awa)).hasStatus(HttpStatus.OK);
		return new Scene(m, devis.at("/jalons/0/id").asString(), devis.at("/jalons/1/id").asString(), awa, kofi);
	}

	private void demarrer(Scene s) throws Exception {
		assertThat(post("/api/missions/" + s.mission() + "/demarrer",
				"{\"version\":" + versionNum(s) + ",\"informations\":\"Les photos sont dans le dossier partagé.\"}", s.awa()))
			.hasStatus(HttpStatus.OK);
	}

	private MvcTestResult livrer(Scene s, String jalon) throws Exception {
		return post("/api/missions/" + s.mission() + "/jalons/" + jalon + "/livrer",
				"{\"version\":" + versionNum(s) + ",\"message\":\"Le catalogue est en ligne, à tester.\",\"liens\":[\"https://boutique.example.org\"]}",
				s.kofi());
	}

	private MvcTestResult jalon(Scene s, String jalon, String action, String corps, UtilisateurConnecte qui) throws Exception {
		return post("/api/missions/" + s.mission() + "/jalons/" + jalon + "/" + action, "{\"version\":" + versionNum(s) + corps + "}", qui);
	}

	private int versionNum(Scene s) throws Exception {
		return corps(lire("/api/missions/" + s.mission(), s.awa())).get("version").asInt();
	}

	private String version(String mission, UtilisateurConnecte qui) throws Exception {
		return "{\"version\":" + corps(lire("/api/missions/" + mission, qui)).get("version").asInt() + "}";
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

	private JsonNode mission(Scene s) throws Exception {
		return corps(lire("/api/missions/" + s.mission(), s.awa()));
	}

	// ---------------------------------------------------------------- démarrer et livrer

	@Test
	void on_ne_livre_pas_avant_le_demarrage() throws Exception {
		Scene s = scene();
		assertThat(corps(lire("/api/missions/" + s.mission(), s.awa())).at("/moi/actions").toString()).contains("demarrer");
		MvcTestResult r = livrer(s, s.premier());
		assertThat(r).hasStatus(HttpStatus.CONFLICT);
		assertThat(corps(r).get("code").asString()).isEqualTo("etat_invalide");
	}

	@Test
	void le_delai_commence_au_demarrage() throws Exception {
		Scene s = scene();
		assertThat(mission(s).at("/travail/devis/jalons/0/echeance").isNull()).isTrue();
		demarrer(s);
		JsonNode m = mission(s);
		assertThat(m.get("statut").asString()).isEqualTo("en_cours");
		Instant demarre = Instant.parse(m.at("/travail/demarreLe").asString());
		assertThat(Instant.parse(m.at("/travail/devis/jalons/0/echeance").asString())).isEqualTo(demarre.plus(Duration.ofDays(10)));
		assertThat(Instant.parse(m.at("/travail/devis/jalons/1/echeance").asString())).isEqualTo(demarre.plus(Duration.ofDays(21)));
		assertThat(m.at("/travail/informationsDepart").asString()).startsWith("Les photos");
		assertThat(corps(lire("/api/missions/" + s.mission(), s.kofi())).at("/moi/actions").toString()).contains("livrer");
	}

	@Test
	void le_client_seul_demarre_et_le_developpeur_seul_livre() throws Exception {
		Scene s = scene();
		assertThat(post("/api/missions/" + s.mission() + "/demarrer", "{\"version\":" + versionNum(s) + ",\"informations\":\"Rien\"}", s.kofi()))
			.hasStatus(HttpStatus.FORBIDDEN);
		demarrer(s);
		assertThat(post("/api/missions/" + s.mission() + "/jalons/" + s.premier() + "/livrer",
				"{\"version\":" + versionNum(s) + ",\"message\":\"Je livre à la place de Kofi.\"}", s.awa())).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(post("/api/missions/" + s.mission() + "/jalons/" + s.premier() + "/livrer",
				"{\"version\":" + versionNum(s) + ",\"message\":\"Le catalogue est en ligne.\",\"liens\":[\"http://interne\"]}", s.kofi()))
			.hasStatus(HttpStatus.BAD_REQUEST);
	}

	// ---------------------------------------------------------------- accepter et retoucher

	@Test
	void une_retouche_dans_la_limite_puis_une_de_trop() throws Exception {
		Scene s = scene();
		demarrer(s);
		assertThat(livrer(s, s.premier())).hasStatus(HttpStatus.OK);
		MvcTestResult retouche = jalon(s, s.premier(), "demander-retouche", ",\"raison\":\"Les prix n'apparaissent pas sur mobile.\"", s.awa());
		assertThat(retouche).hasStatus(HttpStatus.OK);
		JsonNode m = corps(retouche);
		assertThat(m.at("/travail/retouchesRestantes").asInt()).isZero();
		assertThat(m.at("/travail/devis/jalons/0/statut").asString()).isEqualTo("en_cours");
		assertThat(m.at("/travail/devis/jalons/0/livraisons/0/retouche").asString()).startsWith("Les prix");

		assertThat(livrer(s, s.premier())).hasStatus(HttpStatus.OK);
		MvcTestResult deTrop = jalon(s, s.premier(), "demander-retouche", ",\"raison\":\"Encore un petit changement.\"", s.awa());
		assertThat(deTrop).hasStatus(HttpStatus.CONFLICT);
		assertThat(corps(deTrop).get("code").asString()).isEqualTo("retouches_epuisees");
	}

	@Test
	void le_dernier_jalon_accepte_termine_la_mission() throws Exception {
		Scene s = scene();
		demarrer(s);
		livrer(s, s.premier());
		JsonNode livre = mission(s);
		Instant livreLe = Instant.parse(livre.at("/travail/devis/jalons/0/livraisons/0/livreeLe").asString());
		assertThat(Instant.parse(livre.at("/travail/devis/jalons/0/acceptationAutomatiqueLe").asString())).isEqualTo(livreLe.plus(Duration.ofHours(72)));

		assertThat(jalon(s, s.premier(), "accepter", "", s.awa())).hasStatus(HttpStatus.OK);
		assertThat(mission(s).get("statut").asString()).isEqualTo("en_cours");
		livrer(s, s.second());
		MvcTestResult fin = jalon(s, s.second(), "accepter", "", s.awa());
		assertThat(fin).hasStatus(HttpStatus.OK);
		assertThat(corps(fin).get("statut").asString()).isEqualTo("terminee");
		assertThat(corps(fin).at("/travail/devis/jalons/1/acceptationAutomatique").asBoolean()).isFalse();
	}

	@Test
	void une_version_perimee_est_refusee() throws Exception {
		Scene s = scene();
		demarrer(s);
		int perimee = versionNum(s) - 1;
		MvcTestResult r = post("/api/missions/" + s.mission() + "/jalons/" + s.premier() + "/livrer",
				"{\"version\":" + perimee + ",\"message\":\"Le catalogue est en ligne.\"}", s.kofi());
		assertThat(r).hasStatus(HttpStatus.CONFLICT);
		assertThat(corps(r).get("code").asString()).isEqualTo("modification_concurrente");
	}

	// ---------------------------------------------------------------- acceptation automatique

	@Test
	void sans_reponse_72_heures_apres_la_livraison_le_jalon_est_accepte() throws Exception {
		Scene s = scene();
		demarrer(s);
		livrer(s, s.premier());
		Instant livreLe = Instant.now().minus(Duration.ofHours(72)).minusSeconds(1).truncatedTo(java.time.temporal.ChronoUnit.MILLIS);
		base.sql("UPDATE jalon_mission SET livre_le = :t WHERE id = :j").param("t", Timestamp.from(livreLe)).param("j", UUID.fromString(s.premier())).update();

		livraisons.accepterAutomatiquement();
		JsonNode j = mission(s).at("/travail/devis/jalons/0");
		assertThat(j.get("statut").asString()).isEqualTo("accepte");
		assertThat(j.get("acceptationAutomatique").asBoolean()).isTrue();
		assertThat(Instant.parse(j.get("accepteLe").asString())).isEqualTo(livreLe.plus(Duration.ofHours(72)));
	}

	@Test
	void l_acceptation_automatique_ne_passe_qu_une_fois_en_parallele() throws Exception {
		Scene s = scene();
		demarrer(s);
		livrer(s, s.premier());
		base.sql("UPDATE jalon_mission SET livre_le = :t WHERE id = :j")
			.param("t", Timestamp.from(Instant.now().minus(Duration.ofHours(73)))).param("j", UUID.fromString(s.premier())).update();
		int versionAvant = versionNum(s);

		List<Callable<Integer>> taches = List.of(livraisons::accepterAutomatiquement, livraisons::accepterAutomatiquement);
		List<Integer> acceptes = new ArrayList<>();
		try (var executeur = Executors.newFixedThreadPool(2)) {
			for (Future<Integer> f : executeur.invokeAll(taches)) {
				acceptes.add(f.get());
			}
		}
		// D'autres tests peuvent avoir laissé des jalons livrés : on compte seulement que celui-ci n'est accepté qu'une fois.
		assertThat(versionNum(s)).isEqualTo(versionAvant + 1);
		assertThat(mission(s).at("/travail/devis/jalons/0/statut").asString()).isEqualTo("accepte");
	}

	// ---------------------------------------------------------------- paiement

	@Test
	void le_paiement_se_declare_dans_l_ordre() throws Exception {
		Scene s = scene();
		demarrer(s);
		assertThat(jalon(s, s.premier(), "declarer-paiement", ",\"sens\":\"paye\"", s.awa())).hasStatus(HttpStatus.CONFLICT);
		livrer(s, s.premier());
		MvcTestResult tropTot = jalon(s, s.premier(), "declarer-paiement", ",\"sens\":\"recu\"", s.kofi());
		assertThat(tropTot).hasStatus(HttpStatus.CONFLICT);
		assertThat(corps(tropTot).get("code").asString()).isEqualTo("etat_invalide");
		assertThat(jalon(s, s.premier(), "declarer-paiement", ",\"sens\":\"recu\"", s.awa())).hasStatus(HttpStatus.FORBIDDEN);

		assertThat(jalon(s, s.premier(), "declarer-paiement", ",\"sens\":\"paye\"", s.awa())).hasStatus(HttpStatus.OK);
		assertThat(corps(lire("/api/missions/" + s.mission(), s.kofi())).at("/moi/actions").toString()).contains("declarer_recu");
		MvcTestResult recu = jalon(s, s.premier(), "declarer-paiement", ",\"sens\":\"recu\"", s.kofi());
		assertThat(recu).hasStatus(HttpStatus.OK);
		assertThat(corps(recu).at("/travail/devis/jalons/0/paiement").asString()).isEqualTo("declare_recu");
	}

}
