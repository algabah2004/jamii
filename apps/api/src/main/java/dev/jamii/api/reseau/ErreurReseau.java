package dev.jamii.api.reseau;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;

/** Une erreur du contrat reseau.yaml : statut HTTP, `code` métier et, pour une validation, le détail par champ. */
class ErreurReseau extends RuntimeException {

	final HttpStatus statut;

	final String code;

	final List<Map<String, String>> erreurs;

	/** Secondes à attendre (en-tête Retry-After), seulement pour un 429. */
	final long attenteSecondes;

	private ErreurReseau(HttpStatus statut, String code, String titre, List<Map<String, String>> erreurs,
			long attenteSecondes) {
		super(titre, null, false, false);
		this.statut = statut;
		this.code = code;
		this.erreurs = erreurs;
		this.attenteSecondes = attenteSecondes;
	}

	static ErreurReseau validation(String champ, String code) {
		return new ErreurReseau(HttpStatus.BAD_REQUEST, "validation", "Certains champs sont invalides",
				List.of(Map.of("champ", champ, "code", code)), 0);
	}

	static ErreurReseau introuvable() {
		return new ErreurReseau(HttpStatus.NOT_FOUND, "introuvable", "Ressource introuvable", null, 0);
	}

	static ErreurReseau interdit() {
		return new ErreurReseau(HttpStatus.FORBIDDEN, "interdit", "Accès refusé", null, 0);
	}

	/** Un compte démo de visiteur n'écrit et n'appelle que les personnes de la démo (#157). */
	static ErreurReseau compteDemo() {
		return new ErreurReseau(HttpStatus.FORBIDDEN, "compte_demo", "Le compte démo ne peut écrire qu'aux personnes de la démo", null, 0);
	}

	/** Lève {@link #compteDemo()} si `moi` est le compte démo et `autre` un vrai compte. */
	static void verifierDemo(dev.jamii.api.auth.Annuaire annuaire, java.util.UUID moi, java.util.UUID autre) {
		if (annuaire.estDeDemonstration(moi) && !annuaire.estDeDemonstration(autre)) {
			throw compteDemo();
		}
	}

	static ErreurReseau conflit(String code, String titre) {
		return new ErreurReseau(HttpStatus.CONFLICT, code, titre, null, 0);
	}

	static ErreurReseau tropDeRequetes(long attenteSecondes) {
		return new ErreurReseau(HttpStatus.TOO_MANY_REQUESTS, "trop_de_requetes", "Trop de messages, réessaie plus tard",
				null, attenteSecondes);
	}

}
