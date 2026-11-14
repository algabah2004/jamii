package dev.jamii.api.missions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;

import java.sql.Timestamp;
import java.time.Instant;
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
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import dev.jamii.api.TestcontainersConfiguration;
import dev.jamii.api.auth.Role;
import dev.jamii.api.auth.UtilisateurConnecte;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Candidater à une mission, la retenir, se retirer (contracts/missions.yaml, tag « propositions »), sur un vrai PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
@Import(TestcontainersConfiguration.class)
class PropositionsTest {

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

	/** Une mission déjà publiée, insérée directement : la limite de 5 publications par jour ne gêne pas les tests. */
	private String mission(UtilisateurConnecte client, String visibilite) {
		UUID id = UUID.randomUUID();
		Timestamp t = Timestamp.from(Instant.now());
		base.sql("""
				INSERT INTO mission (id, client_id, titre, besoin, competences, visibilite, statut, cree_le, publiee_le)
				VALUES (:id, :c, 'Boutique en ligne', 'Montrer nos modèles avec photos et prix, recevoir les commandes.',
				        ARRAY['vue'], :v, 'publiee', :t, :t)
				""").param("id", id).param("c", client.id()).param("v", visibilite).param("t", t).update();
		return id.toString();
	}

	private static String proposition(String message, long centimes, String devise, int delai) {
		return """
				{"message":"%s","prix":{"centimes":%d,"devise":"%s"},"delaiJours":%d}""".formatted(message, centimes, devise, delai);
	}

	private static final String OFFRE = proposition("Je fais ce genre de boutique depuis deux ans, avec le paiement Wave.", 400000, "XOF", 21);

	private MvcTestResult envoyer(String mission, String corps, UtilisateurConnecte qui) {
		return mvc.post().uri("/api/missions/" + mission + "/propositions").contentType(MediaType.APPLICATION_JSON).content(corps)
			.with(csrf()).with(oauth2Login().oauth2User(qui)).exchange();
	}

	private MvcTestResult action(String chemin, String corps, UtilisateurConnecte qui) {
		return mvc.post().uri(chemin).contentType(MediaType.APPLICATION_JSON).content(corps)
			.with(csrf()).with(oauth2Login().oauth2User(qui)).exchange();
	}

	private MvcTestResult lire(String uri, UtilisateurConnecte qui) {
		return qui == null ? mvc.get().uri(uri).exchange() : mvc.get().uri(uri).with(oauth2Login().oauth2User(qui)).exchange();
	}

	private JsonNode corps(MvcTestResult r) throws Exception {
		return json.readTree(r.getResponse().getContentAsString());
	}

	private String proposer(String mission, UtilisateurConnecte dev) throws Exception {
		MvcTestResult r = envoyer(mission, OFFRE, dev);
		assertThat(r).hasStatus(HttpStatus.CREATED);
		return corps(r).get("id").asString();
	}

	private int version(String mission, UtilisateurConnecte qui) throws Exception {
		return corps(lire("/api/missions/" + mission, qui)).get("version").asInt();
	}

	// ---------------------------------------------------------------- proposer

	@Test
	void un_developpeur_propose_et_le_client_est_prevenu_par_un_evenement() throws Exception {
		UtilisateurConnecte awa = utilisateur("awa");
		UtilisateurConnecte kofi = utilisateur("kofi");
		String m = mission(awa, "publique");

		MvcTestResult r = envoyer(m, OFFRE, kofi);
		assertThat(r).hasStatus(HttpStatus.CREATED);
		JsonNode p = corps(r);
		assertThat(r.getResponse().getHeader("Location")).isEqualTo("/api/missions/" + m + "/propositions/" + p.get("id").asString());
		assertThat(p.get("statut").asString()).isEqualTo("envoyee");
		assertThat(p.at("/developpeur/nom").asString()).isEqualTo("kofi");
		assertThat(p.at("/prix/centimes").asLong()).isEqualTo(400000);
		assertThat(p.toString()).doesNotContain("@secret.example");

		List<PropositionRecue> envoyes = evenements.stream(PropositionRecue.class).filter(e -> e.missionId().toString().equals(m)).toList();
		assertThat(envoyes).hasSize(1);
		assertThat(envoyes.getFirst().clientId()).isEqualTo(awa.id());
		assertThat(envoyes.getFirst().developpeurId()).isEqualTo(kofi.id());

		assertThat(corps(lire("/api/missions/" + m, awa)).get("nombrePropositions").asInt()).isEqualTo(1);
		JsonNode vue = corps(lire("/api/missions/" + m, kofi));
		assertThat(vue.at("/moi/role").asString()).isEqualTo("candidat");
		assertThat(vue.at("/moi/actions")).isEmpty();
	}

