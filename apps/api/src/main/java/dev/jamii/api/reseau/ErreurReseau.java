package dev.jamii.api.reseau;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.http.HttpStatus;

import dev.jamii.api.auth.Annuaire;
import dev.jamii.api.auth.Compte;

/** Une erreur du contrat reseau.yaml : statut HTTP, `code` métier et, pour une validation, le détail par champ. */
class ErreurReseau extends RuntimeException {

	final HttpStatus statut;

	final String code;

	final List<Map<String, String>> erreurs;

	/** Secondes à attendre (en-tête Retry-After), seulement pour un 429. */
	final long attenteSecondes;

	/** Pour `compte_demo` seulement : la personne de la démo à qui écrire à la place ({@code id}, {@code nom}). */
	final Map<String, Object> ecrireA;

	private ErreurReseau(HttpStatus statut, String code, String titre, List<Map<String, String>> erreurs,
			long attenteSecondes) {
		this(statut, code, titre, erreurs, attenteSecondes, null);
	}

	private ErreurReseau(HttpStatus statut, String code, String titre, List<Map<String, String>> erreurs,
			long attenteSecondes, Map<String, Object> ecrireA) {
		super(titre, null, false, false);
		this.statut = statut;
		this.code = code;
		this.erreurs = erreurs;
		this.attenteSecondes = attenteSecondes;
		this.ecrireA = ecrireA;
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

	/**
	 * Un compte démo de visiteur n'écrit et n'appelle que les personnes de la démo (#157). L'erreur dit à qui écrire à la
	 * place, pour que le visiteur essaie quand même : sans ça, il croit à une panne.
	 */
	static ErreurReseau compteDemo(Optional<Compte> contact) {
		return new ErreurReseau(HttpStatus.FORBIDDEN, "compte_demo", "Le compte démo ne peut écrire qu'aux personnes de la démo", null, 0,
				contact.<Map<String, Object>>map(c -> Map.of("id", c.id(), "nom", c.nom())).orElse(null));
	}

	/** Lève {@link #compteDemo} si `moi` est un compte de la démo et `autre` un vrai compte. */
	static void verifierDemo(Annuaire annuaire, UUID moi, UUID autre) {
		if (annuaire.estDeDemonstration(moi) && !annuaire.estDeDemonstration(autre)) {
			throw compteDemo(annuaire.contactDeDemo());
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
