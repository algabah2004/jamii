package dev.jamii.api.profils;

import java.net.URI;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

/** Une erreur du module, rendue au format Erreur du contrat (RFC 9457). Le front se base sur {@code code}. */
class ErreurProfil extends RuntimeException {

	record Champ(String champ, String code) {
	}

	final HttpStatus statut;

	final String code;

	final List<Champ> erreurs;

	final Integer reessayerDans;

	ErreurProfil(HttpStatus statut, String code) {
		this(statut, code, List.of(), null);
	}

	private ErreurProfil(HttpStatus statut, String code, List<Champ> erreurs, Integer reessayerDans) {
		super(code);
		this.statut = statut;
		this.code = code;
		this.erreurs = erreurs;
		this.reessayerDans = reessayerDans;
	}

	static ErreurProfil validation(List<Champ> erreurs) {
		return new ErreurProfil(HttpStatus.BAD_REQUEST, "validation", List.copyOf(erreurs), null);
	}

	static ErreurProfil introuvable() {
		return new ErreurProfil(HttpStatus.NOT_FOUND, "introuvable");
	}

	static ErreurProfil tropDeRequetes(int secondes) {
		return new ErreurProfil(HttpStatus.TOO_MANY_REQUESTS, "trop_de_requetes", List.of(), secondes);
	}

	/** Limité aux contrôleurs du module : les autres modules gardent leurs propres règles. */
	@RestControllerAdvice(basePackageClasses = ErreurProfil.class)
	static class Rendu {

		@ExceptionHandler(ErreurProfil.class)
		ResponseEntity<ProblemDetail> erreur(ErreurProfil e, HttpServletRequest requete) {
			ProblemDetail probleme = ProblemDetail.forStatus(e.statut);
			probleme.setType(URI.create("urn:jamii:erreur:" + e.code));
			probleme.setTitle(e.statut.getReasonPhrase());
			probleme.setInstance(URI.create(requete.getRequestURI()));
			probleme.setProperty("code", e.code);
			if (!e.erreurs.isEmpty()) {
				probleme.setProperty("erreurs", e.erreurs);
			}
			var reponse = ResponseEntity.status(e.statut);
			if (e.reessayerDans != null) {
				reponse.header(HttpHeaders.RETRY_AFTER, String.valueOf(e.reessayerDans));
			}
			return reponse.body(probleme);
		}

		@ExceptionHandler(HttpMessageNotReadableException.class)
		ResponseEntity<ProblemDetail> illisible(HttpServletRequest requete) {
			return erreur(validation(List.of(new Champ("", "json_invalide"))), requete);
		}

		@ExceptionHandler(MethodArgumentTypeMismatchException.class)
		ResponseEntity<ProblemDetail> mauvaisType(MethodArgumentTypeMismatchException e, HttpServletRequest requete) {
			// Un identifiant qui n'est pas un UUID ne désigne aucun profil.
			if (e.getRequiredType() == java.util.UUID.class) {
				return erreur(introuvable(), requete);
			}
			return erreur(validation(List.of(new Champ(e.getName(), "format_invalide"))), requete);
		}

	}

}
