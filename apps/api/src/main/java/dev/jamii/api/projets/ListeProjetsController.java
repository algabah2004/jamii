package dev.jamii.api.projets;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

import dev.jamii.api.auth.Annuaire;
import dev.jamii.api.auth.Compte;
import dev.jamii.api.auth.UtilisateurConnecte;

/** GET /api/projets (contracts/projets.yaml, `listerProjets`). */
@RestController
class ListeProjetsController {

	private static final Set<String> STATUTS = Set.of("recrutement", "en_cours", "en_pause", "livre", "abandonne");

	record ProjetResume(UUID id, String titre, String origine, String statut, ProjetsController.PersonneResume porteur,
			List<String> competencesRecherchees, int nombreMembres, Instant creeLe) {
	}

	record PageProjets(List<ProjetResume> elements, String curseurSuivant) {
	}

	private final ListeProjets liste;

	private final Annuaire annuaire;

	ListeProjetsController(ListeProjets liste, Annuaire annuaire) {
		this.liste = liste;
		this.annuaire = annuaire;
	}

	@GetMapping("/api/projets")
	PageProjets lister(@RequestParam(required = false) String statut, @RequestParam(required = false) String competence,
			@RequestParam(required = false) String membre, @RequestParam(defaultValue = "20") int limite,
			@RequestParam(required = false) String curseur, @AuthenticationPrincipal UtilisateurConnecte moi) {
		if (statut != null && !STATUTS.contains(statut)) {
			throw ErreurProjets.validation("statut", "valeur_invalide");
		}
		if (membre != null && !"moi".equals(membre)) {
			throw ErreurProjets.validation("membre", "valeur_invalide");
		}
		if (limite < 1 || limite > 100) {
			throw ErreurProjets.validation("limite", "hors_limites");
		}
		String filtreCompetence = null;
		if (competence != null) {
			var normalisee = Recrutement.normaliser(List.of(competence));
			if (normalisee.isEmpty()) {
				throw ErreurProjets.validation("competence", "invalide");
			}
			filtreCompetence = normalisee.getFirst();
		}
		var page = liste.lister(moi.id(), statut, filtreCompetence, "moi".equals(membre), limite, curseur);
		Set<UUID> porteurs = new HashSet<>();
		page.elements().forEach(l -> porteurs.add(l.porteurId()));
		Map<UUID, Compte> comptes = annuaire.lire(porteurs);
		return new PageProjets(page.elements().stream()
			.filter(l -> comptes.containsKey(l.porteurId()))
			.map(l -> new ProjetResume(l.id(), l.titre(), l.origine(), l.statut(),
					new ProjetsController.PersonneResume(comptes.get(l.porteurId()).id(), comptes.get(l.porteurId()).nom(),
							comptes.get(l.porteurId()).avatarUrl()),
					l.competencesRecherchees(), l.nombreMembres(), l.creeLe()))
			.toList(), page.curseurSuivant());
	}

}
