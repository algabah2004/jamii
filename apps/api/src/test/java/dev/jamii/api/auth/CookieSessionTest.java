package dev.jamii.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

import dev.jamii.api.TestcontainersConfiguration;

/**
 * Le cookie de session (JAMII_SESSION), tel qu'un vrai serveur l'envoie au navigateur : `Secure` par défaut, donc en
 * production. Seul SESSION_COOKIE_SECURE=false le retire, pour se connecter avec Safari sur http://localhost.
 */
class CookieSessionTest {

	/** La connexion GitHub ouvre une session (elle y garde l'état OAuth) : c'est elle qui pose le cookie. */
	static String cookieDeSession(int port) throws Exception {
		HttpResponse<Void> reponse = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build()
			.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/auth/github?retour=/")).build(),
					HttpResponse.BodyHandlers.discarding());
		return reponse.headers().allValues("Set-Cookie").stream()
			.filter(c -> c.startsWith("JAMII_SESSION="))
			.findFirst()
			.orElseThrow();
	}

	@Nested
	@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
	@Import(TestcontainersConfiguration.class)
	class ParDefaut {

		@LocalServerPort
		int port;

		@Test
		void le_cookie_est_secure_httponly_et_samesite_lax() throws Exception {
			assertThat(cookieDeSession(port)).contains("; Secure").contains("; HttpOnly").contains("SameSite=Lax");
		}

	}

	@Nested
	@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT, properties = "SESSION_COOKIE_SECURE=false")
	@Import(TestcontainersConfiguration.class)
	class EnLocalPourSafari {

		@LocalServerPort
		int port;

		@Test
		void sans_secure_mais_toujours_httponly_et_samesite_lax() throws Exception {
			assertThat(cookieDeSession(port)).doesNotContain("Secure").contains("; HttpOnly").contains("SameSite=Lax");
		}

	}

}
