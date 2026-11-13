package dev.jamii.api.communautes;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;

/** Les erreurs RFC 9457 du module, avec le `code` métier du contrat (contracts/communautes.yaml). */
final class Problemes {

	private Problemes() {
	}

	static ResponseEntity<ProblemDetail> probleme(HttpStatus statut, String code, String titre,
			List<Map<String, String>> erreurs) {
		ProblemDetail erreur = ProblemDetail.forStatus(statut);
		erreur.setType(URI.create("urn:jamii:erreur:" + code));
		erreur.setTitle(titre);
		erreur.setProperty("code", code);
		if (erreurs != null) {
			erreur.setProperty("erreurs", erreurs);
		}
		return ResponseEntity.status(statut).body(erreur);
	}

	static ResponseEntity<ProblemDetail> introuvable() {
		return probleme(HttpStatus.NOT_FOUND, "introuvable", "Ressource introuvable", null);
	}

	static ResponseEntity<ProblemDetail> conflit(String code, String titre) {
		return probleme(HttpStatus.CONFLICT, code, titre, null);
	}

	static ResponseEntity<ProblemDetail> interdit(String code, String titre) {
		return probleme(HttpStatus.FORBIDDEN, code, titre, null);
	}

	static ResponseEntity<ProblemDetail> validation(String champ, String code) {
		return probleme(HttpStatus.BAD_REQUEST, "validation", "Certains champs sont invalides",
				List.of(Map.of("champ", champ, "code", code)));
	}

	static ResponseEntity<?> tropDeRequetes(long attente, String titre) {
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header("Retry-After", String.valueOf(attente))
			.body(probleme(HttpStatus.TOO_MANY_REQUESTS, "trop_de_requetes", titre, null).getBody());
	}

}
