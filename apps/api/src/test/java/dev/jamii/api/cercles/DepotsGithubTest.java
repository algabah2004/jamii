package dev.jamii.api.cercles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestClientException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** L'adaptateur GitHub des dépôts de tour, contre un faux GitHub local, et son choix par `DEPOT_CODE`. */
class DepotsGithubTest {

	private static final JsonMapper JSON = new JsonMapper();

	private static final UUID CERCLE = UUID.fromString("0a1b2c3d-0000-4000-8000-000000000001");

	private static final DepotCode.DemandeDepot DEMANDE = new DepotCode.DemandeDepot(CERCLE, "Cercle de Thiès", 2,
			"Une application pour les marchés", List.of("awa-dev", "kofi", "pas un login", "../admin"));

	/** Ce que le faux GitHub a reçu. */
	record Appel(String methode, String chemin, String autorisation, JsonNode corps) {
	}

	/** Ce qu'il répond : un statut et un corps JSON. */
	record Reponse(int statut, String corps) {
	}

	private final List<Appel> appels = new CopyOnWriteArrayList<>();

	private HttpServer serveur;

	@AfterEach
	void arreter() {
		if (serveur != null) {
			serveur.stop(0);
		}
	}

	private String fauxGithub(Function<Appel, Reponse> repondre) throws IOException {
		serveur = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		serveur.createContext("/", (HttpExchange echange) -> {
			byte[] brut = echange.getRequestBody().readAllBytes();
			JsonNode corps = brut.length == 0 ? null : JSON.readTree(brut);
			Appel appel = new Appel(echange.getRequestMethod(), echange.getRequestURI().getRawPath(),
					echange.getRequestHeaders().getFirst("Authorization"), corps);
			appels.add(appel);
			Reponse reponse = repondre.apply(appel);
			byte[] sortie = reponse.corps().getBytes(StandardCharsets.UTF_8);
			echange.getResponseHeaders().add("Content-Type", "application/json");
			echange.sendResponseHeaders(reponse.statut(), reponse.statut() == 204 ? -1 : sortie.length);
			if (reponse.statut() != 204) {
				echange.getResponseBody().write(sortie);
			}
			echange.close();
		});
		serveur.start();
		return "http://127.0.0.1:" + serveur.getAddress().getPort();
	}

	private static Reponse depot(String nom) {
		return new Reponse(201, "{\"html_url\":\"https://github.com/jamii-cercles/" + nom + "\",\"clone_url\":\"https://github.com/jamii-cercles/"
				+ nom + ".git\"}");
	}

	private DepotsGithub adaptateur(String url) {
		return new DepotsGithub(url, "jamii-cercles", "jeton-de-test", false, Duration.ofSeconds(2));
	}

	// ---------------------------------------------------------------- création

	@Test
	void cree_le_depot_du_tour_dans_l_organisation_et_invite_les_membres_github() throws IOException {
		String nom = "cercle-cercle-de-thies-tour-2-0a1b2c3d";
		String url = fauxGithub(appel -> appel.methode().equals("POST") ? depot(nom) : new Reponse(201, "{}"));

		var depot = adaptateur(url).creerDepot(DEMANDE);

		assertThat(depot).contains(new DepotCode.DepotDuTour("github", "https://github.com/jamii-cercles/" + nom,
				"https://github.com/jamii-cercles/" + nom + ".git"));
		Appel creation = appels.getFirst();
		assertThat(creation.chemin()).isEqualTo("/orgs/jamii-cercles/repos");
		assertThat(creation.autorisation()).isEqualTo("Bearer jeton-de-test");
		assertThat(creation.corps().get("name").asString()).isEqualTo(nom);
		assertThat(creation.corps().get("private").asBoolean()).isFalse();
		assertThat(creation.corps().get("auto_init").asBoolean()).isTrue();
		assertThat(creation.corps().get("description").asString()).contains("Tour 2", "Cercle de Thiès", "marchés");
		assertThat(appels.stream().filter(a -> a.methode().equals("PUT")).map(Appel::chemin))
			.as("seuls les logins valides entrent dans une adresse de GitHub")
			.containsExactly("/repos/jamii-cercles/" + nom + "/collaborators/awa-dev",
					"/repos/jamii-cercles/" + nom + "/collaborators/kofi");
		assertThat(appels.get(1).corps().get("permission").asString()).isEqualTo("push");
	}

