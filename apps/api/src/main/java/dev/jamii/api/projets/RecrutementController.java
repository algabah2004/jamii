package dev.jamii.api.projets;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.jamii.api.auth.Annuaire;
import dev.jamii.api.auth.Compte;
import dev.jamii.api.auth.UtilisateurConnecte;

/** Rôles recherchés et candidatures (contracts/projets.yaml, tag « recrutement »). */
@RestController
class RecrutementController {

	private static final Set<String> STATUTS_CANDIDATURE = Set.of("envoyee", "acceptee", "refusee", "retiree");

	record CreationRole(String titre, List<String> competences) {
	}

	record CreationCandidature(String message) {
	}

	record RoleRecherche(UUID id, String titre, List<String> competences, String statut) {
	}

	record Candidature(UUID id, UUID projetId, RoleRecherche role, ProjetsController.PersonneResume candidat, String message,
			String statut, Instant creeLe, Instant decideeLe) {
	}

	record PageCandidatures(List<Candidature> elements, String curseurSuivant) {
	}

	private final Recrutement recrutement;

	private final Annuaire annuaire;

	RecrutementController(Recrutement recrutement, Annuaire annuaire) {
		this.recrutement = recrutement;
		this.annuaire = annuaire;
	}

	@PostMapping("/api/projets/{projetId}/roles")
	ResponseEntity<RoleRecherche> publierRole(@PathVariable UUID projetId, @RequestBody CreationRole demande,
			@AuthenticationPrincipal UtilisateurConnecte moi) {
		String titre = demande.titre() == null ? "" : demande.titre().strip();
		List<Map<String, String>> erreurs = new ArrayList<>();
		if (titre.length() < 3 || titre.length() > 80) {
			erreurs.add(Map.of("champ", "titre", "code", "longueur_invalide"));
		}
		List<String> competences = Recrutement.normaliser(demande.competences());
		if (competences.isEmpty()) {
			erreurs.add(Map.of("champ", "competences", "code", "invalide"));
		}
		if (!erreurs.isEmpty()) {
			throw ErreurProjets.validation(erreurs);
		}
		var role = recrutement.publierRole(projetId, moi.id(), titre, competences, Instant.now());
		return ResponseEntity.created(URI.create("/api/projets/" + projetId + "/roles/" + role.id())).body(vue(role));
	}

	/** Fusion JSON (RFC 7396) : seuls les champs présents changent ; aucun n'accepte `null`. */
	@PatchMapping(path = "/api/projets/{projetId}/roles/{roleId}", consumes = "application/merge-patch+json")
	RoleRecherche modifierRole(@PathVariable UUID projetId, @PathVariable UUID roleId, @RequestBody Map<String, Object> patch,
			@AuthenticationPrincipal UtilisateurConnecte moi) {
		List<Map<String, String>> erreurs = new ArrayList<>();
		for (String cle : patch.keySet()) {
			if (!Set.of("titre", "competences", "statut").contains(cle)) {
				erreurs.add(Map.of("champ", cle, "code", "inconnu"));
			}
		}
		String titre = null;
		if (patch.containsKey("titre")) {
			titre = patch.get("titre") instanceof String s ? s.strip() : "";
			if (titre.length() < 3 || titre.length() > 80) {
				erreurs.add(Map.of("champ", "titre", "code", "longueur_invalide"));
			}
		}
		List<String> competences = null;
		if (patch.containsKey("competences")) {
			competences = patch.get("competences") instanceof List<?> liste && liste.stream().allMatch(String.class::isInstance)
					? Recrutement.normaliser(liste.stream().map(String.class::cast).toList()) : List.of();
			if (competences.isEmpty()) {
				erreurs.add(Map.of("champ", "competences", "code", "invalide"));
			}
		}
		String statut = null;
		if (patch.containsKey("statut")) {
			statut = patch.get("statut") instanceof String s ? s : "";
			if (!"ouvert".equals(statut) && !"ferme".equals(statut)) {
				erreurs.add(Map.of("champ", "statut", "code", "valeur_invalide"));
			}
		}
		if (!erreurs.isEmpty()) {
			throw ErreurProjets.validation(erreurs);
		}
		return vue(recrutement.modifierRole(projetId, roleId, moi.id(), titre, competences, statut, Instant.now()));
	}

