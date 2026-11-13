package dev.jamii.api.communautes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

import com.jayway.jsonpath.JsonPath;

import dev.jamii.api.TestcontainersConfiguration;
import dev.jamii.api.auth.Role;
import dev.jamii.api.auth.UtilisateurConnecte;

/** Le cœur de la démo : l'IA propose, un pair valide ; l'auteur accepte une réponse humaine. Sur un vrai PostgreSQL. */
@SpringBootTest(properties = "jamii.communautes.ia-reflexion=0s")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@RecordApplicationEvents
class ReponsesTest {

	private static final String QUESTION = """
			{"type":"question","titre":"Comment garder les données hors ligne ?","contenu":"Mon appli perd la liste des commandes quand le réseau coupe."}""";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient base;

	@Autowired
	GestionReponses reponses;

	@Autowired
	ApplicationEvents evenements;

	private UtilisateurConnecte utilisateur(String nom) {
		UUID id = UUID.randomUUID();
		Timestamp t = Timestamp.from(Instant.now());
		base.sql("INSERT INTO utilisateur (id, nom, email, avatar_url, cree_le, mis_a_jour_le) VALUES (:id, :nom, :email, 'https://avatars.example/x', :t, :t)")
			.param("id", id).param("nom", nom).param("email", id + "@example.org").param("t", t).update();
		return new UtilisateurConnecte(id, Set.of(Role.MEMBRE));
	}

	/** Une communauté propre au test, dont les personnes données sont membres. */
	private String communaute(UtilisateurConnecte... membres) {
		String slug = "test-" + UUID.randomUUID().toString().substring(0, 8);
		base.sql("INSERT INTO communaute (id, slug, nom, description, theme, pays, cree_le) VALUES (gen_random_uuid(), :s, 'Test', 'Pour les tests.', 'flutter', NULL, now())")
			.param("s", slug).update();
		for (UtilisateurConnecte m : membres) {
			mvc.post().uri("/api/communautes/{s}/adhesion", slug).with(csrf()).with(oauth2Login().oauth2User(m)).exchange();
		}
		return slug;
	}

	private MvcTestResult post(UtilisateurConnecte qui, String uri, String corps, Object... variables) {
		var requete = mvc.post().uri(uri, variables).with(csrf()).with(oauth2Login().oauth2User(qui));
		return corps == null ? requete.exchange() : requete.contentType(MediaType.APPLICATION_JSON).content(corps).exchange();
	}

	private String json(MvcTestResult r, String chemin) throws Exception {
		Object v = JsonPath.read(r.getResponse().getContentAsString(StandardCharsets.UTF_8), chemin);
		return v == null ? null : v.toString();
	}

	private String question(UtilisateurConnecte auteur, String slug) throws Exception {
		return json(post(auteur, "/api/communautes/{s}/publications", QUESTION, slug), "$.id");
	}

	/** Relit la question jusqu'à l'arrivée de la proposition de l'IA (en arrière-plan), au plus 5 secondes. */
	private MvcTestResult attendreIa(UtilisateurConnecte qui, String publication) throws Exception {
		for (int i = 0; i < 50; i++) {
			MvcTestResult lue = mvc.get().uri("/api/publications/{id}", publication).with(oauth2Login().oauth2User(qui)).exchange();
			if (!"en_attente".equals(json(lue, "$.propositionIa.statut"))) {
				return lue;
			}
			Thread.sleep(100);
		}
		throw new AssertionError("L'IA simulée n'a pas répondu en 5 secondes");
	}

