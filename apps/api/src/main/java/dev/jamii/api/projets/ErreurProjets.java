package dev.jamii.api.projets;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;

/** Une erreur du contrat projets.yaml : statut HTTP, `code` métier et, pour une validation, le détail par champ. */
class ErreurProjets extends RuntimeException {

	final HttpStatus statut;

	final String code;

	final List<Map<String, String>> erreurs;

	/** Secondes à attendre (en-tête Retry-After), seulement pour un 429. */
	final long attenteSecondes;

	ErreurProjets(HttpStatus statut, String code, String titre, List<Map<String, String>> erreurs, long attenteSecondes) {
		super(titre, null, false, false);
		this.statut = statut;
		this.code = code;
		this.erreurs = erreurs;
		this.attenteSecondes = attenteSecondes;
	}

	static ErreurProjets introuvable() {
		return new ErreurProjets(HttpStatus.NOT_FOUND, "introuvable", "Ressource introuvable", null, 0);
	}

	static ErreurProjets interdit() {
		return new ErreurProjets(HttpStatus.FORBIDDEN, "interdit", "Accès refusé", null, 0);
	}

	static ErreurProjets conflit(String code, String titre) {
		return new ErreurProjets(HttpStatus.CONFLICT, code, titre, null, 0);
	}

	static ErreurProjets validation(List<Map<String, String>> erreurs) {
		return new ErreurProjets(HttpStatus.BAD_REQUEST, "validation", "Certains champs sont invalides", erreurs, 0);
	}

	static ErreurProjets validation(String champ, String code) {
		return validation(List.of(Map.of("champ", champ, "code", code)));
	}

}