	@PostMapping("/api/projets/{projetId}/roles/{roleId}/candidatures")
	ResponseEntity<Candidature> candidater(@PathVariable UUID projetId, @PathVariable UUID roleId,
			@RequestBody CreationCandidature demande, @AuthenticationPrincipal UtilisateurConnecte moi) {
		String message = demande.message() == null ? "" : demande.message().strip();
		if (message.length() < 10 || message.length() > 1000) {
			throw ErreurProjets.validation("message", "longueur_invalide");
		}
		var c = recrutement.candidater(projetId, roleId, moi.id(), message, Instant.now());
		return ResponseEntity.created(URI.create("/api/projets/" + projetId + "/candidatures/" + c.id()))
			.body(vues(List.of(c)).getFirst());
	}

	@GetMapping("/api/projets/{projetId}/candidatures")
	PageCandidatures candidaturesDuProjet(@PathVariable UUID projetId, @RequestParam(required = false) String statut,
			@RequestParam(defaultValue = "20") int limite, @RequestParam(required = false) String curseur,
			@AuthenticationPrincipal UtilisateurConnecte moi) {
		verifier(statut, limite);
		return vue(recrutement.candidaturesDuProjet(projetId, moi.id(), statut, limite, curseur));
	}

	@PostMapping("/api/projets/{projetId}/candidatures/{candidatureId}/{decision}")
	Candidature decider(@PathVariable UUID projetId, @PathVariable UUID candidatureId, @PathVariable String decision,
			@AuthenticationPrincipal UtilisateurConnecte moi) {
		return vues(List.of(recrutement.decider(projetId, candidatureId, decision, moi.id(), Instant.now()))).getFirst();
	}

	@GetMapping("/api/me/candidatures")
	PageCandidatures mesCandidatures(@RequestParam(required = false) String statut, @RequestParam(defaultValue = "20") int limite,
			@RequestParam(required = false) String curseur, @AuthenticationPrincipal UtilisateurConnecte moi) {
		verifier(statut, limite);
		return vue(recrutement.mesCandidatures(moi.id(), statut, limite, curseur));
	}

	// ---------------------------------------------------------------- vues

	private static void verifier(String statut, int limite) {
		if (statut != null && !STATUTS_CANDIDATURE.contains(statut)) {
			throw ErreurProjets.validation("statut", "valeur_invalide");
		}
		if (limite < 1 || limite > 100) {
			throw ErreurProjets.validation("limite", "hors_limites");
		}
	}

	private PageCandidatures vue(Recrutement.Page page) {
		return new PageCandidatures(vues(page.elements()), page.curseurSuivant());
	}

	private List<Candidature> vues(List<Recrutement.Candidature> candidatures) {
		Set<UUID> ids = new HashSet<>();
		candidatures.forEach(c -> ids.add(c.candidatId()));
		Map<UUID, Compte> comptes = annuaire.lire(ids);
		return candidatures.stream()
			.filter(c -> comptes.containsKey(c.candidatId()))
			.map(c -> new Candidature(c.id(), c.projetId(), vue(c.role()), personne(comptes.get(c.candidatId())), c.message(),
					c.statut(), c.creeLe(), c.decideeLe()))
			.toList();
	}

	static RoleRecherche vue(Recrutement.Role role) {
		return new RoleRecherche(role.id(), role.titre(), role.competences(), role.statut());
	}

	private static ProjetsController.PersonneResume personne(Compte compte) {
		return new ProjetsController.PersonneResume(compte.id(), compte.nom(), compte.avatarUrl());
	}

}
