package dev.jamii.api.reseau;

import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.jamii.api.auth.Annuaire;
import dev.jamii.api.auth.UtilisateurConnecte;

/** « … est en train d'écrire » (contracts/reseau.yaml, #157). */
@RestController
class EcritureController {

	record Etat(boolean ecrit) {
	}

	private final Ecriture ecriture;

	private final Annuaire annuaire;

	EcritureController(Ecriture ecriture, Annuaire annuaire) {
		this.ecriture = ecriture;
		this.annuaire = annuaire;
	}

	/** J'écris à cette personne. Seulement à une personne qui existe, et pas à moi-même. */
	@PostMapping("/api/conversations/{personneId}/ecriture")
	ResponseEntity<Void> signaler(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable String personneId) {
		UUID autre = uuid(personneId);
		if (autre.equals(moi.id()) || annuaire.lire(autre).isEmpty()) {
			throw ErreurReseau.introuvable();
		}
		ecriture.signaler(moi.id(), autre);
		return ResponseEntity.noContent().build();
	}

	/** Cette personne est-elle en train de m'écrire ? Lu toutes les 2 secondes, sans base de données. */
	@GetMapping("/api/conversations/{personneId}/ecriture")
	ResponseEntity<Etat> lire(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable String personneId) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Etat(ecriture.ecrit(uuid(personneId), moi.id())));
	}

	@ExceptionHandler(ErreurReseau.class)
	ResponseEntity<ProblemDetail> erreur(ErreurReseau e) {
		return ReseauController.probleme(e);
	}

	private static UUID uuid(String valeur) {
		try {
			return UUID.fromString(valeur);
		}
		catch (IllegalArgumentException e) {
			throw ErreurReseau.introuvable();
		}
	}

}
