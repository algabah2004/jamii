package dev.jamii.api.communautes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;

import java.sql.Timestamp;
import java.time.Duration;
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
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import dev.jamii.api.TestcontainersConfiguration;
import dev.jamii.api.auth.Role;
import dev.jamii.api.auth.UtilisateurConnecte;

/** Les routes des communautés et des publications (contracts/communautes.yaml), sur un vrai PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CommunautesTest {

	private static final String QUESTION = """
			{"type":"question","titre":"Comment garder les données hors ligne ?","contenu":"Mon appli perd la liste des commandes quand le réseau coupe."}""";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient base;

	private UtilisateurConnecte utilisateur(String nom) {
		UUID id = UUID.randomUUID();
		Timestamp t = Timestamp.from(Instant.now());
		base.sql("INSERT INTO utilisateur (id, nom, email, avatar_url, cree_le, mis_a_jour_le) VALUES (:id, :nom, :email, 'https://avatars.example/x', :t, :t)")
			.param("id", id).param("nom", nom).param("email", id + "@example.org").param("t", t).update();
		return new UtilisateurConnecte(id, Set.of(Role.MEMBRE));
	}

	/** Une communauté propre à chaque test : les tests ne dépendent pas de leur ordre. */
	private String communaute(String pays) {
		String slug = "test-" + UUID.randomUUID().toString().substring(0, 8);
		base.sql("INSERT INTO communaute (id, slug, nom, description, theme, pays, cree_le) VALUES (gen_random_uuid(), :s, 'Test', 'Pour les tests.', 'test', :p, now())")
			.param("s", slug).param("p", pays).update();
		return slug;
	}

	private MvcTestResult get(UtilisateurConnecte qui, String uri, Object... variables) {
		return mvc.get().uri(uri, variables).with(oauth2Login().oauth2User(qui)).exchange();
	}

	private MvcTestResult rejoindre(UtilisateurConnecte qui, String slug) {
		return mvc.post().uri("/api/communautes/{s}/adhesion", slug).with(csrf()).with(oauth2Login().oauth2User(qui)).exchange();
	}

	private MvcTestResult publier(UtilisateurConnecte qui, String slug, String corps) {
		return mvc.post().uri("/api/communautes/{s}/publications", slug).contentType(MediaType.APPLICATION_JSON).content(corps)
			.with(csrf()).with(oauth2Login().oauth2User(qui)).exchange();
	}

	@Test
	void il_faut_etre_connecte_et_les_communautes_de_depart_existent() {
		assertThat(mvc.get().uri("/api/communautes").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);

		MvcTestResult reponse = get(utilisateur("Koffi Yao"), "/api/communautes?limite=100");

		assertThat(reponse).hasStatusOk();
		assertThat(reponse).bodyJson().extractingPath("$.elements[*].slug").asArray().contains("flutter", "antenne-dakar", "antenne-nairobi");
		assertThat(reponse).bodyJson().extractingPath("$.elements[?(@.slug == 'antenne-dakar')].pays").asArray().containsExactly("SN");
	}

	@Test
	void rejoindre_est_idempotent_et_quitter_aussi() {
		UtilisateurConnecte awa = utilisateur("Awa Diop");
		String slug = communaute("SN");

		assertThat(rejoindre(awa, slug)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(rejoindre(awa, slug)).hasStatus(HttpStatus.NO_CONTENT);
		MvcTestResult fiche = get(awa, "/api/communautes/{s}", slug);
		assertThat(fiche).bodyJson().extractingPath("$.membre").isEqualTo(true);
		assertThat(fiche).bodyJson().extractingPath("$.nombreMembres").isEqualTo(1);
		assertThat(get(awa, "/api/communautes?membre=moi&limite=100")).bodyJson().extractingPath("$.elements[*].slug").asArray().contains(slug);

		MvcTestResult quitter = mvc.delete().uri("/api/communautes/{s}/adhesion", slug).with(csrf()).with(oauth2Login().oauth2User(awa)).exchange();
		assertThat(quitter).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(get(awa, "/api/communautes/{s}", slug)).bodyJson().extractingPath("$.membre").isEqualTo(false);
	}

	@Test
	void sans_jeton_csrf_rejoindre_est_refuse() {
		MvcTestResult reponse = mvc.post().uri("/api/communautes/{s}/adhesion", communaute(null))
			.with(oauth2Login().oauth2User(utilisateur("Chidi Okafor"))).exchange();
		assertThat(reponse).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void une_communaute_inconnue_repond_introuvable() {
		UtilisateurConnecte qui = utilisateur("Aya Kouassi");
		assertThat(get(qui, "/api/communautes/{s}", "nexiste-pas")).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(rejoindre(qui, "nexiste-pas")).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(get(qui, "/api/publications/{id}", UUID.randomUUID())).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void il_faut_etre_membre_pour_publier() {
		MvcTestResult reponse = publier(utilisateur("Wanjiru Kamau"), communaute(null), QUESTION);

		assertThat(reponse).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(reponse).bodyJson().extractingPath("$.code").isEqualTo("pas_membre");
	}

	@Test
	void une_publication_invalide_est_refusee_champ_par_champ() {
		UtilisateurConnecte qui = utilisateur("Fatou Ndiaye");
		String slug = communaute(null);
		rejoindre(qui, slug);

		MvcTestResult reponse = publier(qui, slug, """
				{"type":"sondage","titre":"Court","contenu":"Trop court","projetId":"%s"}""".formatted(UUID.randomUUID()));

		assertThat(reponse).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(reponse).bodyJson().extractingPath("$.erreurs[*].champ").asArray().containsExactlyInAnyOrder("type", "titre", "contenu", "projetId");
	}

	@Test
	void une_question_publiee_se_relit_avec_son_auteur_et_l_etat_de_l_ia() {
		UtilisateurConnecte awa = utilisateur("Awa Diop");
		UtilisateurConnecte koffi = utilisateur("Koffi Yao");
		String slug = communaute(null);
		rejoindre(awa, slug);

		MvcTestResult creee = publier(awa, slug, QUESTION.replace("hors ligne", "<b>hors ligne</b>"));

		assertThat(creee).hasStatus(HttpStatus.CREATED);
		String id = creee.getResponse().getHeader("Location").substring("/api/publications/".length());
		MvcTestResult lue = get(koffi, "/api/publications/{id}", id);
		assertThat(lue).bodyJson().extractingPath("$.titre").isEqualTo("Comment garder les données <b>hors ligne</b> ?");
		assertThat(lue).bodyJson().extractingPath("$.auteur.nom").isEqualTo("Awa Diop");
		assertThat(lue).bodyJson().extractingPath("$.communaute").isEqualTo(slug);
		// Tant que le service IA ne sait pas répondre (#69), la question reste ouverte aux humains.
		assertThat(lue).bodyJson().extractingPath("$.propositionIa.statut").isEqualTo("indisponible");
		assertThat(lue).bodyJson().extractingPath("$.reponses").isEqualTo(List.of());
		assertThat(lue).bodyJson().extractingPath("$.moi.estAuteur").isEqualTo(false);
		assertThat(lue).bodyJson().extractingPath("$.moi.estMembre").isEqualTo(false);
		assertThat(get(awa, "/api/communautes/{s}", slug)).bodyJson().extractingPath("$.questionsOuvertes").isEqualTo(1);
	}

	@Test
	void une_discussion_n_a_pas_de_proposition_de_l_ia() {
		UtilisateurConnecte qui = utilisateur("Chidi Okafor");
		String slug = communaute(null);
		rejoindre(qui, slug);

		MvcTestResult creee = publier(qui, slug, QUESTION.replace("\"question\"", "\"discussion\""));

		assertThat(creee).hasStatus(HttpStatus.CREATED);
		assertThat(creee).bodyJson().extractingPath("$.propositionIa").isNull();
		assertThat(creee).bodyJson().extractingPath("$.moi.estAuteur").isEqualTo(true);
	}

	@Test
	void on_ne_montre_que_son_propre_projet() {
		UtilisateurConnecte awa = utilisateur("Awa Diop");
		UtilisateurConnecte koffi = utilisateur("Koffi Yao");
		String slug = communaute(null);
		rejoindre(koffi, slug);
		MvcTestResult projet = mvc.post().uri("/api/projets").contentType(MediaType.APPLICATION_JSON)
			.content("""
					{"titre":"Caisse hors ligne","description":"Une caisse qui marche sans internet.","origine":"idee"}""")
			.with(csrf()).with(oauth2Login().oauth2User(awa)).exchange();
		String projetId = projet.getResponse().getHeader("Location").substring("/api/projets/".length());
		String corps = """
				{"type":"montre_ton_projet","titre":"Notre caisse hors ligne","contenu":"Elle marche sans internet, venez la tester.","projetId":"%s"}"""
			.formatted(projetId);

		MvcTestResult refusee = publier(koffi, slug, corps);
		assertThat(refusee).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(refusee).bodyJson().extractingPath("$.erreurs[0].code").isEqualTo("pas_dans_l_equipe");

		rejoindre(awa, slug);
		MvcTestResult acceptee = publier(awa, slug, corps);
		assertThat(acceptee).hasStatus(HttpStatus.CREATED);
		assertThat(acceptee).bodyJson().extractingPath("$.projetId").isEqualTo(projetId);
	}

	@Test
	void les_publications_se_parcourent_par_curseur_et_se_filtrent() throws Exception {
		UtilisateurConnecte qui = utilisateur("Aya Kouassi");
		String slug = communaute(null);
		rejoindre(qui, slug);
		publier(qui, slug, QUESTION.replace("\"question\"", "\"discussion\""));
		publier(qui, slug, QUESTION);
		publier(qui, slug, QUESTION.replace("hors ligne", "en cache"));

		MvcTestResult page1 = get(qui, "/api/communautes/{s}/publications?limite=2", slug);
		assertThat(page1).bodyJson().extractingPath("$.elements.length()").isEqualTo(2);
		assertThat(page1).bodyJson().extractingPath("$.elements[0].titre").isEqualTo("Comment garder les données en cache ?");
		String curseur = com.jayway.jsonpath.JsonPath.read(page1.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8), "$.curseurSuivant");
		MvcTestResult page2 = get(qui, "/api/communautes/{s}/publications?limite=2&curseur={c}", slug, curseur);
		assertThat(page2).bodyJson().extractingPath("$.elements[*].type").asArray().containsExactly("discussion");
		assertThat(page2).bodyJson().extractingPath("$.curseurSuivant").isNull();

		assertThat(get(qui, "/api/communautes/{s}/publications?type=question", slug)).bodyJson()
			.extractingPath("$.elements.length()").isEqualTo(2);
		assertThat(get(qui, "/api/communautes/{s}/publications?sansReponseAcceptee=true", slug)).bodyJson()
			.extractingPath("$.elements[*].type").asArray().containsOnly("question");
		assertThat(get(qui, "/api/communautes/{s}/publications?curseur=nimportequoi", slug)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(get(qui, "/api/communautes/{s}/publications?limite=500", slug)).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void au_dela_de_dix_publications_par_heure_il_faut_attendre() {
		UtilisateurConnecte qui = utilisateur("Wanjiru Kamau");
		String slug = communaute(null);
		rejoindre(qui, slug);
		UUID communaute = base.sql("SELECT id FROM communaute WHERE slug = :s").param("s", slug).query(UUID.class).single();
		for (int i = 0; i < GestionCommunautes.PUBLICATIONS_PAR_HEURE; i++) {
			base.sql("""
					INSERT INTO publication (id, communaute_id, auteur_id, type, titre, contenu, cree_le)
					VALUES (gen_random_uuid(), :c, :u, 'discussion', 'Déjà publiée', 'Une publication récente.', :t)
					""").param("c", communaute).param("u", qui.id())
				.param("t", Timestamp.from(Instant.now().minus(Duration.ofMinutes(30)))).update();
		}

		MvcTestResult reponse = publier(qui, slug, QUESTION);

		assertThat(reponse).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(reponse).bodyJson().extractingPath("$.code").isEqualTo("trop_de_requetes");
		assertThat(Long.parseLong(reponse.getResponse().getHeader("Retry-After"))).isBetween(1L, 1800L);
	}

}
