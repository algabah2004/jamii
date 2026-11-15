package dev.jamii.api.demandes;

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

import org.junit.jupiter.api.BeforeEach;
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

/**
 * Publier sa demande (contracts/demandes.yaml v0.3, `POST /api/demandes/{id}/publication`) : l'auteur relit son
 * cahier des charges, choisit le résumé que la communauté verra, et l'événement public `DemandePubliee` part.
 */
@SpringBootTest
@AutoConfigureMockMvc
@RecordApplicationEvents
@Import({ TestcontainersConfiguration.class, DemandesTest.IaDeTest.class })
class PublicationDemandesTest {

	private static final String BESOIN = "{\"titre\":\"Boutique de couture en ligne\",\"description\":\"Je vends mes créations et je veux que les clients commandent sans passer par WhatsApp.\",\"dureeSemaines\":8,\"ville\":\"Thiès\",\"pays\":\"SN\"}";

	private static final String RESUME = "Une boutique en ligne simple pour vendre les créations d'un atelier de couture.";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient base;

	@Autowired
	JsonMapper json;

	@Autowired
	ApplicationEvents evenements;

	@BeforeEach
	void viderLIa() {
		DemandesTest.IA.reinitialiser();
	}

	// ---------------------------------------------------------------- aides

	private UtilisateurConnecte utilisateur(String nom) {
		UUID id = UUID.randomUUID();
		Timestamp t = Timestamp.from(Instant.now());
		base.sql("INSERT INTO utilisateur (id, nom, email, cree_le, mis_a_jour_le) VALUES (:id, :nom, :email, :t, :t)")
			.param("id", id).param("nom", nom).param("email", nom + "." + id + "@secret.example").param("t", t).update();
		return new UtilisateurConnecte(id, Set.of(Role.MEMBRE));
	}

	/** Une demande dont le plan est prêt (`terminee`) ou non. */
	private String demande(UtilisateurConnecte auteur, boolean planPret) throws Exception {
		MvcTestResult cree = mvc.post().uri("/api/demandes").contentType(MediaType.APPLICATION_JSON).content(BESOIN)
			.with(csrf()).with(oauth2Login().oauth2User(auteur)).exchange();
		assertThat(cree).hasStatus(HttpStatus.CREATED);
		String id = cree.getResponse().getHeader("Location").substring("/api/demandes/".length());
		if (planPret) {
			JsonNode orientation = json.readTree("""
					{"resume":"Une boutique","secteur":"commerce","services":[],"developpeursNecessaires":true,
					 "competences":["vue","spring"],"plan":{"cahierDesCharges":{"resume":"Un résumé"},"jalons":[],"taches":[]}}""");
			DemandesTest.IA.etats.put(DemandesTest.IA.parCle.get(UUID.fromString(id)),
					new PlansIa.Etat("terminee", "ia", orientation, null));
		}
		return id;
	}

	private MvcTestResult publier(UtilisateurConnecte qui, String id, String corps) {
		return mvc.post().uri("/api/demandes/{id}/publication", id).contentType(MediaType.APPLICATION_JSON).content(corps)
			.with(csrf()).with(oauth2Login().oauth2User(qui)).exchange();
	}

	private static String resume(String texte) {
		return "{\"resume\":\"" + texte + "\"}";
	}

	private JsonNode corps(MvcTestResult r) throws Exception {
		return json.readTree(r.getResponse().getContentAsString());
	}

	private List<DemandePubliee> publiees(String id) {
		return evenements.stream(DemandePubliee.class).filter(e -> e.demandeId().toString().equals(id)).toList();
	}

	// ---------------------------------------------------------------- publier

