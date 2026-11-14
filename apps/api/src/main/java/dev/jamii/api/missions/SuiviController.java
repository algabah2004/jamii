package dev.jamii.api.missions;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
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

import dev.jamii.api.auth.UtilisateurConnecte;

/** Démarrer, livrer, accepter, retoucher et déclarer un paiement (contracts/missions.yaml, tag « suivi »). Connexion requise. */
@RestController
@RequestMapping("/api/missions/{missionId}")
class SuiviController {

	private final Livraisons livraisons;

	private final Missions missions;

	private final VuesMissions vues;

	SuiviController(Livraisons livraisons, Missions missions, VuesMissions vues) {
		this.livraisons = livraisons;
		this.missions = missions;
		this.vues = vues;
	}

	@PostMapping("/demarrer")
	VuesMissions.MissionVue demarrer(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable UUID missionId,
			@RequestBody Map<String, Object> corps) {
		var e = new MissionsController.Erreurs(corps, Set.of("version", "informations"));
		int version = e.entier("version", 0, Integer.MAX_VALUE, true);
		String informations = e.texte("informations", 1, 4000, true);
		e.verifier();
		livraisons.demarrer(missionId, moi.id(), version, informations);
		return vue(missionId, moi);
	}

	@PostMapping("/jalons/{jalonId}/livrer")
	VuesMissions.MissionVue livrer(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable UUID missionId,
			@PathVariable UUID jalonId, @RequestBody Map<String, Object> corps) {
		var e = new MissionsController.Erreurs(corps, Set.of("version", "message", "liens"));
		int version = e.entier("version", 0, Integer.MAX_VALUE, true);
		String message = e.texte("message", 10, 4000, true);
		List<String> liens = liens(e, corps.get("liens"));
		e.verifier();
		livraisons.livrer(missionId, jalonId, moi.id(), version, message, liens);
		return vue(missionId, moi);
	}

	@PostMapping("/jalons/{jalonId}/accepter")
	VuesMissions.MissionVue accepter(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable UUID missionId,
			@PathVariable UUID jalonId, @RequestBody Map<String, Object> corps) {
		livraisons.accepter(missionId, jalonId, moi.id(), MissionsController.version(corps));
		return vue(missionId, moi);
	}

	@PostMapping("/jalons/{jalonId}/demander-retouche")
	VuesMissions.MissionVue demanderRetouche(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable UUID missionId,
			@PathVariable UUID jalonId, @RequestBody Map<String, Object> corps) {
		var e = new MissionsController.Erreurs(corps, Set.of("version", "raison"));
		int version = e.entier("version", 0, Integer.MAX_VALUE, true);
		String raison = e.texte("raison", 10, 2000, true);
		e.verifier();
		livraisons.demanderRetouche(missionId, jalonId, moi.id(), version, raison);
		return vue(missionId, moi);
	}

	@PostMapping("/jalons/{jalonId}/declarer-paiement")
	VuesMissions.MissionVue declarerPaiement(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable UUID missionId,
			@PathVariable UUID jalonId, @RequestBody Map<String, Object> corps) {
		var e = new MissionsController.Erreurs(corps, Set.of("version", "sens"));
		int version = e.entier("version", 0, Integer.MAX_VALUE, true);
		String sens = e.choix("sens", List.of("paye", "recu"), true);
		e.verifier();
		livraisons.declarerPaiement(missionId, jalonId, moi.id(), version, "paye".equals(sens) ? Paiements.Sens.PAYE : Paiements.Sens.RECU);
		return vue(missionId, moi);
	}

	private VuesMissions.MissionVue vue(UUID missionId, UtilisateurConnecte moi) {
		return vues.vue(missions.lire(missionId, moi.id()), moi.id());
	}

	/** Au plus 5 liens `https` (test, dépôt, fichiers), affichés tels quels : ils ne sont jamais appelés par l'API. */
	private static List<String> liens(MissionsController.Erreurs e, Object brut) {
		if (brut == null) {
			return List.of();
		}
		if (!(brut instanceof List<?> elements)) {
			e.ajouter("liens", "type_invalide");
			return List.of();
		}
		if (elements.size() > 5) {
			e.ajouter("liens", "trop_nombreux");
		}
		List<String> liens = new ArrayList<>();
		for (int i = 0; i < elements.size(); i++) {
			if (elements.get(i) instanceof String s && s.length() <= 500 && estHttps(s)) {
				liens.add(s);
			}
			else {
				e.ajouter("liens[" + i + "]", "lien_invalide");
			}
		}
		return liens;
	}

	private static boolean estHttps(String texte) {
		try {
			URI uri = new URI(texte);
			return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
		}
		catch (URISyntaxException e) {
			return false;
		}
	}

}