	@Test
	void un_depot_deja_cree_est_relu_ce_qui_rend_le_tirage_rejouable() throws IOException {
		String nom = "cercle-cercle-de-thies-tour-2-0a1b2c3d";
		String url = fauxGithub(appel -> switch (appel.methode()) {
			case "POST" -> new Reponse(422, "{\"message\":\"Repository creation failed.\"}");
			case "GET" -> new Reponse(200, depot(nom).corps());
			default -> new Reponse(204, "");
		});

		var depot = adaptateur(url).creerDepot(DEMANDE);

		assertThat(depot).map(DepotCode.DepotDuTour::url).contains("https://github.com/jamii-cercles/" + nom);
		assertThat(appels.get(1).chemin()).isEqualTo("/repos/jamii-cercles/" + nom);
	}

	@Test
	void une_invitation_refusee_n_empeche_ni_le_depot_ni_les_autres_invitations() throws IOException {
		String url = fauxGithub(appel -> {
			if (appel.methode().equals("POST")) {
				return depot("x");
			}
			return appel.chemin().endsWith("/awa-dev") ? new Reponse(404, "{\"message\":\"Not Found\"}") : new Reponse(201, "{}");
		});

		assertThat(adaptateur(url).creerDepot(DEMANDE)).isPresent();
		assertThat(appels).hasSize(3);
	}

	@Test
	void une_panne_de_github_remonte_pour_que_le_tour_reste_sans_depot() throws IOException {
		String url = fauxGithub(appel -> new Reponse(503, "{\"message\":\"Service Unavailable\"}"));

		assertThatThrownBy(() -> adaptateur(url).creerDepot(DEMANDE)).isInstanceOf(RestClientException.class)
			.as("le jeton n'apparaît pas dans le message").message().doesNotContain("jeton-de-test");
	}

	// ---------------------------------------------------------------- nom du dépôt

	@Test
	void le_nom_du_depot_est_lisible_unique_par_tour_et_dans_l_alphabet_de_github() {
		assertThat(DepotsGithub.nom(DEMANDE)).isEqualTo("cercle-cercle-de-thies-tour-2-0a1b2c3d");
		var etrange = new DepotCode.DemandeDepot(CERCLE, "  ***  ", 1, null, List.of());
		assertThat(DepotsGithub.nom(etrange)).isEqualTo("cercle-tour-1-0a1b2c3d");
		var long_ = new DepotCode.DemandeDepot(CERCLE, "Le très très long nom d'un cercle de Ouagadougou au Burkina", 6, null, List.of());
		assertThat(DepotsGithub.nom(long_)).matches("[a-z0-9-]+").hasSizeLessThanOrEqualTo(100).doesNotContain("--");
	}

	// ---------------------------------------------------------------- choix par la configuration

