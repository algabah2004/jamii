package dev.jamii.api.vitrine;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.jamii.api.deploiement.Hebergeur;

/**
 * `GET /api/vitrine` (contracts/vitrine.yaml) : les projets en ligne, lisibles sans connexion (la route est publique
 * dans la sécurité). La réponse peut être gardée une minute par un cache.
 */
@RestController
@RequestMapping("/api/vitrine")
class VitrineController {

	private final Vitrine vitrine;

	VitrineController(Vitrine vitrine) {
		this.vitrine = vitrine;
	}

	@GetMapping
	ResponseEntity<?> lister(@RequestParam(required = false) String hebergeur, @RequestParam(required = false) String pays,
			@RequestParam(defaultValue = "20") int limite, @RequestParam(required = false) String curseur) {
		Hebergeur filtre = null;
		if (hebergeur != null) {
			filtre = "datacloud".equals(hebergeur) ? Hebergeur.DATACLOUD : "autre".equals(hebergeur) ? Hebergeur.AUTRE : null;
			if (filtre == null) {
				return invalide("hebergeur", "valeur_inconnue");
			}
		}
		if (pays != null && !pays.matches("^[A-Z]{2}$")) {
			return invalide("pays", "format_invalide");
		}
		if (limite < 1 || limite > 100) {
			return invalide("limite", "hors_limites");
		}
		Vitrine.Curseur apres = null;
		if (curseur != null) {
			apres = Vitrine.Curseur.lire(curseur).orElse(null);
			if (apres == null) {
				return invalide("curseur", "invalide");
			}
		}
		// Tel que le contrat le donne (contracts/vitrine.yaml, en-tête Cache-Control de la réponse 200).
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "public, max-age=60")
			.body(vitrine.lister(filtre, pays, limite, apres));
	}

	/** Une erreur de paramètre au format du contrat (RFC 9457 avec `code` et la liste `erreurs`). */
	private static ResponseEntity<ProblemDetail> invalide(String champ, String code) {
		ProblemDetail probleme = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
		probleme.setType(URI.create("urn:jamii:erreur:validation"));
		probleme.setTitle("Certains champs sont invalides");
		probleme.setProperty("code", "validation");
		probleme.setProperty("erreurs", List.of(Map.of("champ", champ, "code", code)));
		return ResponseEntity.badRequest().body(probleme);
	}

}
