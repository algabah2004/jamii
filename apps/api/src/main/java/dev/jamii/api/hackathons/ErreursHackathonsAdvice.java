package dev.jamii.api.hackathons;

import java.net.URI;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Les erreurs du contrat hackathons.yaml, au format RFC 9457 avec `code`, pour les contrôleurs du module. */
@RestControllerAdvice(assignableTypes = { HackathonsController.class, InscriptionsController.class })
class ErreursHackathonsAdvice {

	@ExceptionHandler(ErreurHackathons.class)
	ResponseEntity<ProblemDetail> erreur(ErreurHackathons e) {
		ProblemDetail probleme = ProblemDetail.forStatus(e.statut);
		probleme.setType(URI.create("urn:jamii:erreur:" + e.code));
		probleme.setTitle(e.getMessage());
		probleme.setProperty("code", e.code);
		if (e.erreurs != null) {
			probleme.setProperty("erreurs", e.erreurs);
		}
		var reponse = ResponseEntity.status(e.statut);
		if (e.attenteSecondes > 0) {
			reponse = reponse.header(HttpHeaders.RETRY_AFTER, String.valueOf(e.attenteSecondes));
		}
		return reponse.body(probleme);
	}

}
