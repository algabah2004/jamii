package dev.jamii.api.hackathons;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.jamii.api.auth.UtilisateurConnecte;

/** Rendre et lire le projet d'une équipe (contracts/hackathons.yaml, tag « rendus »). Connexion requise. */
@RestController
@RequestMapping("/api/hackathons/{hackathonId}/equipes/{equipeId}/rendu")
class RendusController {

	record RenduVue(UUID id, UUID equipeId, String titre, String description, String lienProduit, String depot, String video,
			List<String> reponses, Boolean lienRepond, Instant lienVerifieLe, int version, Instant modifieLe) {
	}

	private final Rendus rendus;

	RendusController(Rendus rendus) {
		this.rendus = rendus;
	}

	@GetMapping
	RenduVue lire(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable UUID hackathonId, @PathVariable UUID equipeId) {
		return vue(rendus.lire(hackathonId, equipeId, moi.id()));
	}

	/** Le rendu est enregistré (et la transaction validée) avant la vérification du lien. */
	@PutMapping
	ResponseEntity<RenduVue> rendre(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable UUID hackathonId,
			@PathVariable UUID equipeId, @RequestBody Map<String, Object> corps) {
		Rendus.Enregistre e = rendus.rendre(hackathonId, equipeId, moi.id(), lireNouveau(corps));
		RenduVue vue = vue(rendus.verifierLien(e.rendu()));
		return ResponseEntity.status(e.cree() ? HttpStatus.CREATED : HttpStatus.OK).body(vue);
	}

	static RenduVue vue(Rendus.Rendu r) {
		return new RenduVue(r.id(), r.equipeId(), r.titre(), r.description(), r.lienProduit(), r.depot(), r.video(), r.reponses(),
				r.lienRepond(), r.lienVerifieLe(), r.version(), r.modifieLe());
	}

	// ---------------------------------------------------------------- lecture du corps

	private static Rendus.Nouveau lireNouveau(Map<String, Object> corps) {
		Corps c = new Corps(corps, Set.of("version", "titre", "description", "lienProduit", "depot", "video", "reponses"));
		Integer version = corps.get("version") == null ? null : c.entier("version", 0, Integer.MAX_VALUE, false);
		String titre = c.texte("titre", 3, 120, true);
		String description = c.texte("description", 20, 4000, true);
		URI lien = lien(c, "lienProduit", true);
		URI depot = lien(c, "depot", false);
		URI video = lien(c, "video", false);
		List<String> reponses = reponses(c, corps.get("reponses"));
		c.verifier();
		return new Rendus.Nouveau(version, titre, description, lien, depot, video, reponses);
	}

	/**
	 * Un lien public en `https`, sans identifiants ni port, vers un nom de domaine. Une adresse IP ou un nom interne
	 * (`localhost`, `.local`, `.internal`) est refusé dès ici (protection SSRF) ; la vérification réelle contrôle en plus
	 * les adresses IP derrière le nom au moment de l'appel.
	 */
	static URI lien(Corps c, String champ, boolean obligatoire) {
		String texte = c.texte(champ, 1, 500, obligatoire);
		if (texte == null) {
			return null;
		}
		try {
			URI uri = new URI(texte);
			String hote = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
			if (!"https".equalsIgnoreCase(uri.getScheme()) || hote.isEmpty() || uri.getRawUserInfo() != null || uri.getPort() != -1) {
				c.ajouter(champ, "lien_invalide");
				return null;
			}
			if (!hote.contains(".") || hote.endsWith(".localhost") || hote.endsWith(".local") || hote.endsWith(".internal")
					|| hote.startsWith("[") || hote.chars().allMatch(ch -> ch == '.' || Character.isDigit(ch))) {
				c.ajouter(champ, "adresse_interne");
				return null;
			}
			return uri;
		}
		catch (URISyntaxException e) {
			c.ajouter(champ, "lien_invalide");
			return null;
		}
	}

	private static List<String> reponses(Corps c, Object brut) {
		if (brut == null) {
			return List.of();
		}
		if (!(brut instanceof List<?> elements) || elements.size() > 5
				|| !elements.stream().allMatch(o -> o instanceof String s && s.length() <= 1000)) {
			c.ajouter("reponses", "format_invalide");
			return List.of();
		}
		return elements.stream().map(o -> ((String) o).strip()).toList();
	}

}
