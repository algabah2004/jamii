package dev.jamii.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.simple.JdbcClient;

import dev.jamii.api.TestcontainersConfiguration;

/**
 * Le parcours complet sur un vrai serveur HTTP, comme le fera le navigateur : jeton CSRF, demande du lien,
 * confirmation, puis session. MockMvc ne peut pas le vérifier : il n'applique pas le nom ni les attributs du cookie
 * de session (JAMII_SESSION, HttpOnly, Secure, SameSite=Lax).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({ TestcontainersConfiguration.class, ConnexionEmailBoutEnBoutTest.EnvoiCapture.class })
class ConnexionEmailBoutEnBoutTest {

	@TestConfiguration(proxyBeanMethods = false)
	static class EnvoiCapture {

		static final List<String> TEXTES = new ArrayList<>();

		@Bean
		@Primary
		EnvoiEmail envoiCapture() {
			return (destinataire, objet, texte) -> TEXTES.add(texte);
		}

	}

	@LocalServerPort
	int port;

	@Autowired
	JdbcClient base;

	private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

	private HttpResponse<String> envoyer(String methode, String chemin, String corps, String... entetes) throws Exception {
		HttpRequest.Builder requete = HttpRequest.newBuilder(URI.create("http://localhost:" + port + chemin))
			.method(methode, corps == null ? BodyPublishers.noBody() : BodyPublishers.ofString(corps));
		if (corps != null) {
			requete.header("Content-Type", "application/json");
		}
		for (int i = 0; i < entetes.length; i += 2) {
			requete.header(entetes[i], entetes[i + 1]);
		}
		return client.send(requete.build(), BodyHandlers.ofString());
	}

	private static String cookie(HttpResponse<?> reponse, String nom) {
		return reponse.headers().allValues("Set-Cookie").stream().filter(c -> c.startsWith(nom + "=")).findFirst().orElse(null);
	}

	private static String valeur(String setCookie) {
		return setCookie.substring(setCookie.indexOf('=') + 1, setCookie.indexOf(';') < 0 ? setCookie.length() : setCookie.indexOf(';'));
	}

	@Test
	void parcours_complet_du_lien_de_connexion_a_la_session() throws Exception {
		// 1. Avant toute connexion, le front obtient le jeton CSRF par n'importe quelle première requête.
		HttpResponse<String> premiere = envoyer("GET", "/api/me", null);
		assertThat(premiere.statusCode()).isEqualTo(401);
		String xsrf = cookie(premiere, "XSRF-TOKEN");
		assertThat(xsrf).as("le cookie CSRF est posé dès la première requête, avant même de se connecter").isNotNull();
		String jetonCsrf = valeur(xsrf);
		String enTetesCsrf = "XSRF-TOKEN=" + jetonCsrf;

		// 2. Une session ouverte avant la connexion (ici par le début d'une connexion GitHub) ne doit pas survivre.
		HttpResponse<String> github = envoyer("GET", "/api/auth/github", null);
		String ancienne = cookie(github, "JAMII_SESSION");
		assertThat(ancienne).isNotNull();

		// 3. Demande du lien.
		String email = "bout-en-bout-" + UUID.randomUUID() + "@example.org";
		HttpResponse<String> demande = envoyer("POST", "/api/auth/lien-email", "{\"email\":\"" + email + "\"}",
				"X-XSRF-TOKEN", jetonCsrf, "Cookie", enTetesCsrf);
		assertThat(demande.statusCode()).isEqualTo(202);
		Matcher m = Pattern.compile("jeton=([A-Za-z0-9_-]+)").matcher(EnvoiCapture.TEXTES.getLast());
		assertThat(m.find()).isTrue();

		// 4. Confirmation, avec l'ancienne session.
		HttpResponse<String> confirmation = envoyer("POST", "/api/auth/lien-email/confirmation",
				"{\"jeton\":\"" + m.group(1) + "\"}", "X-XSRF-TOKEN", jetonCsrf, "Cookie",
				enTetesCsrf + "; JAMII_SESSION=" + valeur(ancienne));
		assertThat(confirmation.statusCode()).isEqualTo(204);
		String session = cookie(confirmation, "JAMII_SESSION");
		assertThat(session).as("un nouveau cookie de session").isNotNull();
		assertThat(session).contains("HttpOnly").contains("Secure").containsIgnoringCase("SameSite=Lax");
		assertThat(valeur(session)).as("contre la fixation de session").isNotEqualTo(valeur(ancienne));

		// 5. La nouvelle session connecte ; l'ancienne ne connecte pas.
		HttpResponse<String> moi = envoyer("GET", "/api/me", null, "Cookie", "JAMII_SESSION=" + valeur(session));
		assertThat(moi.statusCode()).isEqualTo(200);
		assertThat(moi.body()).contains("\"email\":\"" + email + "\"").contains("\"methodesConnexion\":[\"email\"]")
			.contains("\"nom\":\"Nouveau membre\"");
		assertThat(envoyer("GET", "/api/me", null, "Cookie", "JAMII_SESSION=" + valeur(ancienne)).statusCode()).isEqualTo(401);

		// 6. Le même lien ne sert pas deux fois.
		HttpResponse<String> rejeu = envoyer("POST", "/api/auth/lien-email/confirmation",
				"{\"jeton\":\"" + m.group(1) + "\"}", "X-XSRF-TOKEN", jetonCsrf, "Cookie", enTetesCsrf);
		assertThat(rejeu.statusCode()).isEqualTo(400);
		assertThat(cookie(rejeu, "JAMII_SESSION")).isNull();
	}

}
