package dev.jamii.api.auth;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/** Les refus de la sécurité (401, 403) au format Erreur du contrat (RFC 9457), jamais une page HTML ni une redirection. */
@Component
class ErreursSecurite {

	private final JsonMapper json;

	ErreursSecurite(JsonMapper json) {
		this.json = json;
	}

	void nonConnecte(HttpServletRequest requete, HttpServletResponse reponse) throws IOException {
		ecrire(requete, reponse, HttpStatus.UNAUTHORIZED, "non_connecte", "Connexion requise");
	}

	void interdit(HttpServletRequest requete, HttpServletResponse reponse) throws IOException {
		ecrire(requete, reponse, HttpStatus.FORBIDDEN, "interdit", "Accès refusé");
	}

	private void ecrire(HttpServletRequest requete, HttpServletResponse reponse, HttpStatus statut, String code, String titre)
			throws IOException {
		Map<String, Object> erreur = new LinkedHashMap<>();
		erreur.put("type", "urn:jamii:erreur:" + code);
		erreur.put("title", titre);
		erreur.put("status", statut.value());
		erreur.put("code", code);
		erreur.put("instance", requete.getRequestURI());
		reponse.setStatus(statut.value());
		// JSON est toujours en UTF-8 (RFC 8259) : pas de paramètre charset, comme le reste de l'API.
		reponse.setContentType("application/problem+json");
		json.writeValue(reponse.getOutputStream(), erreur);
	}

}
