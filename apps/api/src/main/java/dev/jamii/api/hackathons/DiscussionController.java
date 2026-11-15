package dev.jamii.api.hackathons;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.jamii.api.auth.Annuaire;
import dev.jamii.api.auth.Compte;
import dev.jamii.api.auth.UtilisateurConnecte;

/** La discussion d'un hackathon : annonces et communauté liée (contracts/hackathons.yaml, tag « discussion »). */
@RestController
@RequestMapping("/api/hackathons/{hackathonId}")
class DiscussionController {

	record AnnonceVue(UUID id, JuryController.PersonneResume auteur, String texte, java.time.Instant publieeLe) {
	}

	record DiscussionVue(String communaute, List<AnnonceVue> annonces) {
	}

	private final Discussion discussion;

	private final Annuaire annuaire;

	DiscussionController(Discussion discussion, Annuaire annuaire) {
		this.discussion = discussion;
		this.annuaire = annuaire;
	}

	/** Public comme le hackathon : un visiteur lit les annonces d'un hackathon publié. */
	@GetMapping("/discussion")
	DiscussionVue lire(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable UUID hackathonId) {
		Discussion.Etat etat = discussion.lire(hackathonId, moi == null ? null : moi.id());
		return new DiscussionVue(etat.communaute(), vues(etat.annonces()));
	}

	@PostMapping("/annonces")
	ResponseEntity<AnnonceVue> annoncer(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable UUID hackathonId,
			@RequestBody Map<String, Object> corps) {
		Corps c = new Corps(corps, Set.of("texte"));
		String texte = c.texte("texte", 1, Discussion.TEXTE_MAX, true);
		c.verifier();
		return ResponseEntity.status(HttpStatus.CREATED).body(vues(List.of(discussion.annoncer(hackathonId, moi.id(), texte))).getFirst());
	}

	@DeleteMapping("/annonces/{annonceId}")
	ResponseEntity<Void> retirer(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable UUID hackathonId,
			@PathVariable UUID annonceId) {
		discussion.retirer(hackathonId, moi.id(), annonceId);
		return ResponseEntity.noContent().build();
	}

	@PutMapping("/communaute")
	ResponseEntity<Void> lier(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable UUID hackathonId,
			@RequestBody Map<String, Object> corps) {
		Corps c = new Corps(corps, Set.of("communaute"));
		String slug = c.texte("communaute", 1, 80, false);
		c.verifier();
		discussion.lierCommunaute(hackathonId, moi.id(), slug);
		return ResponseEntity.noContent().build();
	}

	private List<AnnonceVue> vues(List<Discussion.Annonce> annonces) {
		Map<UUID, Compte> comptes = annuaire.lire(annonces.stream().map(Discussion.Annonce::auteurId).filter(java.util.Objects::nonNull).distinct().toList());
		return annonces.stream().map(a -> {
			Compte c = a.auteurId() == null ? null : comptes.get(a.auteurId());
			JuryController.PersonneResume auteur = new JuryController.PersonneResume(a.auteurId(),
					c == null ? "Compte supprimé" : c.nom(), c == null ? null : c.avatarUrl());
			return new AnnonceVue(a.id(), auteur, a.texte(), a.publieeLe());
		}).toList();
	}

}
