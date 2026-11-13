package dev.jamii.api.profils;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;

/**
 * Le service IA de l'équipe, en HTTP (contracts/ia.yaml) : dépose une tâche `analyse_profil`, puis la relit jusqu'à
 * la fin. Appelé en tâche de fond seulement : aucune requête d'utilisateur n'attend l'IA.
 */
class CartesIaHttp implements CartesIa {

	private final RestClient client;

	private final Duration attenteMax;

	private final Duration intervalle;

	CartesIaHttp(String url, String jeton, Duration delai, Duration attenteMax, Duration intervalle) {
		if (url == null || url.isBlank() || jeton == null || jeton.isBlank()) {
			throw new IllegalStateException("IA_FOURNISSEUR=service demande IA_URL et IA_JETON_INTERNE");
		}
		var transport = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(delai).build());
		transport.setReadTimeout(delai);
		this.client = RestClient.builder()
			.baseUrl(url)
			.requestFactory(transport)
			.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + jeton)
			.build();
		this.attenteMax = attenteMax;
		this.intervalle = intervalle;
	}

	@Override
	public List<Proposee> proposer(UUID cle, Entree entree) {
		Map<String, Object> corps = Map.of("langages", entree.langages(), "depots", entree.depots(),
				"competencesDeclarees", entree.competencesDeclarees(), "langue", "fr");
		JsonNode tache = client.post()
			.uri("/taches")
			.header("Idempotency-Key", cle.toString())
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("type", "analyse_profil", "entree", corps))
			.retrieve()
			.body(JsonNode.class);
		String id = tache.get("id").asString();
		Instant limite = Instant.now().plus(attenteMax);
		while (true) {
			JsonNode etat = client.get().uri("/taches/{id}", id).retrieve().body(JsonNode.class);
			String statut = etat.get("statut").asString();
			if ("terminee".equals(statut)) {
				return CartesIa.nettoyer(lire(etat.path("resultat").path("competences")), entree);
			}
			if ("echouee".equals(statut) || Instant.now().isAfter(limite)) {
				throw new IllegalStateException("Carte de compétences non obtenue : " + statut);
			}
			try {
				Thread.sleep(intervalle);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException("Attente interrompue", e);
			}
		}
	}

	private static List<Proposee> lire(JsonNode competences) {
		List<Proposee> brutes = new ArrayList<>();
		for (JsonNode c : competences) {
			List<String> preuves = new ArrayList<>();
			c.path("preuves").forEach(p -> preuves.add(p.asString()));
			brutes.add(new Proposee(texte(c, "competence"), texte(c, "niveau"), preuves, texte(c, "raison")));
		}
		return brutes;
	}

	private static String texte(JsonNode noeud, String champ) {
		JsonNode valeur = noeud.get(champ);
		return valeur == null || !valeur.isString() ? null : valeur.asString();
	}

}
