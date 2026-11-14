package dev.jamii.api.missions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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

/** Le devis d'une mission : l'envoyer, l'accepter, et ce que ça attribue (contracts/missions.yaml, tag « propositions »). */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
@Import(TestcontainersConfiguration.class)
class DevisTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient base;

	@Autowired
	JsonMapper json;

	@Autowired
	ApplicationEvents evenements;

	// ---------------------------------------------------------------- aides

	private UtilisateurConnecte utilisateur(String nom) {
		UUID id = UUID.randomUUID();
		Timestamp t = Timestamp.from(Instant.now());
		base.sql("INSERT INTO utilisateur (id, nom, email, cree_le, mis_a_jour_le) VALUES (:id, :nom, :email, :t, :t)")
			.param("id", id).param("nom", nom).param("email", nom + "." + id + "@secret.example").param("t", t).update();
		return new UtilisateurConnecte(id, Set.of(Role.MEMBRE));
	}

	private String mission(UtilisateurConnecte client) {
		UUID id = UUID.randomUUID();
		Timestamp t = Timestamp.from(Instant.now());
		base.sql("""
				INSERT INTO mission (id, client_id, titre, besoin, competences, visibilite, statut, cree_le, publiee_le)
				VALUES (:id, :c, 'Boutique en ligne', 'Montrer nos modèles avec photos et prix, recevoir les commandes.',
				        ARRAY['vue'], 'publique', 'publiee', :t, :t)
				""").param("id", id).param("c", client.id()).param("t", t).update();
		return id.toString();
	}

	private static final String OFFRE = """
			{"message":"Je fais ce genre de boutique depuis deux ans, avec le paiement Wave.","prix":{"centimes":400000,"devise":"XOF"},"delaiJours":21}""";

	private static String devis(long total, long premier, long second) {
		return """
				{"total":{"centimes":%d,"devise":"XOF"},"retouchesComprises":2,"conditions":"Hébergement non compris.",
				 "jalons":[{"titre":"Le catalogue","montant":{"centimes":%d,"devise":"XOF"},"delaiJours":10},
				           {"titre":"Les commandes","description":"WhatsApp et paiement","montant":{"centimes":%d,"devise":"XOF"},"delaiJours":21}]}"""
			.formatted(total, premier, second);
	}

	private static final String DEVIS = devis(400000, 150000, 250000);

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

	private String proposer(String mission, UtilisateurConnecte dev) throws Exception {
		MvcTestResult r = post("/api/missions/" + mission + "/propositions", OFFRE, dev);
		assertThat(r).hasStatus(HttpStatus.CREATED);
		return corps(r).get("id").asString();
	}

	private int version(String mission, UtilisateurConnecte qui) throws Exception {
		return corps(lire("/api/missions/" + mission, qui)).get("version").asInt();
	}

	private void retenir(String mission, String proposition, UtilisateurConnecte client) throws Exception {
		assertThat(post("/api/missions/" + mission + "/propositions/" + proposition + "/retenir",
				"{\"version\":" + version(mission, client) + "}", client)).hasStatus(HttpStatus.OK);
	}

	private String devisEnvoye(String mission, UtilisateurConnecte dev, String corps) throws Exception {
		MvcTestResult r = post("/api/missions/" + mission + "/devis", corps, dev);
		assertThat(r).hasStatus(HttpStatus.CREATED);
		return corps(r).get("id").asString();
	}

	// ---------------------------------------------------------------- envoyer

	@Test
	void le_developpeur_retenu_envoie_son_devis_et_le_client_le_voit() throws Exception {
		UtilisateurConnecte awa = utilisateur("awa");
		UtilisateurConnecte kofi = utilisateur("kofi");
		String m = mission(awa);
		retenir(m, proposer(m, kofi), awa);

		MvcTestResult r = post("/api/missions/" + m + "/devis", DEVIS, kofi);
		assertThat(r).hasStatus(HttpStatus.CREATED);
		JsonNode d = corps(r);
		assertThat(r.getResponse().getHeader("Location")).isEqualTo("/api/missions/" + m + "/devis/" + d.get("id").asString());
		assertThat(d.at("/total/centimes").asLong()).isEqualTo(400000);
		assertThat(d.get("accepteLe").isNull()).isTrue();
		assertThat(d.get("jalons")).hasSize(2);
		assertThat(d.at("/jalons/0/ordre").asInt()).isEqualTo(1);
		assertThat(d.at("/jalons/1/titre").asString()).isEqualTo("Les commandes");
		assertThat(d.at("/jalons/0/statut").asString()).isEqualTo("a_faire");
		assertThat(d.at("/jalons/0/paiement").asString()).isEqualTo("non_paye");
		assertThat(d.at("/jalons/0/echeance").isNull()).as("le délai commence au démarrage").isTrue();

		assertThat(evenements.stream(DevisEnvoye.class).filter(e -> e.missionId().toString().equals(m)).toList()).hasSize(1);

		JsonNode vueClient = corps(lire("/api/missions/" + m, awa));
		assertThat(vueClient.at("/travail/devis/id").asString()).isEqualTo(d.get("id").asString());
		assertThat(vueClient.at("/travail/developpeur/nom").asString()).isEqualTo("kofi");
		assertThat(vueClient.at("/moi/actions").toString()).contains("accepter_devis");
		assertThat(corps(lire("/api/missions/" + m, kofi)).at("/travail/devis/id").asString()).isEqualTo(d.get("id").asString());
	}

	@Test
	void seul_le_developpeur_retenu_envoie_un_devis() throws Exception {
		UtilisateurConnecte awa = utilisateur("awa");
		UtilisateurConnecte kofi = utilisateur("kofi");
		UtilisateurConnecte yann = utilisateur("yann");
		String m = mission(awa);
		retenir(m, proposer(m, kofi), awa);
		proposer(m, yann);

		assertThat(post("/api/missions/" + m + "/devis", DEVIS, yann)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(post("/api/missions/" + m + "/devis", DEVIS, awa)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(post("/api/missions/" + m + "/devis", DEVIS, utilisateur("passant"))).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(post("/api/missions/" + UUID.randomUUID() + "/devis", DEVIS, kofi)).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void le_devis_d_un_developpeur_non_retenu_est_refuse() throws Exception {
		UtilisateurConnecte awa = utilisateur("awa");
		UtilisateurConnecte kofi = utilisateur("kofi");
		String m = mission(awa);
		proposer(m, kofi);

		assertThat(post("/api/missions/" + m + "/devis", DEVIS, kofi)).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void la_somme_des_jalons_doit_egaler_le_total() throws Exception {
		UtilisateurConnecte awa = utilisateur("awa");
		UtilisateurConnecte kofi = utilisateur("kofi");
		String m = mission(awa);
		retenir(m, proposer(m, kofi), awa);

		MvcTestResult r = post("/api/missions/" + m + "/devis", devis(400000, 150000, 200000), kofi);
		assertThat(r).hasStatus(HttpStatus.CONFLICT);
		assertThat(corps(r).get("code").asString()).isEqualTo("somme_jalons_invalide");
	}

	@Test
	void le_devis_refuse_les_champs_invalides_et_les_devises_melangees() throws Exception {
		UtilisateurConnecte awa = utilisateur("awa");
		UtilisateurConnecte kofi = utilisateur("kofi");
		String m = mission(awa);
		retenir(m, proposer(m, kofi), awa);

		MvcTestResult vide = post("/api/missions/" + m + "/devis",
				"{\"total\":{\"centimes\":1,\"devise\":\"XOF\"},\"retouchesComprises\":11,\"jalons\":[]}", kofi);
		assertThat(vide).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(corps(vide).get("erreurs").toString()).contains("retouchesComprises", "jalons");

		MvcTestResult melange = post("/api/missions/" + m + "/devis", DEVIS.replaceFirst("\"devise\":\"XOF\"\\},\"delaiJours\":21", "\"devise\":\"EUR\"},\"delaiJours\":21"), kofi);
		assertThat(melange).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(corps(melange).get("erreurs").toString()).contains("devise");

		MvcTestResult mal = post("/api/missions/" + m + "/devis", DEVIS.replace("Le catalogue", "ab"), kofi);
		assertThat(mal).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(corps(mal).get("erreurs").toString()).contains("jalons[0].titre");
	}

	@Test
	void un_nouveau_devis_remplace_celui_qui_n_est_pas_encore_accepte() throws Exception {
		UtilisateurConnecte awa = utilisateur("awa");
		UtilisateurConnecte kofi = utilisateur("kofi");
		String m = mission(awa);
		retenir(m, proposer(m, kofi), awa);
		String premier = devisEnvoye(m, kofi, DEVIS);
		String second = devisEnvoye(m, kofi, devis(300000, 100000, 200000));

		JsonNode vue = corps(lire("/api/missions/" + m, awa));
		assertThat(vue.at("/travail/devis/id").asString()).isEqualTo(second);
		assertThat(vue.at("/travail/devis/total/centimes").asLong()).isEqualTo(300000);
		assertThat(post("/api/missions/" + m + "/devis/" + premier + "/accepter", "{\"version\":" + vue.get("version").asInt() + "}", awa))
			.as("l'ancien devis n'existe plus").hasStatus(HttpStatus.NOT_FOUND);
	}

	// ---------------------------------------------------------------- accepter

	@Test
	void accepter_le_devis_attribue_la_mission_et_ecarte_les_autres_propositions() throws Exception {
		UtilisateurConnecte awa = utilisateur("awa");
		UtilisateurConnecte kofi = utilisateur("kofi");
		UtilisateurConnecte yann = utilisateur("yann");
		UtilisateurConnecte amina = utilisateur("amina");
		String m = mission(awa);
		String deKofi = proposer(m, kofi);
		proposer(m, yann);
		proposer(m, amina);
		retenir(m, deKofi, awa);
		String d = devisEnvoye(m, kofi, DEVIS);
		int v = version(m, awa);

		MvcTestResult r = post("/api/missions/" + m + "/devis/" + d + "/accepter", "{\"version\":" + v + "}", awa);
		assertThat(r).hasStatus(HttpStatus.OK);
		JsonNode vue = corps(r);
		assertThat(vue.get("statut").asString()).isEqualTo("attribuee");
		assertThat(vue.get("version").asInt()).isEqualTo(v + 1);
		assertThat(vue.at("/travail/devis/accepteLe").isNull()).isFalse();
		assertThat(vue.at("/travail/retouchesRestantes").asInt()).isEqualTo(2);
		assertThat(vue.at("/travail/informationsDepart").isNull()).isTrue();

		JsonNode propositions = corps(lire("/api/missions/" + m + "/propositions", awa)).get("elements");
		for (JsonNode p : propositions) {
			assertThat(p.get("statut").asString()).isEqualTo(p.get("developpeur").get("nom").asString().equals("kofi") ? "acceptee" : "non_retenue");
		}

		List<MissionAttribuee> envoyes = evenements.stream(MissionAttribuee.class).filter(e -> e.missionId().toString().equals(m)).toList();
		assertThat(envoyes).hasSize(1);
		assertThat(envoyes.getFirst().developpeurId()).isEqualTo(kofi.id());
		assertThat(envoyes.getFirst().nonRetenus()).containsExactlyInAnyOrder(yann.id(), amina.id());

		assertThat(corps(lire("/api/missions/" + m, kofi)).at("/moi/role").asString()).isEqualTo("developpeur_retenu");
		assertThat(corps(lire("/api/missions/" + m, yann)).get("travail").isNull()).as("un candidat écarté ne voit pas le travail").isTrue();
		assertThat(post("/api/missions/" + m + "/propositions", OFFRE, utilisateur("tardif")))
			.as("une mission attribuée n'est plus visible du public").hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void seul_le_client_accepte_avec_la_bonne_version() throws Exception {
		UtilisateurConnecte awa = utilisateur("awa");
		UtilisateurConnecte kofi = utilisateur("kofi");
		String m = mission(awa);
		retenir(m, proposer(m, kofi), awa);
		String d = devisEnvoye(m, kofi, DEVIS);
		String chemin = "/api/missions/" + m + "/devis/" + d + "/accepter";

		assertThat(post(chemin, "{\"version\":" + version(m, awa) + "}", kofi)).hasStatus(HttpStatus.FORBIDDEN);
		MvcTestResult perimee = post(chemin, "{\"version\":99}", awa);
		assertThat(perimee).hasStatus(HttpStatus.CONFLICT);
		assertThat(corps(perimee).get("code").asString()).isEqualTo("modification_concurrente");
		assertThat(corps(lire("/api/missions/" + m, awa)).get("statut").asString()).isEqualTo("publiee");
	}

	@Test
	void deux_acceptations_simultanees_n_en_font_qu_une() throws Exception {
		UtilisateurConnecte awa = utilisateur("awa");
		UtilisateurConnecte kofi = utilisateur("kofi");
		String m = mission(awa);
		retenir(m, proposer(m, kofi), awa);
		String d = devisEnvoye(m, kofi, DEVIS);
		String corps = "{\"version\":" + version(m, awa) + "}";

		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			Callable<Integer> appel = () -> post("/api/missions/" + m + "/devis/" + d + "/accepter", corps, awa).getResponse().getStatus();
			List<Integer> statuts = pool.invokeAll(List.of(appel, appel)).stream().map(f -> {
				try {
					return f.get();
				}
				catch (Exception e) {
					throw new IllegalStateException(e);
				}
			}).toList();
			assertThat(statuts).containsExactlyInAnyOrder(200, 409);
		}
		finally {
			pool.shutdownNow();
		}
		assertThat(evenements.stream(MissionAttribuee.class).filter(e -> e.missionId().toString().equals(m)).toList()).hasSize(1);
	}

	@Test
	void on_n_envoie_plus_de_devis_une_fois_la_mission_attribuee() throws Exception {
		UtilisateurConnecte awa = utilisateur("awa");
		UtilisateurConnecte kofi = utilisateur("kofi");
		String m = mission(awa);
		retenir(m, proposer(m, kofi), awa);
		String d = devisEnvoye(m, kofi, DEVIS);
		post("/api/missions/" + m + "/devis/" + d + "/accepter", "{\"version\":" + version(m, awa) + "}", awa);

		MvcTestResult r = post("/api/missions/" + m + "/devis", DEVIS, kofi);
		assertThat(r).hasStatus(HttpStatus.CONFLICT);
		assertThat(corps(r).get("code").asString()).isEqualTo("etat_invalide");
	}

}
