package dev.jamii.api.projets;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.jamii.api.auth.Annuaire;
import dev.jamii.api.auth.Compte;
import dev.jamii.api.auth.UtilisateurConnecte;

/** Les jalons et les tâches d'un projet, réservés à l'équipe (contracts/projets.yaml, tag « equipe »). */
@RestController
class EquipeController {

	private static final Set<String> STATUTS_TACHE = Set.of("a_faire", "en_cours", "livree");

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
		return new PageTaches(page.elements().stream().map(t -> {
			Compte c = t.assigneeId() == null ? null : comptes.get(t.assigneeId());
			var personne = c == null ? null : new ProjetsController.PersonneResume(c.id(), c.nom(), c.avatarUrl());
			return new Tache(t.id(), t.titre(), t.jalonId(), personne, t.statut(), t.echeance(), t.livreeLe(), t.proposeeParIa(),
					t.version(), t.creeLe());
		}).toList(), page.curseurSuivant());
	}

}