	/** Comme au démarrage de l'API : « 5s » se lit comme une durée. */
	private final ApplicationContextRunner contexte = new ApplicationContextRunner()
		.withInitializer(c -> c.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance()))
		.withUserConfiguration(DepotsConfig.class);

	@Test
	void sans_depot_code_les_tours_n_ont_pas_de_depot() {
		contexte.run(c -> assertThat(c).hasNotFailed().doesNotHaveBean(DepotCode.class));
	}

	@Test
	void depot_code_github_branche_l_adaptateur_et_exige_organisation_et_jeton() {
		contexte.withPropertyValues("jamii.depot-code.fournisseur=github", "jamii.depot-code.github.organisation=jamii-cercles",
				"jamii.depot-code.github.jeton=jeton-de-test")
			.run(c -> assertThat(c).getBean(DepotCode.class).isInstanceOf(DepotsGithub.class));
		contexte.withPropertyValues("jamii.depot-code.fournisseur=github", "jamii.depot-code.github.organisation=jamii-cercles")
			.run(c -> assertThat(c).hasFailed());
		contexte.withPropertyValues("jamii.depot-code.fournisseur=gitlab").run(c -> assertThat(c).hasFailed());
	}

	// ---------------------------------------------------------------- activité

	private static final DepotCode.DepotDuTour DU_TOUR = new DepotCode.DepotDuTour("github",
			"https://github.com/jamii-cercles/cercle-de-thies-tour-2-0a1b2c3d",
			"https://github.com/jamii-cercles/cercle-de-thies-tour-2-0a1b2c3d.git");

	private static final String COMMITS = """
			[{"sha":"9fceb02d0ae598e95dc970b74767f19372d61af8","html_url":"https://github.com/jamii-cercles/x/commit/9fceb02",
			  "commit":{"message":"Écran de caisse hors ligne\\n\\nDétails longs","author":{"date":"2026-11-14T20:41:00Z"}},
			  "author":{"login":"kofi","avatar_url":"https://avatars.githubusercontent.com/u/1"}},
			 {"sha":"aaaaaaa","html_url":"https://github.com/jamii-cercles/x/commit/aaaaaaa",
			  "commit":{"message":"Commit d'un inconnu","author":{"date":"2026-11-14T19:00:00Z"}},"author":null},
			 {"sha":"bbbbbbb","html_url":"javascript:alert(1)",
			  "commit":{"message":"Lien piégé","author":{"date":"2026-11-14T18:00:00Z"}},"author":null}]
			""";

	private static final String DEMANDES = """
			[{"number":3,"title":"Synchronisation au retour du réseau","state":"open","merged_at":null,
			  "user":{"login":"awa-dev","avatar_url":"https://avatars.githubusercontent.com/u/2"},
			  "updated_at":"2026-11-14T20:12:00Z","html_url":"https://github.com/jamii-cercles/x/pull/3"},
			 {"number":2,"title":"Maquette","state":"closed","merged_at":"2026-11-14T10:00:00Z",
			  "user":{"login":"kofi"},"updated_at":"2026-11-14T10:00:00Z","html_url":"https://github.com/jamii-cercles/x/pull/2"},
			 {"number":1,"title":"Essai","state":"closed","merged_at":null,
			  "user":{"login":"kofi"},"updated_at":"2026-11-13T10:00:00Z","html_url":"https://github.com/jamii-cercles/x/pull/1"}]
			""";

	@Test
	void lit_les_commits_et_les_demandes_de_fusion_du_depot_du_tour() throws IOException {
		String url = fauxGithub(a -> new Reponse(200, a.chemin().endsWith("/commits") ? COMMITS : DEMANDES));

		var activite = adaptateur(url).activite(DU_TOUR).orElseThrow();

		assertThat(appels).extracting(Appel::chemin).containsExactly(
				"/repos/jamii-cercles/cercle-de-thies-tour-2-0a1b2c3d/commits",
				"/repos/jamii-cercles/cercle-de-thies-tour-2-0a1b2c3d/pulls");
		assertThat(activite.commits()).extracting(DepotCode.Commit::message)
			.as("première ligne seulement, et le lien qui n'est pas en https est écarté")
			.containsExactly("Écran de caisse hors ligne", "Commit d'un inconnu");
		assertThat(activite.commits().get(0).auteur().login()).isEqualTo("kofi");
		assertThat(activite.commits().get(1).auteur()).isNull();
		assertThat(activite.demandesDeFusion()).extracting(DepotCode.DemandeDeFusion::etat)
			.containsExactly("ouverte", "fusionnee", "fermee");
	}

	@Test
	void un_depot_hors_de_l_organisation_n_est_jamais_appele() throws IOException {
		String url = fauxGithub(a -> new Reponse(200, "[]"));
		var ailleurs = new DepotCode.DepotDuTour("github", "https://github.com/quelquun/son-depot", "https://github.com/quelquun/son-depot.git");
		var autreSite = new DepotCode.DepotDuTour("github", "https://exemple.com/jamii-cercles/x", "https://exemple.com/x.git");

		assertThat(adaptateur(url).activite(ailleurs)).isEmpty();
		assertThat(adaptateur(url).activite(autreSite)).isEmpty();
		assertThat(appels).isEmpty();
	}

	@Test
	void une_panne_de_github_rend_l_activite_indisponible_sans_exception() throws IOException {
		String url = fauxGithub(a -> new Reponse(503, "{}"));

		assertThat(adaptateur(url).activite(DU_TOUR)).isEmpty();
	}

	@Test
	void la_premiere_ligne_est_coupee_a_200_caracteres_sans_caractere_de_controle() {
		assertThat(DepotsGithub.premiereLigne("a\u0007b\nsuite")).isEqualTo("a b");
		assertThat(DepotsGithub.premiereLigne("x".repeat(300))).hasSize(200).endsWith("…");
		assertThat(DepotsGithub.premiereLigne(null)).isEmpty();
	}

}
