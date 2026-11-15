package dev.jamii.api.hackathons;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;

/** Une erreur du contrat hackathons.yaml : statut HTTP, `code` métier et, pour une validation, le détail par champ. */
class ErreurHackathons extends RuntimeException {

	final HttpStatus statut;

	final String code;

	final List<Map<String, String>> erreurs;

	/** Secondes à attendre (en-tête Retry-After), seulement pour un 429. */
	final long attenteSecondes;

	private ErreurHackathons(HttpStatus statut, String code, String titre, List<Map<String, String>> erreurs, long attenteSecondes) {
		super(titre, null, false, false);
		this.statut = statut;
		this.code = code;
		this.erreurs = erreurs;
		this.attenteSecondes = attenteSecondes;
	}

	/** 404 : un hackathon qui n'existe pas, ou que la personne n'a pas le droit de savoir exister. */
	static ErreurHackathons introuvable() {
		return new ErreurHackathons(HttpStatus.NOT_FOUND, "introuvable", "Ressource introuvable", null, 0);
	}

	static ErreurHackathons nonConnecte() {
		return new ErreurHackathons(HttpStatus.UNAUTHORIZED, "non_connecte", "Connexion requise", null, 0);
	}

	static ErreurHackathons interdit() {
		return new ErreurHackathons(HttpStatus.FORBIDDEN, "interdit", "Accès refusé", null, 0);
	}

	/** 403 : un juré face au projet de sa propre équipe. */
	static ErreurHackathons conflitInterets() {
		return new ErreurHackathons(HttpStatus.FORBIDDEN, "conflit_interets", "Tu ne notes pas le projet de ta propre équipe", null, 0);
	}

	static ErreurHackathons conflit(String code, String titre) {
		return new ErreurHackathons(HttpStatus.CONFLICT, code, titre, null, 0);
	}

	static ErreurHackathons validation(List<Map<String, String>> erreurs) {
		return new ErreurHackathons(HttpStatus.BAD_REQUEST, "validation", "Certains champs sont invalides", erreurs, 0);
	}

	static ErreurHackathons validation(String champ, String code) {
		return validation(List.of(Map.of("champ", champ, "code", code)));
	}

	static ErreurHackathons limiteAtteinte(String titre, long attenteSecondes) {
		return new ErreurHackathons(HttpStatus.TOO_MANY_REQUESTS, "limite_atteinte", titre, null, Math.max(1, attenteSecondes));
	}

}
