package dev.jamii.api.cercles;

import java.net.http.HttpClient;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import tools.jackson.databind.JsonNode;

/**
 * Le dépôt de chaque tour, créé dans une organisation GitHub (`DEPOT_CODE=github`, docs/priorites.md). Le jeton
 * (`GITHUB_DEPOTS_JETON`) doit pouvoir créer des dépôts dans l'organisation et y inviter des membres ; il ne sort jamais
 * de l'API et n'apparaît dans aucun journal.
 * <p>
 * Rejouable : si le dépôt existe déjà (tirage relancé après une panne), il est relu au lieu d'être recréé.
 */
class DepotsGithub implements DepotCode {

	private static final Logger journal = LoggerFactory.getLogger(DepotsGithub.class);

	/** Les logins GitHub valides : seuls ceux-là entrent dans une adresse de l'API GitHub. */
	static final Pattern LOGIN = Pattern.compile("^[A-Za-z0-9](?:[A-Za-z0-9-]{0,37}[A-Za-z0-9])?$");

	private static final Pattern ORGANISATION = LOGIN;

	private final RestClient client;

	private final String organisation;

	private final boolean prives;

	DepotsGithub(String apiUrl, String organisation, String jeton, boolean prives, Duration delai) {
		if (organisation == null || !ORGANISATION.matcher(organisation).matches() || jeton == null || jeton.isBlank()) {
			throw new IllegalStateException("DEPOT_CODE=github demande GITHUB_DEPOTS_ORGANISATION et GITHUB_DEPOTS_JETON");
		}
		var transport = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(delai).build());
		transport.setReadTimeout(delai);
		this.client = RestClient.builder()
			.baseUrl(apiUrl)
			.requestFactory(transport)
			.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + jeton)
			.defaultHeader(HttpHeaders.ACCEPT, "application/vnd.github+json")
			.defaultHeader("X-GitHub-Api-Version", "2022-11-28")
			.defaultHeader(HttpHeaders.USER_AGENT, "jamii-api")
			.build();
		this.organisation = organisation;
		this.prives = prives;
	}

	@Override
	public Optional<DepotDuTour> creerDepot(DemandeDepot demande) {
		String nom = nom(demande);
		JsonNode depot = creerOuRelire(nom, demande);
		for (String login : demande.loginsGithub()) {
			inviter(nom, login);
		}
		return Optional.of(new DepotDuTour("github", depot.get("html_url").asString(), depot.get("clone_url").asString()));
	}

	private JsonNode creerOuRelire(String nom, DemandeDepot demande) {
		Map<String, Object> corps = new LinkedHashMap<>();
		corps.put("name", nom);
		corps.put("description", description(demande));
		corps.put("private", prives);
		// Un premier commit (README) : le dépôt se clone et se déploie tout de suite.
		corps.put("auto_init", true);
		try {
			return client.post()
				.uri("/orgs/{org}/repos", organisation)
				.contentType(MediaType.APPLICATION_JSON)
				.body(corps)
				.retrieve()
				.body(JsonNode.class);
		}
		catch (HttpClientErrorException e) {
			// 422 : le nom est déjà pris dans l'organisation, donc ce tour a déjà son dépôt.
			if (e.getStatusCode().value() != 422) {
				throw e;
			}
			return client.get().uri("/repos/{org}/{nom}", organisation, nom).retrieve().body(JsonNode.class);
		}
	}

	@Override
	public boolean prives() {
		return prives;
	}

	/**
	 * Les 10 derniers commits et les 10 demandes de fusion mises à jour le plus récemment. On ne lit que les dépôts de
	 * notre organisation : une adresse qui en sort (base modifiée à la main, autre fournisseur) n'est jamais appelée.
	 */
	@Override
	public Optional<Activite> activite(DepotDuTour depot) {
		var m = DEPOT_DE_L_ORGANISATION.matcher(depot.url() == null ? "" : depot.url());
		if (!"github".equals(depot.fournisseur()) || !m.matches() || !m.group(1).equalsIgnoreCase(organisation)) {
			return Optional.empty();
		}
		String nom = m.group(2);
		try {
			JsonNode commits = client.get().uri("/repos/{org}/{nom}/commits?per_page=10", organisation, nom)
				.retrieve().body(JsonNode.class);
			JsonNode demandes = client.get()
				.uri("/repos/{org}/{nom}/pulls?state=all&sort=updated&direction=desc&per_page=10", organisation, nom)
				.retrieve().body(JsonNode.class);
			List<Commit> lesCommits = new ArrayList<>();
			for (JsonNode c : commits) {
				String url = texte(c.path("html_url"));
				String date = texte(c.path("commit").path("author").path("date"));
				if (url == null || !url.startsWith("https://") || date == null) {
					continue;
				}
				lesCommits.add(new Commit(texte(c.path("sha")), premiereLigne(texte(c.path("commit").path("message"))),
						auteur(c.path("author")), Instant.parse(date), url));
			}
			List<DemandeDeFusion> lesDemandes = new ArrayList<>();
			for (JsonNode d : demandes) {
				String url = texte(d.path("html_url"));
				if (url == null || !url.startsWith("https://")) {
					continue;
				}
				String etat = texte(d.path("merged_at")) != null ? "fusionnee" : "open".equals(texte(d.path("state"))) ? "ouverte" : "fermee";
				lesDemandes.add(new DemandeDeFusion(d.path("number").asInt(), premiereLigne(texte(d.path("title"))), etat,
						auteur(d.path("user")), Instant.parse(texte(d.path("updated_at"))), url));
			}
			return Optional.of(new Activite(List.copyOf(lesCommits), List.copyOf(lesDemandes)));
		}
		catch (RuntimeException e) {
			// GitHub lent, en panne, jeton expiré ou dépôt supprimé : la page le dit, sans faire tomber la réponse.
			journal.warn("Activité du dépôt d'un tour illisible : {}", e.getClass().getSimpleName());
			return Optional.empty();
		}
	}

	/** `https://github.com/<organisation>/<dépôt>`, sans rien après. */
	private static final Pattern DEPOT_DE_L_ORGANISATION = Pattern
		.compile("^https://github\\.com/([A-Za-z0-9-]{1,39})/([A-Za-z0-9._-]{1,100})/?$");

	private static Auteur auteur(JsonNode compte) {
		String login = texte(compte.path("login"));
		return login == null ? null : new Auteur(login, texte(compte.path("avatar_url")));
	}

	private static String texte(JsonNode n) {
		return n == null || n.isMissingNode() || n.isNull() ? null : n.asString();
	}

	/** La première ligne, sans caractères de contrôle, coupée à 200 caractères. */
	static String premiereLigne(String texte) {
		if (texte == null) {
			return "";
		}
		String ligne = texte.lines().findFirst().orElse("").replaceAll("\\p{Cntrl}", " ").strip();
		return ligne.length() > 200 ? ligne.substring(0, 199) + "…" : ligne;
	}

	/** Une invitation qui échoue (login renommé, compte supprimé) n'empêche ni le dépôt ni les autres invitations. */
	private void inviter(String nom, String login) {
		if (login == null || !LOGIN.matcher(login).matches()) {
			return;
		}
		try {
			client.put()
				.uri("/repos/{org}/{nom}/collaborators/{login}", organisation, nom, login)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("permission", "push"))
				.retrieve()
				.toBodilessEntity();
		}
		catch (RestClientException e) {
			journal.warn("Invitation au dépôt d'un tour impossible : {}", e.getClass().getSimpleName());
		}
	}

	/**
	 * `cercle-<nom du cercle>-tour-<n>-<8 caractères de l'identifiant>` : lisible, unique par tour, toujours le même pour
	 * le même tour (ce qui rend la création rejouable), et dans l'alphabet des noms GitHub.
	 */
	static String nom(DemandeDepot demande) {
		String sansAccents = Normalizer.normalize(demande.cercleNom(), Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		String slug = sansAccents.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
		if (slug.length() > 40) {
			slug = slug.substring(0, 40).replaceAll("-+$", "");
		}
		String debut = slug.isEmpty() ? "cercle" : "cercle-" + slug;
		return debut + "-tour-" + demande.numero() + "-" + demande.cercleId().toString().substring(0, 8);
	}

	private static String description(DemandeDepot demande) {
		String titre = demande.titre() == null ? "" : demande.titre().replaceAll("\\p{Cntrl}", " ").strip();
		String texte = "Tour " + demande.numero() + " du cercle « " + demande.cercleNom().strip() + " » sur Jamii"
				+ (titre.isEmpty() ? "" : " : " + titre);
		return texte.length() > 350 ? texte.substring(0, 350) : texte;
	}

}
