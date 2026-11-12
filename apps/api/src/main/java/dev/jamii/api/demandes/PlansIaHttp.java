package dev.jamii.api.demandes;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import tools.jackson.databind.JsonNode;

/**
 * Le service IA de l'équipe, en HTTP (contracts/ia.yaml). Appelé par l'API seulement, avec le jeton interne
 * `IA_JETON_INTERNE` : le navigateur ne le contacte jamais. Délais courts : l'IA est lente, mais le dépôt et la lecture
 * d'une tâche ne le sont pas, et une requête de l'utilisateur ne doit pas attendre un service qui ne répond pas.
 */
class PlansIaHttp implements PlansIa {

	private final RestClient client;

	PlansIaHttp(String url, String jeton, Duration delai) {
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
	}

	@Override
	public UUID deposer(UUID cle, Entree entree) {
		Map<String, Object> corps = new LinkedHashMap<>();
		corps.put("source", "demande_pme");
		corps.put("titre", entree.titre());
		corps.put("description", entree.description());
		corps.put("langue", "fr");
		if (entree.dureeSemaines() != null) {
			corps.put("dureeSemaines", entree.dureeSemaines());
		}
		// 202 : tâche créée. 200 : la même clé avait déjà servi, la tâche existante est renvoyée.
		JsonNode tache = client.post()
			.uri("/taches")
			.header("Idempotency-Key", cle.toString())
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("type", "plan_projet", "entree", corps))
			.retrieve()
			.body(JsonNode.class);
		return UUID.fromString(tache.get("id").asString());
	}

	@Override
	public Etat lire(UUID tacheId) {
		JsonNode tache = client.get().uri("/taches/{id}", tacheId).retrieve().body(JsonNode.class);
		return new Etat(tache.get("statut").asString(), texte(tache, "origine"), tache.get("resultat"),
				texte(tache, "erreurCode"));
	}

	private static String texte(JsonNode noeud, String champ) {
		JsonNode valeur = noeud.get(champ);
		return valeur == null || valeur.isNull() ? null : valeur.asString();
	}

}
