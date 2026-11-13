package dev.jamii.api.projets;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.jamii.api.auth.Annuaire;
import dev.jamii.api.auth.Compte;
import dev.jamii.api.auth.UtilisateurConnecte;

/** Les jalons et les tâches d'un projet, réservés à l'équipe (contracts/projets.yaml, tag « equipe »). */
@RestController
class EquipeController {

	private static final Set<String> STATUTS_TACHE = Set.of("a_faire", "en_cours", "livree");

	private static final Set<String> CHAMPS_MODIFIABLES = Set.of("titre", "jalonId", "assigneeId", "echeance", "statut", "version");

	record Jalon(UUID id, String titre, LocalDate echeance, String statut, int ordre) {
	}

	record Tache(UUID id, String titre, UUID jalonId, ProjetsController.PersonneResume assignee, String statut,
			LocalDate echeance, Instant livreeLe, boolean proposeeParIa, int version, Instant creeLe) {
	}

	record PageTaches(List<Tache> elements, String curseurSuivant) {
	}

	private final TachesEtJalons equipe;

	private final Annuaire annuaire;

	EquipeController(TachesEtJalons equipe, Annuaire annuaire) {
		this.equipe = equipe;
		this.annuaire = annuaire;
	}

	@GetMapping("/api/projets/{projetId}/jalons")
	List<Jalon> jalons(@PathVariable UUID projetId, @AuthenticationPrincipal UtilisateurConnecte moi) {
		return equipe.jalons(projetId, moi.id()).stream()
			.map(j -> new Jalon(j.id(), j.titre(), j.echeance(), j.statut(), j.ordre()))
			.toList();
	}

	@GetMapping("/api/projets/{projetId}/taches")
	PageTaches taches(@PathVariable UUID projetId, @RequestParam(required = false) String statut,
			@RequestParam(required = false) String jalonId, @RequestParam(required = false) String assignee,
			@RequestParam(defaultValue = "20") int limite, @RequestParam(required = false) String curseur,
			@AuthenticationPrincipal UtilisateurConnecte moi) {
		if (statut != null && !STATUTS_TACHE.contains(statut)) {
			throw ErreurProjets.validation("statut", "valeur_invalide");
		}
		if (assignee != null && !"moi".equals(assignee)) {
			throw ErreurProjets.validation("assignee", "valeur_invalide");
		}
		if (limite < 1 || limite > 100) {
			throw ErreurProjets.validation("limite", "hors_limites");
		}
		UUID jalon = null;
		if (jalonId != null) {
			try {
				jalon = UUID.fromString(jalonId);
			}
			catch (IllegalArgumentException e) {
				throw ErreurProjets.validation("jalonId", "format_invalide");
			}
		}
		var page = equipe.taches(projetId, moi.id(), statut, jalon, "moi".equals(assignee), limite, curseur);
		Set<UUID> assignes = new HashSet<>();
		page.elements().forEach(t -> {
			if (t.assigneeId() != null) {
				assignes.add(t.assigneeId());
			}
		});
		Map<UUID, Compte> comptes = annuaire.lire(assignes);
		return new PageTaches(page.elements().stream().map(t -> vue(t, comptes)).toList(), page.curseurSuivant());
	}

	/** Fusion JSON (RFC 7396) : seuls les champs présents changent ; `version` est obligatoire. */
	@PatchMapping(path = "/api/projets/{projetId}/taches/{tacheId}", consumes = { "application/merge-patch+json", "application/json" })
	Tache modifierTache(@PathVariable UUID projetId, @PathVariable UUID tacheId, @RequestBody Map<String, Object> patch,
			@AuthenticationPrincipal UtilisateurConnecte moi) {
		List<Map<String, String>> erreurs = new ArrayList<>();
		for (String cle : patch.keySet()) {
			if (!CHAMPS_MODIFIABLES.contains(cle)) {
				erreurs.add(Map.of("champ", cle, "code", "inconnu"));
			}
		}
		String titre = null;
		if (patch.containsKey("titre")) {
			titre = patch.get("titre") instanceof String s ? s.strip() : "";
			if (titre.length() < 3 || titre.length() > 200) {
				erreurs.add(Map.of("champ", "titre", "code", "longueur_invalide"));
			}
		}
		UUID jalonId = identifiant(patch, "jalonId", erreurs);
		UUID assigneeId = identifiant(patch, "assigneeId", erreurs);
		LocalDate echeance = null;
		if (patch.get("echeance") != null) {
			try {
				echeance = LocalDate.parse((String) patch.get("echeance"));
			}
			catch (RuntimeException e) {
				erreurs.add(Map.of("champ", "echeance", "code", "format_invalide"));
			}
		}
		String statut = null;
		if (patch.containsKey("statut")) {
			statut = patch.get("statut") instanceof String s ? s : "";
			if (!STATUTS_TACHE.contains(statut)) {
				erreurs.add(Map.of("champ", "statut", "code", "valeur_invalide"));
			}
		}
		int version = 0;
		if (!(patch.get("version") instanceof Integer v)) {
			erreurs.add(Map.of("champ", "version", "code", patch.containsKey("version") ? "format_invalide" : "obligatoire"));
		}
		else {
			version = v;
		}
		if (!erreurs.isEmpty()) {
			throw ErreurProjets.validation(erreurs);
		}
		var modification = new TachesEtJalons.Modification(titre, patch.containsKey("jalonId"), jalonId, patch.containsKey("assigneeId"),
				assigneeId, patch.containsKey("echeance"), echeance, statut, version);
		var tache = equipe.modifier(projetId, tacheId, moi.id(), modification, Instant.now());
		return vue(tache, tache.assigneeId() == null ? Map.of() : annuaire.lire(Set.of(tache.assigneeId())));
	}

	/** Un identifiant facultatif : absent ou null donnent null ; autre chose qu'un UUID est une erreur. */
	private static UUID identifiant(Map<String, Object> patch, String champ, List<Map<String, String>> erreurs) {
		Object valeur = patch.get(champ);
		if (valeur == null) {
			return null;
		}
		try {
			return UUID.fromString((String) valeur);
		}
		catch (RuntimeException e) {
			erreurs.add(Map.of("champ", champ, "code", "format_invalide"));
			return null;
		}
	}

	private static Tache vue(TachesEtJalons.Tache t, Map<UUID, Compte> comptes) {
		Compte c = t.assigneeId() == null ? null : comptes.get(t.assigneeId());
		var personne = c == null ? null : new ProjetsController.PersonneResume(c.id(), c.nom(), c.avatarUrl());
		return new Tache(t.id(), t.titre(), t.jalonId(), personne, t.statut(), t.echeance(), t.livreeLe(), t.proposeeParIa(),
				t.version(), t.creeLe());
	}

}
