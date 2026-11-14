package dev.jamii.api.missions;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.jamii.api.auth.Compte;
import dev.jamii.api.auth.UtilisateurConnecte;

/**
 * Les propositions et le devis d'une mission (contracts/missions.yaml, tag « propositions »). Toutes ces routes
 * demandent la connexion.
 */
@RestController
@RequestMapping("/api/missions/{missionId}")
class PropositionsController {

	record PagePropositions(List<VuesMissions.PropositionVue> elements, String curseurSuivant) {
	}

	private final Propositions propositions;

	private final Devis devis;

	private final Missions missions;

	private final VuesMissions vues;

	PropositionsController(Propositions propositions, Devis devis, Missions missions, VuesMissions vues) {
		this.propositions = propositions;
		this.devis = devis;
		this.missions = missions;
		this.vues = vues;
	}

	// ---------------------------------------------------------------- propositions

	@GetMapping("/propositions")
	PagePropositions lister(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable UUID missionId,
			@RequestParam(defaultValue = "20") int limite, @RequestParam(required = false) String curseur) {
		if (limite < 1 || limite > 100) {
			throw ErreurMissions.validation(List.of(Map.of("champ", "limite", "code", "hors_limites")));
		}
		Propositions.Page page = propositions.lister(missionId, moi.id(), limite, curseur);
		Map<UUID, Compte> comptes = vues.comptes(page.elements().stream().map(Propositions.Proposition::developpeurId).distinct().toList());
		return new PagePropositions(page.elements().stream().map(p -> vues.proposition(p, comptes.get(p.developpeurId()))).toList(),
				page.curseurSuivant());
	}

	@PostMapping("/propositions")
	ResponseEntity<VuesMissions.PropositionVue> proposer(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable UUID missionId,
			@RequestBody Map<String, Object> corps) {
		Propositions.Proposition p = propositions.proposer(missionId, moi.id(), lireProposition(corps));
		Compte auteur = vues.comptes(List.of(moi.id())).get(moi.id());
		return ResponseEntity.created(URI.create("/api/missions/" + missionId + "/propositions/" + p.id()))
			.body(vues.proposition(p, auteur));
	}

	@PostMapping("/propositions/{propositionId}/retenir")
	VuesMissions.MissionVue retenir(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable UUID missionId,
			@PathVariable UUID propositionId, @RequestBody Map<String, Object> corps) {
		propositions.retenir(missionId, propositionId, moi.id(), MissionsController.version(corps));
		return vues.vue(missions.lire(missionId, moi.id()), moi.id());
	}

	@PostMapping("/propositions/{propositionId}/retirer")
	ResponseEntity<Void> retirer(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable UUID missionId,
			@PathVariable UUID propositionId) {
		propositions.retirer(missionId, propositionId, moi.id());
		return ResponseEntity.noContent().build();
	}

	// ---------------------------------------------------------------- devis

	@PostMapping("/devis")
	ResponseEntity<VuesMissions.DevisVue> envoyerDevis(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable UUID missionId,
			@RequestBody Map<String, Object> corps) {
		Devis.DevisComplet envoye = devis.envoyer(missionId, moi.id(), lireDevis(corps));
		return ResponseEntity.created(URI.create("/api/missions/" + missionId + "/devis/" + envoye.id())).body(vues.devis(envoye));
	}

	@PostMapping("/devis/{devisId}/accepter")
	VuesMissions.MissionVue accepterDevis(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable UUID missionId,
			@PathVariable UUID devisId, @RequestBody Map<String, Object> corps) {
		devis.accepter(missionId, devisId, moi.id(), MissionsController.version(corps));
		return vues.vue(missions.lire(missionId, moi.id()), moi.id());
	}

	// ---------------------------------------------------------------- lecture du corps

	private static Propositions.Nouvelle lireProposition(Map<String, Object> corps) {
		var e = new MissionsController.Erreurs(corps, Set.of("message", "prix", "delaiJours", "reponses"));
		String message = e.texte("message", 30, 4000, true);
		Missions.Montant prix = e.montant("prix", true);
		int delai = e.entier("delaiJours", 1, 365, true);
		List<String> reponses = new ArrayList<>();
		Object brutes = corps.get("reponses");
		if (brutes instanceof List<?> liste) {
			if (liste.size() > 5) {
				e.ajouter("reponses", "trop_nombreuses");
			}
			for (Object o : liste) {
				if (o instanceof String s && s.length() <= 1000) {
					reponses.add(s);
				}
				else {
					e.ajouter("reponses", "format_invalide");
				}
			}
		}
		else if (brutes != null) {
			e.ajouter("reponses", "type_invalide");
		}
		e.verifier();
		return new Propositions.Nouvelle(message, prix, delai, List.copyOf(reponses));
	}

	private static Devis.Nouveau lireDevis(Map<String, Object> corps) {
		var e = new MissionsController.Erreurs(corps, Set.of("total", "retouchesComprises", "conditions", "jalons"));
		Missions.Montant total = e.montant("total", true);
		int retouches = e.entier("retouchesComprises", 0, 10, true);
		String conditions = e.texte("conditions", 0, 4000, false);
		List<Devis.NouveauJalon> jalons = new ArrayList<>();
		Object brut = corps.get("jalons");
		if (!(brut instanceof List<?> liste)) {
			e.ajouter("jalons", brut == null ? "obligatoire" : "type_invalide");
		}
		else {
			if (liste.isEmpty()) {
				e.ajouter("jalons", "obligatoire");
			}
			else if (liste.size() > 10) {
				e.ajouter("jalons", "trop_nombreux");
			}
			for (int i = 0; i < liste.size(); i++) {
				if (!(liste.get(i) instanceof Map<?, ?> element)) {
					e.ajouter("jalons[" + i + "]", "type_invalide");
					continue;
				}
				@SuppressWarnings("unchecked")
				var sous = e.sous((Map<String, Object>) element, Set.of("titre", "description", "montant", "delaiJours"), "jalons[" + i + "].");
				String titre = sous.texte("titre", 3, 120, true);
				String description = sous.texte("description", 0, 2000, false);
				Missions.Montant montant = sous.montant("montant", true);
				int delai = sous.entier("delaiJours", 1, 365, true);
				if (montant != null && total != null && !montant.devise().equals(total.devise())) {
					sous.ajouter("montant", "devise_differente");
				}
				jalons.add(new Devis.NouveauJalon(titre, description == null || description.isEmpty() ? null : description, montant, delai));
			}
		}
		e.verifier();
		return new Devis.Nouveau(total, retouches, conditions == null || conditions.isEmpty() ? null : conditions, List.copyOf(jalons));
	}

}
