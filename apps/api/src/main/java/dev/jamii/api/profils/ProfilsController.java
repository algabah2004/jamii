package dev.jamii.api.profils;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import dev.jamii.api.auth.Annuaire;
import dev.jamii.api.auth.Compte;
import dev.jamii.api.auth.UtilisateurConnecte;

/** Lire le profil d'une autre personne (contrat : tag « profils » de contracts/profils.yaml). */
@RestController
class ProfilsController {

	private final ProfilRepository profils;

	private final Annuaire comptes;

	ProfilsController(ProfilRepository profils, Annuaire comptes) {
		this.profils = profils;
		this.comptes = comptes;
	}

	/** Un profil non publié répond 404 aux autres : on ne révèle pas qu'il existe (api-conventions.md §5). */
	@GetMapping("/api/profils/{utilisateurId}")
	@Transactional(readOnly = true)
	Vues.ProfilPublic lire(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable UUID utilisateurId) {
		Profil profil = profils.findById(utilisateurId)
			.filter(p -> p.publie() || p.utilisateurId().equals(moi.id()))
			.orElseThrow(ErreurProfil::introuvable);
		Compte compte = comptes.lire(utilisateurId).orElseThrow(ErreurProfil::introuvable);
		return Vues.ProfilPublic.depuis(profil, compte);
	}

}
