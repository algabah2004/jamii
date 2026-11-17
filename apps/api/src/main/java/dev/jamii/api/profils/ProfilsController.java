package dev.jamii.api.profils;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.jamii.api.auth.Annuaire;
import dev.jamii.api.auth.Compte;
import dev.jamii.api.auth.UtilisateurConnecte;

/** Lire le profil d'une autre personne (contrat : tag « profils » de contracts/profils.yaml). */
@RestController
class ProfilsController {

	private final ProfilRepository profils;

	private final Annuaire comptes;

	private final Preuves preuves;

	ProfilsController(ProfilRepository profils, Annuaire comptes, Preuves preuves) {
		this.profils = profils;
		this.comptes = comptes;
		this.preuves = preuves;
	}

	/**
	 * Un profil non publié répond 404 aux autres : on ne révèle pas qu'il existe (api-conventions.md §5). Un profil
	 * publié se lit sans compte (#159) : c'est le lien partagé et le QR code de la carte. Jamais l'e-mail.
	 */
	@GetMapping("/api/profils/{utilisateurId}")
	@Transactional(readOnly = true)
	Vues.ProfilPublic lire(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable UUID utilisateurId) {
		Profil profil = visible(moi, utilisateurId);
		Compte compte = comptes.lire(utilisateurId).orElseThrow(ErreurProfil::introuvable);
		return Vues.ProfilPublic.depuis(profil, compte, preuves.resume(utilisateurId));
	}

	/**
	 * Le détail d'un point de l'indice (contracts/profils.yaml, `listerPreuves`) : réservé aux personnes connectées,
	 * car un avis cite le nom de son auteur. `curseur` : combien de preuves déjà lues.
	 */
	@GetMapping("/api/profils/{utilisateurId}/preuves")
	@Transactional(readOnly = true)
	Vues.Page<Vues.Preuve> preuves(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable UUID utilisateurId,
			@RequestParam(required = false) String type, @RequestParam(required = false) String curseur,
			@RequestParam(defaultValue = "20") int limite) {
		if (moi == null) {
			throw new ErreurProfil(HttpStatus.UNAUTHORIZED, "non_connecte");
		}
		if (type != null && !Preuves.TYPES.contains(type)) {
			throw new ErreurProfil(HttpStatus.BAD_REQUEST, "validation");
		}
		if (limite < 1 || limite > 100) {
			throw new ErreurProfil(HttpStatus.BAD_REQUEST, "validation");
		}
		int apres = 0;
		if (curseur != null) {
			try {
				apres = Integer.parseInt(curseur);
			}
			catch (NumberFormatException e) {
				apres = -1;
			}
			if (apres < 0 || apres > 10_000) {
				throw new ErreurProfil(HttpStatus.BAD_REQUEST, "validation");
			}
		}
		visible(moi, utilisateurId);
		List<Vues.Preuve> lues = preuves.lister(utilisateurId, type, apres, limite);
		boolean suite = lues.size() > limite;
		return new Vues.Page<>(suite ? lues.subList(0, limite) : lues, suite ? String.valueOf(apres + limite) : null);
	}

	/** Un profil non publié n'existe pas pour les autres : 404. */
	private Profil visible(UtilisateurConnecte moi, UUID utilisateurId) {
		return profils.findById(utilisateurId)
			.filter(p -> p.publie() || (moi != null && p.utilisateurId().equals(moi.id())))
			.orElseThrow(ErreurProfil::introuvable);
	}

}