	@Test
	void publier_une_demande_dont_le_plan_est_pret_envoie_l_evenement_public() throws Exception {
		UtilisateurConnecte awa = utilisateur("Awa");
		String id = demande(awa, true);

		MvcTestResult r = publier(awa, id, resume(RESUME));
		assertThat(r).hasStatus(HttpStatus.OK);
		JsonNode vue = corps(r);
		assertThat(vue.at("/publication/resume").asString()).isEqualTo(RESUME);
		assertThat(vue.at("/publication/publieeLe").isNull()).isFalse();

		List<DemandePubliee> envoyes = publiees(id);
		assertThat(envoyes).hasSize(1);
		DemandePubliee e = envoyes.getFirst();
		assertThat(e.auteurId()).isEqualTo(awa.id());
		assertThat(e.titre()).isEqualTo("Boutique de couture en ligne");
		assertThat(e.resume()).isEqualTo(RESUME);
		assertThat(e.secteur()).isEqualTo("commerce");
		assertThat(e.ville()).isEqualTo("Thiès");
		assertThat(e.pays()).isEqualTo("SN");
		assertThat(e.dureeSemaines()).isEqualTo(8);
		assertThat(e.competences()).containsExactly("vue", "spring");
		assertThat(e.toString()).as("jamais le texte d'origine ni de coordonnées").doesNotContain("WhatsApp").doesNotContain("@");
	}

	@Test
	void une_demande_non_publiee_n_a_pas_de_publication() throws Exception {
		UtilisateurConnecte awa = utilisateur("Awa");
		String id = demande(awa, true);
		JsonNode vue = corps(mvc.get().uri("/api/demandes/{id}", id).with(oauth2Login().oauth2User(awa)).exchange());
		assertThat(vue.get("publication").isNull()).isTrue();
	}

	@Test
	void la_publication_se_relit_ensuite() throws Exception {
		UtilisateurConnecte awa = utilisateur("Awa");
		String id = demande(awa, true);
		publier(awa, id, resume(RESUME));
		JsonNode vue = corps(mvc.get().uri("/api/demandes/{id}", id).with(oauth2Login().oauth2User(awa)).exchange());
		assertThat(vue.at("/publication/resume").asString()).isEqualTo(RESUME);
	}

	@Test
	void sans_plan_pret_on_ne_publie_pas() throws Exception {
		UtilisateurConnecte awa = utilisateur("Awa");
		String id = demande(awa, false);

		MvcTestResult r = publier(awa, id, resume(RESUME));
		assertThat(r).hasStatus(HttpStatus.CONFLICT);
		assertThat(corps(r).get("code").asString()).isEqualTo("plan_non_pret");
		assertThat(publiees(id)).isEmpty();
	}

	@Test
	void ia_en_panne_au_moment_de_publier_repond_plan_non_pret_sans_planter() throws Exception {
		UtilisateurConnecte awa = utilisateur("Awa");
		String id = demande(awa, false);
		DemandesTest.IA.enPanne = true;

		MvcTestResult r = publier(awa, id, resume(RESUME));
		assertThat(r).hasStatus(HttpStatus.CONFLICT);
		assertThat(corps(r).get("code").asString()).isEqualTo("plan_non_pret");
	}

	@Test
	void l_orientation_de_secours_se_lit_mais_ne_se_publie_pas() throws Exception {
		UtilisateurConnecte awa = utilisateur("Awa");
		String id = demande(awa, false);
		DemandesTest.IA.enPanne = true;
		// En panne depuis plus de 20 secondes : la lecture donne le secours, la publication reste refusée.
		base.sql("UPDATE demande SET cree_le = :t WHERE id = :i")
			.param("t", Timestamp.from(Instant.now().minusSeconds(180))).param("i", UUID.fromString(id)).update();
		JsonNode vue = corps(mvc.get().uri("/api/demandes/{id}", id).with(oauth2Login().oauth2User(awa)).exchange());
		assertThat(vue.at("/orientation/origine").asString()).isEqualTo("repli");

		MvcTestResult r = publier(awa, id, resume(RESUME));
		assertThat(r).hasStatus(HttpStatus.CONFLICT);
		assertThat(corps(r).get("code").asString()).isEqualTo("plan_non_pret");
		assertThat(publiees(id)).isEmpty();
	}

