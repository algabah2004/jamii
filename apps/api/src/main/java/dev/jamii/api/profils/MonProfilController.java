package dev.jamii.api.profils;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.jamii.api.auth.Annuaire;
import dev.jamii.api.auth.Compte;
import dev.jamii.api.auth.UtilisateurConnecte;

/** Les routes /api/me/profil (contrat : tag « mon-profil » de contracts/profils.yaml). */
@RestController
@RequestMapping("/api/me/profil")
class MonProfilController {

	private final ProfilRepository profils;

	private final Annuaire comptes;

	private final GenerationProfils generation;

	private final Clock horloge = Clock.systemUTC();

	MonProfilController(ProfilRepository profils, Annuaire comptes, GenerationProfils generation) {
		this.profils = profils;
		this.comptes = comptes;
		this.generation = generation;
	}

	@GetMapping
	@Transactional(readOnly = true)
	Vues.MonProfil lire(@AuthenticationPrincipal UtilisateurConnecte moi) {
		return vue(monProfil(moi.id()));
	}

	@PatchMapping(consumes = { "application/merge-patch+json", "application/json" })
	@Transactional
	Vues.MonProfil modifier(@AuthenticationPrincipal UtilisateurConnecte moi, @RequestBody Map<String, Object> patch) {
		Profil profil = monProfil(moi.id());
		ModificationProfil.appliquer(patch, profil);
		profil.toucher(horloge.instant());
		return vue(profils.saveAndFlush(profil));
	}

	@PostMapping("/generation")
	ResponseEntity<Vues.Generation> generer(@AuthenticationPrincipal UtilisateurConnecte moi) {
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(generation.lancer(moi.id()));
	}

	@PostMapping("/publier")
	@Transactional
	Vues.MonProfil publier(@AuthenticationPrincipal UtilisateurConnecte moi) {
		Profil profil = monProfil(moi.id());
		profil.publier(true, horloge.instant());
		return vue(profils.saveAndFlush(profil));
	}

	@PostMapping("/retirer")
	@Transactional
	Vues.MonProfil retirer(@AuthenticationPrincipal UtilisateurConnecte moi) {
		Profil profil = monProfil(moi.id());
		profil.publier(false, horloge.instant());
		return vue(profils.saveAndFlush(profil));
	}

	private Profil monProfil(UUID id) {
		return profils.findById(id).orElseThrow(() -> new ErreurProfil(HttpStatus.NOT_FOUND, "profil_non_genere"));
	}

	private Vues.MonProfil vue(Profil profil) {
		Compte compte = comptes.lire(profil.utilisateurId()).orElseThrow(ErreurProfil::introuvable);
		return Vues.MonProfil.depuis(profil, compte);
	}

}
