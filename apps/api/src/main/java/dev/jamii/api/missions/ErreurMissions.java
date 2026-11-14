package dev.jamii.api.missions;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;

/** Une erreur du contrat missions.yaml : statut HTTP, `code` métier et, pour une validation, le détail par champ. */
class ErreurMissions extends RuntimeException {

	final HttpStatus statut;

	final String code;

	final List<Map<String, String>> erreurs;

	/** Secondes à attendre (en-tête Retry-After), seulement pour un 429. */
	final long attenteSecondes;

	private ErreurMissions(HttpStatus statut, String code, String titre, List<Map<String, String>> erreurs, long attenteSecondes) {
		super(titre, null, false, false);
		this.statut = statut;
		this.code = code;
		this.erreurs = erreurs;
		this.attenteSecondes = attenteSecondes;
	}

	static ErreurMissions nonConnecte() {
		return new ErreurMissions(HttpStatus.UNAUTHORIZED, "non_connecte", "Connexion requise", null, 0);
	}

	/** 404 : une mission qui n'existe pas, ou que la personne n'a pas le droit de savoir exister. */
	static ErreurMissions introuvable() {
		return new ErreurMissions(HttpStatus.NOT_FOUND, "introuvable", "Ressource introuvable", null, 0);
	}

	static ErreurMissions interdit() {
		return new ErreurMissions(HttpStatus.FORBIDDEN, "interdit", "Accès refusé", null, 0);
	}

	static ErreurMissions conflit(String code, String titre) {
		return new ErreurMissions(HttpStatus.CONFLICT, code, titre, null, 0);
	}

	static ErreurMissions validation(List<Map<String, String>> erreurs) {
		return new ErreurMissions(HttpStatus.BAD_REQUEST, "validation", "Certains champs sont invalides", erreurs, 0);
	}

	static ErreurMissions limiteAtteinte(long attenteSecondes) {
		return new ErreurMissions(HttpStatus.TOO_MANY_REQUESTS, "limite_atteinte", "Trop de missions publiées aujourd'hui", null,
				Math.max(1, attenteSecondes));
	}

}
