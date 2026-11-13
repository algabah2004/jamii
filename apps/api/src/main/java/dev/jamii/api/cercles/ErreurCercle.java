package dev.jamii.api.cercles;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;

/** Une erreur du contrat cercles.yaml : statut HTTP, `code` métier et, pour une validation, le détail par champ. */
class ErreurCercle extends RuntimeException {

	final HttpStatus statut;

	final String code;

	final List<Map<String, String>> erreurs;

	/** Secondes à attendre (en-tête Retry-After), seulement pour un 429. */
	final long attenteSecondes;

	ErreurCercle(HttpStatus statut, String code, String titre) {
		this(statut, code, titre, null, 0);
	}

	ErreurCercle(HttpStatus statut, String code, String titre, List<Map<String, String>> erreurs, long attenteSecondes) {
		super(titre, null, false, false);
		this.statut = statut;
		this.code = code;
		this.erreurs = erreurs;
		this.attenteSecondes = attenteSecondes;
	}

	static ErreurCercle introuvable() {
		return new ErreurCercle(HttpStatus.NOT_FOUND, "introuvable", "Ressource introuvable");
	}

	static ErreurCercle interdit() {
		return new ErreurCercle(HttpStatus.FORBIDDEN, "interdit", "Accès refusé");
	}

	static ErreurCercle conflit(String code, String titre) {
		return new ErreurCercle(HttpStatus.CONFLICT, code, titre);
	}

}