	@Test
	void deux_propositions_du_meme_developpeur_sont_refusees() throws Exception {
		String m = mission(utilisateur("awa"), "publique");
		UtilisateurConnecte kofi = utilisateur("kofi");
		proposer(m, kofi);

		MvcTestResult r = envoyer(m, OFFRE, kofi);
		assertThat(r).hasStatus(HttpStatus.CONFLICT);
		assertThat(corps(r).get("code").asString()).isEqualTo("deja_candidat");
	}

	@Test
	void le_client_ne_candidate_pas_a_sa_mission() throws Exception {
		UtilisateurConnecte awa = utilisateur("awa");
		MvcTestResult r = envoyer(mission(awa, "publique"), OFFRE, awa);
		assertThat(r).hasStatus(HttpStatus.CONFLICT);
		assertThat(corps(r).get("code").asString()).isEqualTo("propre_mission");
	}

	@Test
	void on_ne_propose_que_sur_une_mission_ouverte_et_visible() throws Exception {
		UtilisateurConnecte awa = utilisateur("awa");
		UtilisateurConnecte kofi = utilisateur("kofi");
		String fermee = mission(awa, "publique");
		base.sql("UPDATE mission SET statut = 'fermee' WHERE id = :id").param("id", UUID.fromString(fermee)).update();
		MvcTestResult r = envoyer(fermee, OFFRE, kofi);
		assertThat(r).hasStatus(HttpStatus.CONFLICT);
		assertThat(corps(r).get("code").asString()).isEqualTo("etat_invalide");

		String brouillon = mission(awa, "publique");
		base.sql("UPDATE mission SET statut = 'brouillon', publiee_le = NULL WHERE id = :id").param("id", UUID.fromString(brouillon)).update();
		assertThat(envoyer(brouillon, OFFRE, kofi)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(envoyer(UUID.randomUUID().toString(), OFFRE, kofi)).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void une_mission_sur_invitation_n_accepte_que_les_invites() throws Exception {
		UtilisateurConnecte awa = utilisateur("awa");
		UtilisateurConnecte kofi = utilisateur("kofi");
		UtilisateurConnecte amina = utilisateur("amina");
		String m = mission(awa, "invitation");
		base.sql("INSERT INTO invitation_mission (mission_id, utilisateur_id, cree_le) VALUES (:m, :u, now())")
			.param("m", UUID.fromString(m)).param("u", kofi.id()).update();

		assertThat(envoyer(m, OFFRE, amina)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(envoyer(m, OFFRE, kofi)).hasStatus(HttpStatus.CREATED);
	}

	@Test
	void le_client_voit_toutes_les_propositions_un_developpeur_seulement_la_sienne() throws Exception {
		UtilisateurConnecte awa = utilisateur("awa");
		UtilisateurConnecte kofi = utilisateur("kofi");
		UtilisateurConnecte yann = utilisateur("yann");
		String m = mission(awa, "publique");
		String deKofi = proposer(m, kofi);
		String deYann = proposer(m, yann);

		String duClient = corps(lire("/api/missions/" + m + "/propositions", awa)).get("elements").toString();
		assertThat(duClient).contains(deKofi, deYann);

		JsonNode page = corps(lire("/api/missions/" + m + "/propositions", kofi));
		assertThat(page.get("elements")).hasSize(1);
		assertThat(page.get("elements").get(0).get("id").asString()).isEqualTo(deKofi);
		assertThat(page.get("curseurSuivant").isNull()).isTrue();

		assertThat(lire("/api/missions/" + m + "/propositions", null)).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(lire("/api/missions/" + m + "/propositions", utilisateur("inconnu")).getResponse().getContentAsString())
			.as("sans proposition, rien à voir").contains("\"elements\":[]");
	}

	@Test
	void les_champs_en_erreur_sont_signales_ensemble() throws Exception {
		UtilisateurConnecte kofi = utilisateur("kofi");
		String m = mission(utilisateur("awa"), "publique");
		MvcTestResult r = envoyer(m, proposition("court", -5, "xof", 0), kofi);
		assertThat(r).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(corps(r).get("erreurs").toString()).contains("message", "prix", "delaiJours");
	}

	@Test
	void plus_de_vingt_propositions_par_jour_repondent_429() throws Exception {
		UtilisateurConnecte kofi = utilisateur("kofi");
		for (int i = 0; i < 20; i++) {
			assertThat(envoyer(mission(utilisateur("client" + i), "publique"), OFFRE, kofi)).hasStatus(HttpStatus.CREATED);
		}
		String vingtEtUnieme = mission(utilisateur("dernier"), "publique");
		MvcTestResult r = envoyer(vingtEtUnieme, OFFRE, kofi);
		assertThat(r).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(Long.parseLong(r.getResponse().getHeader("Retry-After"))).isBetween(1L, 86_400L);
		assertThat(corps(r).get("code").asString()).isEqualTo("limite_atteinte");
		assertThat(corps(lire("/api/missions/" + vingtEtUnieme, utilisateur("autre"))).get("nombrePropositions").asInt())
			.as("rien n'est enregistré à moitié").isZero();
	}

	// ---------------------------------------------------------------- retenir et retirer

	@Test
	void le_client_retient_une_proposition_a_la_fois() throws Exception {
		UtilisateurConnecte awa = utilisateur("awa");
		UtilisateurConnecte kofi = utilisateur("kofi");
		UtilisateurConnecte yann = utilisateur("yann");
		String m = mission(awa, "publique");
		String deKofi = proposer(m, kofi);
		String deYann = proposer(m, yann);
		int v = version(m, awa);

		assertThat(action("/api/missions/" + m + "/propositions/" + deKofi + "/retenir", "{\"version\":" + v + "}", awa)).hasStatus(HttpStatus.OK);
		assertThat(action("/api/missions/" + m + "/propositions/" + deYann + "/retenir", "{\"version\":" + v + "}", awa)).hasStatus(HttpStatus.OK);

		JsonNode page = corps(lire("/api/missions/" + m + "/propositions", awa));
		String statuts = page.get("elements").toString();
		assertThat(page.get("elements")).hasSize(2);
		for (JsonNode p : page.get("elements")) {
			assertThat(p.get("statut").asString()).isEqualTo(p.get("id").asString().equals(deYann) ? "retenue" : "envoyee");
		}
		assertThat(statuts).doesNotContain("acceptee");
		assertThat(corps(lire("/api/missions/" + m, kofi)).at("/moi/actions")).isEmpty();
		assertThat(corps(lire("/api/missions/" + m, yann)).at("/moi/actions").toString()).contains("envoyer_devis");
	}

	@Test
	void seul_le_client_retient_et_avec_la_bonne_version() throws Exception {
		UtilisateurConnecte awa = utilisateur("awa");
		UtilisateurConnecte kofi = utilisateur("kofi");
		String m = mission(awa, "publique");
		String p = proposer(m, kofi);
		String chemin = "/api/missions/" + m + "/propositions/" + p + "/retenir";

		assertThat(action(chemin, "{\"version\":1}", kofi)).hasStatus(HttpStatus.FORBIDDEN);
		MvcTestResult perimee = action(chemin, "{\"version\":99}", awa);
		assertThat(perimee).hasStatus(HttpStatus.CONFLICT);
		assertThat(corps(perimee).get("code").asString()).isEqualTo("modification_concurrente");
		assertThat(action("/api/missions/" + m + "/propositions/" + UUID.randomUUID() + "/retenir", "{\"version\":1}", awa)).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void un_developpeur_retire_sa_proposition_tant_que_la_mission_n_est_pas_attribuee() throws Exception {
		UtilisateurConnecte awa = utilisateur("awa");
		UtilisateurConnecte kofi = utilisateur("kofi");
		UtilisateurConnecte yann = utilisateur("yann");
		String m = mission(awa, "publique");
		String deKofi = proposer(m, kofi);

		assertThat(action("/api/missions/" + m + "/propositions/" + deKofi + "/retirer", "{}", yann)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(action("/api/missions/" + m + "/propositions/" + deKofi + "/retirer", "{}", kofi)).hasStatus(HttpStatus.NO_CONTENT);
		MvcTestResult encore = action("/api/missions/" + m + "/propositions/" + deKofi + "/retirer", "{}", kofi);
		assertThat(encore).hasStatus(HttpStatus.CONFLICT);
		assertThat(corps(encore).get("code").asString()).isEqualTo("etat_invalide");
		assertThat(corps(lire("/api/missions/" + m, awa)).get("nombrePropositions").asInt()).as("une proposition retirée ne compte plus").isZero();
	}

	@Test
	void fermer_la_mission_ecarte_les_propositions_en_cours() throws Exception {
		UtilisateurConnecte awa = utilisateur("awa");
		UtilisateurConnecte kofi = utilisateur("kofi");
		String m = mission(awa, "publique");
		String p = proposer(m, kofi);

		assertThat(action("/api/missions/" + m + "/fermer", "{\"version\":" + version(m, awa) + "}", awa)).hasStatus(HttpStatus.OK);
		JsonNode propositions = corps(lire("/api/missions/" + m + "/propositions", kofi)).get("elements");
		assertThat(propositions.get(0).get("id").asString()).isEqualTo(p);
		assertThat(propositions.get(0).get("statut").asString()).isEqualTo("non_retenue");
	}

}
