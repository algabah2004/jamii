package dev.jamii.api.profils;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

/**
 * Adaptateur GitHub : une seule requête par génération (GET /users/{login}/repos), en lecture, sans données privées.
 * Sans jeton, GitHub limite à 60 requêtes par heure et par adresse IP, partagées par tous les utilisateurs du serveur :
 * en production, renseigner GITHUB_TOKEN (un jeton sans aucune permission suffit, il passe la limite à 5 000).
 */
class SourceGithubHttp implements SourceGithub {

	private static final Duration DELAI = Duration.ofSeconds(10);

	private final RestClient github;

	SourceGithubHttp(String urlApi, String jeton) {
		var requetes = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(DELAI).build());
		requetes.setReadTimeout(DELAI);
		var construction = RestClient.builder()
			.baseUrl(urlApi)
			.requestFactory(requetes)
			.defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
			.defaultHeader("X-GitHub-Api-Version", "2022-11-28")
			.defaultHeader(HttpHeaders.USER_AGENT, "jamii-api");
		if (jeton != null && !jeton.isBlank()) {
			construction.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + jeton.strip());
		}
		this.github = construction.build();
	}

	@Override
	public List<Depot> depotsPublics(String login) {
		try {
			DepotGithubBrut[] depots = github.get()
				.uri("/users/{login}/repos?type=owner&sort=pushed&per_page=100", login)
				.retrieve()
				.body(DepotGithubBrut[].class);
			return depots == null ? List.of() : List.of(depots).stream().map(DepotGithubBrut::versDepot).toList();
		}
		catch (HttpClientErrorException e) {
			if (e.getStatusCode() == HttpStatus.NOT_FOUND) {
				throw new CompteIntrouvable(login);
			}
			throw e;
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record DepotGithubBrut(String name, @JsonProperty("html_url") String htmlUrl, String description, String language,
			@JsonProperty("stargazers_count") int etoiles, int size, boolean fork, List<String> topics,
			@JsonProperty("pushed_at") Instant pousseLe) {

		Depot versDepot() {
			return new Depot(name, htmlUrl, description, language, etoiles, size, fork, topics == null ? List.of() : topics,
					pousseLe);
		}

	}

}
