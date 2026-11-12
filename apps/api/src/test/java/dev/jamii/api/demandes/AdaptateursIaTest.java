package dev.jamii.api.demandes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Les deux adaptateurs du service IA : le simulé (sans réseau) et le HTTP (contre un faux service local). */
class AdaptateursIaTest {

	private static final JsonMapper JSON = new JsonMapper();

	private static final PlansIa.Entree ENTREE = new PlansIa.Entree("Boutique de couture", "Je vends mes créations en ligne.", 8);

	/** Une horloge que le test fait avancer. */
	private static final class Horloge extends Clock {

		Instant maintenant = Instant.parse("2026-11-12T10:00:00Z");

		@Override
		public ZoneOffset getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return maintenant;
		}

	}

	// ---------------------------------------------------------------- simulé

	@Test
	void le_simule_fait_avancer_la_tache_avec_le_temps_et_annonce_un_repli() {
		Horloge horloge = new Horloge();
		PlansIaSimules ia = new PlansIaSimules(Duration.ofSeconds(4), horloge, JSON);
		UUID tache = ia.deposer(UUID.randomUUID(), ENTREE);

		assertThat(ia.lire(tache).statut()).isEqualTo("en_attente");
		horloge.maintenant = horloge.maintenant.plusSeconds(2);
		assertThat(ia.lire(tache).statut()).isEqualTo("en_cours");
		horloge.maintenant = horloge.maintenant.plusSeconds(2);
		PlansIa.Etat fini = ia.lire(tache);

		assertThat(fini.statut()).isEqualTo("terminee");
		assertThat(fini.origine()).as("un plan simulé est annoncé comme un repli").isEqualTo("repli");
		assertThat(fini.resultat().get("jalons")).hasSize(3);
		assertThat(fini.resultat().get("jalons").get(2).get("semaine").asInt()).isEqualTo(8);
		assertThat(fini.resultat().get("cahierDesCharges").get("resume").asString()).contains("Je vends mes créations");
	}

	@Test
	void le_simule_est_idempotent_et_refuse_une_tache_inconnue() {
		PlansIaSimules ia = new PlansIaSimules(Duration.ofSeconds(4), new Horloge(), JSON);
		UUID cle = UUID.randomUUID();

		assertThat(ia.deposer(cle, ENTREE)).isEqualTo(ia.deposer(cle, ENTREE));
		assertThat(ia.deposer(UUID.randomUUID(), ENTREE)).isNotEqualTo(ia.deposer(cle, ENTREE));
		assertThatThrownBy(() -> ia.lire(UUID.randomUUID())).isInstanceOf(IllegalStateException.class);
	}

	// ---------------------------------------------------------------- HTTP

	private record Requete(String methode, String chemin, String autorisation, String cle, String corps) {
	}

	private HttpServer faux(List<Requete> recues, AtomicReference<String> reponseGet, int codeGet) throws IOException {
		HttpServer serveur = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		serveur.createContext("/taches", echange -> {
			String corps = new String(echange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			recues.add(new Requete(echange.getRequestMethod(), echange.getRequestURI().getPath(),
					echange.getRequestHeaders().getFirst("Authorization"), echange.getRequestHeaders().getFirst("Idempotency-Key"),
					corps));
			if ("POST".equals(echange.getRequestMethod())) {
				repondre(echange, 202, "{\"id\":\"0b0f5a4e-6c1d-4f0e-9a3b-2f6f0d1c7e11\",\"type\":\"plan_projet\",\"statut\":\"en_attente\",\"creeLe\":\"2026-11-12T10:00:00Z\"}");
			}
			else {
				repondre(echange, codeGet, reponseGet.get());
			}
		});
		serveur.start();
		return serveur;
	}

	private static void repondre(HttpExchange echange, int code, String corps) throws IOException {
		byte[] octets = corps.getBytes(StandardCharsets.UTF_8);
		echange.getResponseHeaders().add("Content-Type", "application/json");
		echange.sendResponseHeaders(code, octets.length);
		echange.getResponseBody().write(octets);
		echange.close();
	}

	@Test
	void l_adaptateur_http_depose_avec_le_jeton_la_cle_et_le_minimum_de_donnees() throws Exception {
		List<Requete> recues = new ArrayList<>();
		HttpServer serveur = faux(recues, new AtomicReference<>("{}"), 200);
		try {
			PlansIaHttp ia = new PlansIaHttp("http://127.0.0.1:" + serveur.getAddress().getPort(), "jeton-secret", Duration.ofSeconds(2));
			UUID cle = UUID.randomUUID();

			UUID tache = ia.deposer(cle, ENTREE);

			assertThat(tache).isEqualTo(UUID.fromString("0b0f5a4e-6c1d-4f0e-9a3b-2f6f0d1c7e11"));
			Requete requete = recues.getFirst();
			assertThat(requete.methode()).isEqualTo("POST");
			assertThat(requete.autorisation()).isEqualTo("Bearer jeton-secret");
			assertThat(requete.cle()).isEqualTo(cle.toString());
			JsonNode corps = JSON.readTree(requete.corps());
			assertThat(corps.get("type").asString()).isEqualTo("plan_projet");
			assertThat(corps.get("entree").get("source").asString()).isEqualTo("demande_pme");
			assertThat(corps.get("entree").get("titre").asString()).isEqualTo("Boutique de couture");
			assertThat(corps.get("entree").get("dureeSemaines").asInt()).isEqualTo(8);
			assertThat(corps.get("entree").get("langue").asString()).isEqualTo("fr");
			assertThat(corps.get("entree").propertyNames()).containsExactlyInAnyOrder("source", "titre", "description", "langue",
					"dureeSemaines");
		}
		finally {
			serveur.stop(0);
		}
	}

	@Test
	void l_adaptateur_http_lit_l_etat_et_le_resultat() throws Exception {
		List<Requete> recues = new ArrayList<>();
		AtomicReference<String> reponse = new AtomicReference<>(
				"{\"id\":\"0b0f5a4e-6c1d-4f0e-9a3b-2f6f0d1c7e11\",\"type\":\"plan_projet\",\"statut\":\"terminee\",\"origine\":\"ia\",\"creeLe\":\"2026-11-12T10:00:00Z\",\"resultat\":{\"jalons\":[{\"titre\":\"A\"}]}}");
		HttpServer serveur = faux(recues, reponse, 200);
		try {
			PlansIaHttp ia = new PlansIaHttp("http://127.0.0.1:" + serveur.getAddress().getPort(), "jeton", Duration.ofSeconds(2));

			PlansIa.Etat etat = ia.lire(UUID.fromString("0b0f5a4e-6c1d-4f0e-9a3b-2f6f0d1c7e11"));

			assertThat(etat.statut()).isEqualTo("terminee");
			assertThat(etat.origine()).isEqualTo("ia");
			assertThat(etat.resultat().get("jalons").get(0).get("titre").asString()).isEqualTo("A");
			assertThat(etat.erreurCode()).isNull();
			assertThat(recues.getFirst().chemin()).isEqualTo("/taches/0b0f5a4e-6c1d-4f0e-9a3b-2f6f0d1c7e11");
			assertThat(recues.getFirst().autorisation()).isEqualTo("Bearer jeton");

			reponse.set("{\"id\":\"0b0f5a4e-6c1d-4f0e-9a3b-2f6f0d1c7e11\",\"statut\":\"echouee\",\"erreurCode\":\"delai_depasse\"}");
			PlansIa.Etat echec = ia.lire(UUID.randomUUID());
			assertThat(echec.statut()).isEqualTo("echouee");
			assertThat(echec.erreurCode()).isEqualTo("delai_depasse");
			assertThat(echec.resultat()).isNull();
		}
		finally {
			serveur.stop(0);
		}
	}

	@Test
	void l_adaptateur_http_leve_une_exception_si_le_service_refuse_ou_ne_repond_pas() throws Exception {
		HttpServer serveur = faux(new ArrayList<>(), new AtomicReference<>("{\"code\":\"non_connecte\"}"), 401);
		int port = serveur.getAddress().getPort();
		try {
			PlansIaHttp ia = new PlansIaHttp("http://127.0.0.1:" + port, "mauvais", Duration.ofSeconds(2));
			assertThatThrownBy(() -> ia.lire(UUID.randomUUID())).isInstanceOf(RuntimeException.class);
		}
		finally {
			serveur.stop(0);
		}
		PlansIaHttp ferme = new PlansIaHttp("http://127.0.0.1:" + port, "jeton", Duration.ofMillis(500));
		assertThatThrownBy(() -> ferme.deposer(UUID.randomUUID(), ENTREE)).isInstanceOf(RuntimeException.class);
	}

	@Test
	void le_mode_service_exige_l_adresse_et_le_jeton() {
		assertThatThrownBy(() -> new PlansIaHttp("", "jeton", Duration.ofSeconds(1))).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new PlansIaHttp("http://localhost:8000", " ", Duration.ofSeconds(1)))
			.isInstanceOf(IllegalStateException.class).hasMessageContaining("IA_JETON_INTERNE");
	}

}
