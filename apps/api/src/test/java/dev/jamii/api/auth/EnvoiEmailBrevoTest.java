package dev.jamii.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClientException;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** L'adaptateur Brevo contre un faux Brevo local. */
class EnvoiEmailBrevoTest {

	private static final JsonMapper JSON = new JsonMapper();

	record Appel(String methode, String chemin, String cle, JsonNode corps) {
	}

	private final List<Appel> appels = new CopyOnWriteArrayList<>();

	private HttpServer serveur;

	@AfterEach
	void arreter() {
		if (serveur != null) {
			serveur.stop(0);
		}
	}

	private String fauxBrevo(int statut) throws IOException {
		serveur = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		serveur.createContext("/", echange -> {
			appels.add(new Appel(echange.getRequestMethod(), echange.getRequestURI().getPath(),
					echange.getRequestHeaders().getFirst("api-key"), JSON.readTree(echange.getRequestBody().readAllBytes())));
			byte[] sortie = "{\"messageId\":\"<1@brevo>\"}".getBytes();
			echange.getResponseHeaders().add("Content-Type", "application/json");
			echange.sendResponseHeaders(statut, sortie.length);
			echange.getResponseBody().write(sortie);
			echange.close();
		});
		serveur.start();
		return "http://127.0.0.1:" + serveur.getAddress().getPort();
	}

	@Test
	void envoie_le_message_en_texte_brut_avec_la_cle() throws IOException {
		var envoi = new EnvoiEmailBrevo(fauxBrevo(201), "cle-de-test", "bonjour@jamii.dev", "Jamii");

		envoi.envoyer("awa@exemple.sn", "Ton lien de connexion à Jamii", "Bonjour,\nhttps://jamii/lien");

		assertThat(appels).hasSize(1);
		Appel appel = appels.get(0);
		assertThat(appel.methode()).isEqualTo("POST");
		assertThat(appel.chemin()).isEqualTo("/smtp/email");
		assertThat(appel.cle()).isEqualTo("cle-de-test");
		assertThat(appel.corps().at("/sender/email").asString()).isEqualTo("bonjour@jamii.dev");
		assertThat(appel.corps().at("/sender/name").asString()).isEqualTo("Jamii");
		assertThat(appel.corps().at("/to/0/email").asString()).isEqualTo("awa@exemple.sn");
		assertThat(appel.corps().at("/subject").asString()).isEqualTo("Ton lien de connexion à Jamii");
		assertThat(appel.corps().at("/textContent").asString()).contains("https://jamii/lien");
	}

	@Test
	void un_refus_de_brevo_remonte_en_exception() throws IOException {
		var envoi = new EnvoiEmailBrevo(fauxBrevo(401), "mauvaise-cle", "bonjour@jamii.dev", "Jamii");

		assertThatThrownBy(() -> envoi.envoyer("awa@exemple.sn", "Objet", "Texte"))
			.isInstanceOf(RestClientException.class);
	}

	@Test
	void refuse_de_demarrer_sans_cle_ni_expediteur_valide() {
		assertThatThrownBy(() -> new EnvoiEmailBrevo("http://127.0.0.1:1", "", "bonjour@jamii.dev", "Jamii"))
			.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> new EnvoiEmailBrevo("http://127.0.0.1:1", "cle", "pas-une-adresse", "Jamii"))
			.isInstanceOf(IllegalStateException.class);
	}

}
