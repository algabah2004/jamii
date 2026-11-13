package dev.jamii.api.profils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** La carte de compétences : ce qui part vers l'IA, ce qu'on accepte d'elle, et les deux adaptateurs. */
class CartesIaTest {

	private static final Instant T = Instant.parse("2026-11-10T12:00:00Z");

	private static final List<SourceGithub.Depot> DEPOTS = List.of(
			new SourceGithub.Depot("api-paiements", "https://github.com/awa/api-paiements", "Paiement mobile", "TypeScript",
					17, 2100, false, List.of("nodejs"), T),
			new SourceGithub.Depot("vieux", "https://github.com/awa/vieux", null, "Java", 0, 10, false, null,
					T.minusSeconds(86_400)),
			new SourceGithub.Depot("copie", "https://github.com/awa/copie", "Fork", "Dart", 0, 9000, true, List.of(), T));

	private static final CartesIa.Entree ENTREE = CartesIa.entree(DEPOTS,
			List.of(new Profil.Langage("TypeScript", new BigDecimal("0.9")), new Profil.Langage("Java", new BigDecimal("0.1"))),
			Set.of("ux-design"));

	@Test
	void seuls_des_faits_publics_partent_vers_l_ia_sans_fork_ni_url() {
		assertThat(ENTREE.depots()).extracting(CartesIa.Depot::nom).containsExactly("api-paiements", "vieux");
		assertThat(ENTREE.depots().get(1).sujets()).isEmpty();
		assertThat(new JsonMapper().writeValueAsString(ENTREE)).doesNotContain("github.com", "awa");
		assertThat(ENTREE.competencesDeclarees()).containsExactly("ux-design");
	}

	@Test
	void une_competence_sans_preuve_reelle_ou_mal_formee_est_retiree() {
		List<CartesIa.Proposee> carte = CartesIa.nettoyer(List.of(
				new CartesIa.Proposee("Node.JS", "confirme", List.of("api-paiements", "invente"), "API REST"),
				new CartesIa.Proposee("nodejs", "debutant", List.of("api-paiements"), "doublon"),
				new CartesIa.Proposee("kubernetes", "confirme", List.of("depot-invente"), "sans preuve"),
				new CartesIa.Proposee("java", "expert", List.of("vieux"), "niveau inconnu"),
				new CartesIa.Proposee("<script>", "debutant", List.of("vieux"), "forme invalide")), ENTREE);

		assertThat(carte).containsExactly(new CartesIa.Proposee("node.js", "confirme", List.of("api-paiements"), "API REST"));
	}

	@Test
	void le_simule_prouve_chaque_langage_par_ses_depots() {
		assertThat(new CartesIaSimulees().proposer(UUID.randomUUID(), ENTREE)).extracting(CartesIa.Proposee::competence,
				CartesIa.Proposee::niveau).containsExactly(org.assertj.core.groups.Tuple.tuple("typescript", "debutant"),
						org.assertj.core.groups.Tuple.tuple("java", "debutant"));
	}

	@Test
	void le_stockage_relit_ce_qu_il_a_ecrit() {
		var carte = List.of(new CartesIa.Proposee("java", "debutant", List.of("vieux"), "Un dépôt"));
		assertThat(CartesIa.Stockage.lire(CartesIa.Stockage.ecrire(carte))).isEqualTo(carte);
		assertThat(CartesIa.Stockage.ecrire(List.of())).isNull();
		assertThat(CartesIa.Stockage.lire(null)).isEmpty();
	}

	// ---------------------------------------------------------------- HTTP

	private static void repondre(HttpExchange echange, int code, String corps) throws IOException {
		byte[] octets = corps.getBytes(StandardCharsets.UTF_8);
		echange.getResponseHeaders().add("Content-Type", "application/json");
		echange.sendResponseHeaders(code, octets.length);
		echange.getResponseBody().write(octets);
		echange.close();
	}

	@Test
	void l_adaptateur_http_depose_analyse_profil_puis_relit_jusqu_a_la_fin() throws Exception {
		List<String> corps = new ArrayList<>();
		AtomicInteger lectures = new AtomicInteger();
		HttpServer serveur = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		serveur.createContext("/taches", echange -> {
			if ("POST".equals(echange.getRequestMethod())) {
				corps.add(new String(echange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
				repondre(echange, 202, "{\"id\":\"0b0f5a4e-6c1d-4f0e-9a3b-2f6f0d1c7e11\",\"statut\":\"en_attente\"}");
			}
			else if (lectures.incrementAndGet() < 2) {
				repondre(echange, 200, "{\"statut\":\"en_cours\"}");
			}
			else {
				repondre(echange, 200, """
						{"statut":"terminee","origine":"ia","resultat":{"competences":[
						 {"competence":"typescript","niveau":"intermediaire","preuves":["api-paiements"],"raison":"API"},
						 {"competence":"go","niveau":"confirme","preuves":["imaginaire"],"raison":"inventé"}]}}""");
			}
		});
		serveur.start();
		try {
			CartesIaHttp ia = new CartesIaHttp("http://127.0.0.1:" + serveur.getAddress().getPort(), "jeton",
					Duration.ofSeconds(2), Duration.ofSeconds(5), Duration.ofMillis(10));

			List<CartesIa.Proposee> carte = ia.proposer(UUID.randomUUID(), ENTREE);

			assertThat(carte).extracting(CartesIa.Proposee::competence).containsExactly("typescript");
			JsonNode envoye = new JsonMapper().readTree(corps.getFirst());
			assertThat(envoye.get("type").asString()).isEqualTo("analyse_profil");
			assertThat(envoye.get("entree").propertyNames()).containsExactlyInAnyOrder("langages", "depots",
					"competencesDeclarees", "langue");
			assertThat(envoye.get("entree").get("depots").get(0).propertyNames()).doesNotContain("url");
		}
		finally {
			serveur.stop(0);
		}
	}

	@Test
	void l_adaptateur_http_abandonne_si_la_tache_echoue_ou_si_le_service_est_absent() throws Exception {
		HttpServer serveur = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		serveur.createContext("/taches", echange -> repondre(echange, "POST".equals(echange.getRequestMethod()) ? 202 : 200,
				"{\"id\":\"0b0f5a4e-6c1d-4f0e-9a3b-2f6f0d1c7e11\",\"statut\":\"echouee\"}"));
		serveur.start();
		int port = serveur.getAddress().getPort();
		try {
			CartesIaHttp ia = new CartesIaHttp("http://127.0.0.1:" + port, "jeton", Duration.ofSeconds(2),
					Duration.ofSeconds(5), Duration.ofMillis(10));
			assertThatThrownBy(() -> ia.proposer(UUID.randomUUID(), ENTREE)).isInstanceOf(IllegalStateException.class);
		}
		finally {
			serveur.stop(0);
		}
		CartesIaHttp absent = new CartesIaHttp("http://127.0.0.1:" + port, "jeton", Duration.ofMillis(500),
				Duration.ofSeconds(1), Duration.ofMillis(10));
		assertThatThrownBy(() -> absent.proposer(UUID.randomUUID(), ENTREE)).isInstanceOf(RuntimeException.class);
	}

}