	@Test
	void publier_deux_fois_est_refuse_et_l_evenement_n_part_qu_une_fois() throws Exception {
		UtilisateurConnecte awa = utilisateur("Awa");
		String id = demande(awa, true);
		assertThat(publier(awa, id, resume(RESUME))).hasStatus(HttpStatus.OK);

		MvcTestResult encore = publier(awa, id, resume(RESUME));
		assertThat(encore).hasStatus(HttpStatus.CONFLICT);
		assertThat(corps(encore).get("code").asString()).isEqualTo("deja_publiee");
		assertThat(publiees(id)).hasSize(1);
	}

	@Test
	void deux_publications_simultanees_n_en_font_qu_une() throws Exception {
		UtilisateurConnecte awa = utilisateur("Awa");
		String id = demande(awa, true);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			Callable<HttpStatus> appel = () -> HttpStatus.valueOf(publier(awa, id, resume(RESUME)).getResponse().getStatus());
			List<HttpStatus> statuts = pool.invokeAll(List.of(appel, appel)).stream().map(f -> {
				try {
					return f.get();
				}
				catch (Exception e) {
					throw new IllegalStateException(e);
				}
			}).toList();
			assertThat(statuts).containsExactlyInAnyOrder(HttpStatus.OK, HttpStatus.CONFLICT);
		}
		finally {
			pool.shutdownNow();
		}
		assertThat(publiees(id)).hasSize(1);
	}

	// ---------------------------------------------------------------- droits et validation

	@Test
	void seul_l_auteur_publie_les_autres_recoivent_404() throws Exception {
		String id = demande(utilisateur("Awa"), true);
		assertThat(publier(utilisateur("Kofi"), id, resume(RESUME))).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(publier(utilisateur("Kofi"), UUID.randomUUID().toString(), resume(RESUME))).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(publiees(id)).isEmpty();
	}

	@Test
	void sans_connexion_401_et_sans_jeton_csrf_403() throws Exception {
		UtilisateurConnecte awa = utilisateur("Awa");
		String id = demande(awa, true);
		assertThat(mvc.post().uri("/api/demandes/{id}/publication", id).contentType(MediaType.APPLICATION_JSON)
			.content(resume(RESUME)).with(csrf()).exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.post().uri("/api/demandes/{id}/publication", id).contentType(MediaType.APPLICATION_JSON)
			.content(resume(RESUME)).with(oauth2Login().oauth2User(awa)).exchange()).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void un_resume_trop_court_ou_trop_long_ou_absent_est_refuse_avec_le_champ() throws Exception {
		UtilisateurConnecte awa = utilisateur("Awa");
		String id = demande(awa, true);
		for (String corps : List.of(resume("trop court"), resume("x".repeat(601)), "{}", resume("   "))) {
			MvcTestResult r = publier(awa, id, corps);
			assertThat(r).hasStatus(HttpStatus.BAD_REQUEST);
			assertThat(corps(r).get("erreurs").toString()).contains("resume");
		}
		assertThat(publiees(id)).isEmpty();
	}

	@Test
	void le_resume_public_ne_garde_ni_e_mail_ni_telephone() throws Exception {
		UtilisateurConnecte awa = utilisateur("Awa");
		String id = demande(awa, true);

		MvcTestResult r = publier(awa, id,
				resume("Une boutique de couture. Écrivez-moi à awa@example.com ou au +221 77 123 45 67 pour en parler."));
		assertThat(r).hasStatus(HttpStatus.OK);
		String publie = corps(r).at("/publication/resume").asString();
		assertThat(publie).doesNotContain("awa@example.com").doesNotContain("77 123 45 67");
		assertThat(publiees(id).getFirst().resume()).isEqualTo(publie);
	}

	@Test
	void la_publication_fait_partie_de_l_export_du_compte() throws Exception {
		UtilisateurConnecte awa = utilisateur("Awa");
		String id = demande(awa, true);
		publier(awa, id, resume(RESUME));

		MvcTestResult export = mvc.get().uri("/api/me/export").with(oauth2Login().oauth2User(awa)).exchange();
		assertThat(export).hasStatus(HttpStatus.OK);
		JsonNode demandes = corps(export).at("/demandes");
		assertThat(demandes).hasSize(1);
		assertThat(demandes.get(0).at("/publication/resume").asString()).isEqualTo(RESUME);
	}

}