	@Test
	void l_ia_propose_une_reponse_sans_auteur_puis_un_pair_la_valide() throws Exception {
		UtilisateurConnecte koffi = utilisateur("Koffi Yao");
		UtilisateurConnecte awa = utilisateur("Awa Diop");
		String slug = communaute(koffi, awa);
		String id = question(koffi, slug);

		MvcTestResult lue = attendreIa(awa, id);
		assertThat(lue).bodyJson().extractingPath("$.propositionIa.statut").isEqualTo("prete");
		assertThat(lue).bodyJson().extractingPath("$.reponses[0].proposeeParIa").isEqualTo(true);
		assertThat(lue).bodyJson().extractingPath("$.reponses[0].auteur").isNull();
		String reponseIa = json(lue, "$.reponses[0].id");

		MvcTestResult validee = post(awa, "/api/reponses/{id}/valider", null, reponseIa);

		assertThat(validee).hasStatusOk();
		assertThat(validee).bodyJson().extractingPath("$.reponseAccepteeId").isEqualTo(reponseIa);
		assertThat(validee).bodyJson().extractingPath("$.reponses[0].acceptee").isEqualTo(true);
		assertThat(validee).bodyJson().extractingPath("$.reponses[0].valideePar.nom").isEqualTo("Awa Diop");
		// Une réponse de l'IA ne compte sur le profil de personne, même validée.
		assertThat(evenements.stream(ReponseAcceptee.class)).isEmpty();
		assertThat(post(utilisateur("Chidi Okafor"), "/api/reponses/{id}/valider", null, reponseIa)).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void l_auteur_de_la_question_ne_valide_pas_la_reponse_de_l_ia() throws Exception {
		UtilisateurConnecte koffi = utilisateur("Koffi Yao");
		String id = question(koffi, communaute(koffi));
		String reponseIa = json(attendreIa(koffi, id), "$.reponses[0].id");

		MvcTestResult refus = post(koffi, "/api/reponses/{id}/valider", null, reponseIa);

		assertThat(refus).hasStatus(HttpStatus.CONFLICT);
		assertThat(refus).bodyJson().extractingPath("$.code").isEqualTo("propre_question");
		assertThat(post(koffi, "/api/reponses/{id}/accepter", null, reponseIa)).bodyJson().extractingPath("$.code").isEqualTo("proposee_par_ia");
	}

	@Test
	void l_auteur_accepte_une_reponse_humaine_qui_compte_sur_le_profil_de_son_auteur() throws Exception {
		UtilisateurConnecte koffi = utilisateur("Koffi Yao");
		UtilisateurConnecte wanjiru = utilisateur("Wanjiru Kamau");
		UtilisateurConnecte aya = utilisateur("Aya Kouassi");
		String slug = communaute(koffi, wanjiru);
		String id = question(koffi, slug);

		assertThat(post(aya, "/api/publications/{id}/reponses", "{\"contenu\":\"Essaie le cache local.\"}", id))
			.bodyJson().extractingPath("$.code").isEqualTo("pas_membre");
		MvcTestResult repondue = post(wanjiru, "/api/publications/{id}/reponses", "{\"contenu\":\"Garde une copie locale avec Hive.\"}", id);
		assertThat(repondue).hasStatus(HttpStatus.CREATED);
		assertThat(repondue).bodyJson().extractingPath("$.auteur.nom").isEqualTo("Wanjiru Kamau");
		String reponse = json(repondue, "$.id");

		assertThat(post(wanjiru, "/api/reponses/{id}/accepter", null, reponse)).bodyJson().extractingPath("$.code").isEqualTo("pas_auteur_question");
		MvcTestResult acceptee = post(koffi, "/api/reponses/{id}/accepter", null, reponse);

		assertThat(acceptee).hasStatusOk();
		assertThat(acceptee).bodyJson().extractingPath("$.reponses[0].id").isEqualTo(reponse);
		assertThat(acceptee).bodyJson().extractingPath("$.accepteeLe").isNotNull();
		assertThat(evenements.stream(ReponseAcceptee.class)).singleElement()
			.satisfies(e -> assertThat(e.auteurId()).isEqualTo(wanjiru.id()));
		assertThat(post(koffi, "/api/reponses/{id}/accepter", null, reponse)).bodyJson().extractingPath("$.code").isEqualTo("deja_acceptee");
	}

	@Test
	void on_n_accepte_pas_sa_propre_reponse_pour_se_donner_une_preuve() throws Exception {
		UtilisateurConnecte koffi = utilisateur("Koffi Yao");
		String id = question(koffi, communaute(koffi));
		String sienne = json(post(koffi, "/api/publications/{id}/reponses", "{\"contenu\":\"J'ai trouvé tout seul.\"}", id), "$.id");

		MvcTestResult refus = post(koffi, "/api/reponses/{id}/accepter", null, sienne);

		assertThat(refus).hasStatus(HttpStatus.CONFLICT);
		assertThat(refus).bodyJson().extractingPath("$.code").isEqualTo("propre_reponse");
		assertThat(evenements.stream(ReponseAcceptee.class)).isEmpty();
	}

	@Test
	void une_discussion_n_a_pas_de_reponse_acceptee() throws Exception {
		UtilisateurConnecte fatou = utilisateur("Fatou Ndiaye");
		UtilisateurConnecte chidi = utilisateur("Chidi Okafor");
		String slug = communaute(fatou, chidi);
		String id = json(post(fatou, "/api/communautes/{s}/publications", QUESTION.replace("\"question\"", "\"discussion\""), slug), "$.id");
		String reponse = json(post(chidi, "/api/publications/{id}/reponses", "{\"contenu\":\"Je viens vendredi !\"}", id), "$.id");

		assertThat(post(fatou, "/api/reponses/{id}/accepter", null, reponse)).bodyJson().extractingPath("$.code").isEqualTo("pas_une_question");
	}

	@Test
	void si_l_ia_tombe_la_question_reste_ouverte_aux_humains() throws Exception {
		UtilisateurConnecte koffi = utilisateur("Koffi Yao");
		String slug = communaute(koffi);
		// Insérée directement : l'IA simulée de l'application ne doit pas répondre pendant le test.
		UUID id = UUID.randomUUID();
		base.sql("""
				INSERT INTO publication (id, communaute_id, auteur_id, type, titre, contenu, proposition_ia, cree_le)
				SELECT :id, c.id, :u, 'question', 'Une question', 'Le détail de la question.', 'en_attente', now()
				FROM communaute c WHERE c.slug = :s
				""").param("id", id).param("u", koffi.id()).param("s", slug).update();
		var executeur = Executors.newSingleThreadExecutor();
		ReponsesIa enPanne = q -> {
			throw new IllegalStateException("service IA injoignable");
		};

		new PropositionsIa(enPanne, reponses, executeur).demander(id, new ReponsesIa.Question("t", "c", "flutter"));
		executeur.shutdown();
		assertThat(executeur.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

		MvcTestResult lue = mvc.get().uri("/api/publications/{id}", id).with(oauth2Login().oauth2User(koffi)).exchange();
		assertThat(lue).bodyJson().extractingPath("$.propositionIa.statut").isEqualTo("indisponible");
		assertThat(lue).bodyJson().extractingPath("$.reponses.length()").isEqualTo(0);
		// Une IA qui répond trop tard ne fait pas réapparaître de proposition.
		reponses.propositionPrete(id, "Trop tard", Instant.now());
		assertThat(base.sql("SELECT count(*) FROM reponse WHERE publication_id = :p").param("p", id).query(Long.class).single()).isZero();
	}

	@Test
	void une_proposition_bloquee_plus_de_deux_minutes_s_affiche_indisponible() {
		Instant maintenant = Instant.now();
		assertThat(PropositionsIa.statutAffiche("en_attente", maintenant.minus(Duration.ofMinutes(3)), maintenant)).isEqualTo("indisponible");
		assertThat(PropositionsIa.statutAffiche("en_attente", maintenant.minusSeconds(10), maintenant)).isEqualTo("en_attente");
		assertThat(PropositionsIa.statutAffiche("prete", maintenant.minus(Duration.ofHours(1)), maintenant)).isEqualTo("prete");
	}

	@Test
	void l_ia_simulee_ne_recoit_que_le_texte_et_repond_prudemment() {
		Optional<String> texte = new ReponsesIaSimulees(Duration.ZERO).proposer(new ReponsesIa.Question("Riverpod hors ligne", "…", "flutter"));
		assertThat(texte).hasValueSatisfying(t -> assertThat(t).contains("Riverpod hors ligne").contains("doit vérifier"));
	}

	@Test
	void au_dela_de_trente_reponses_par_heure_il_faut_attendre() throws Exception {
		UtilisateurConnecte qui = utilisateur("Aya Kouassi");
		String slug = communaute(qui);
		UUID id = UUID.fromString(question(qui, slug));
		for (int i = 0; i < GestionReponses.REPONSES_PAR_HEURE; i++) {
			base.sql("INSERT INTO reponse (id, publication_id, auteur_id, contenu, cree_le) VALUES (gen_random_uuid(), :p, :u, 'Déjà répondu.', :t)")
				.param("p", id).param("u", qui.id()).param("t", Timestamp.from(Instant.now().minus(Duration.ofMinutes(20)))).update();
		}

		MvcTestResult refus = post(qui, "/api/publications/{id}/reponses", "{\"contenu\":\"Encore une réponse.\"}", id);

		assertThat(refus).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(refus.getResponse().getHeader("Retry-After")).isNotBlank();
	}

}
