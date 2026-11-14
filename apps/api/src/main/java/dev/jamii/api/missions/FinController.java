package dev.jamii.api.missions;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.jamii.api.auth.Role;
import dev.jamii.api.auth.UtilisateurConnecte;

/** Annuler une mission, ouvrir et trancher un désaccord (contracts/missions.yaml, tag « fin »). Connexion requise. */
@RestController
@RequestMapping("/api/missions/{missionId}")
class FinController {

	private final Fin fin;

	private final Missions missions;

	private final VuesMissions vues;

	FinController(Fin fin, Missions missions, VuesMissions vues) {
		this.fin = fin;
		this.missions = missions;
		this.vues = vues;
	}

	@PostMapping("/annuler")
	VuesMissions.MissionVue annuler(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable UUID missionId,
			@RequestBody Map<String, Object> corps) {
		var e = new MissionsController.Erreurs(corps, Set.of("version", "raison"));
		int version = e.entier("version", 0, Integer.MAX_VALUE, true);
		String raison = e.texte("raison", 10, 1000, true);
		e.verifier();
		fin.annuler(missionId, moi.id(), version, raison);
		return vue(missionId, moi);
	}

	@PostMapping("/desaccord")
	VuesMissions.MissionVue ouvrirDesaccord(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable UUID missionId,
			@RequestBody Map<String, Object> corps) {
		var e = new MissionsController.Erreurs(corps, Set.of("version", "raison"));
		int version = e.entier("version", 0, Integer.MAX_VALUE, true);
		String raison = e.texte("raison", 20, 4000, true);
		e.verifier();
		fin.ouvrirDesaccord(missionId, moi.id(), version, raison);
		return vue(missionId, moi);
	}

	/** Seul un modérateur tranche (403 sinon, avant même de lire la mission). */
	@PostMapping("/desaccord/trancher")
	VuesMissions.MissionVue trancher(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable UUID missionId,
			@RequestBody Map<String, Object> corps) {
		if (!moi.roles().contains(Role.MODERATEUR)) {
			throw ErreurMissions.interdit();
		}
		var e = new MissionsController.Erreurs(corps, Set.of("version", "issue", "explication"));
		int version = e.entier("version", 0, Integer.MAX_VALUE, true);
		String issue = e.choix("issue", List.of("terminee", "annulee"), true);
		String explication = e.texte("explication", 20, 4000, true);
		e.verifier();
		fin.trancher(missionId, moi.id(), version, issue, explication);
		return vue(missionId, moi);
	}

	private VuesMissions.MissionVue vue(UUID missionId, UtilisateurConnecte moi) {
		return vues.vue(missions.lire(missionId, moi.id(), moi.roles().contains(Role.MODERATEUR)), moi.id());
	}

}
