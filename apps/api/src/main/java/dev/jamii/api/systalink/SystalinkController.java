package dev.jamii.api.systalink;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Routes de contracts/systalink.yaml. */
@RestController
@EnableConfigurationProperties(LiensSystalink.class)
class SystalinkController {

	private final CalculTableauDeBord calcul;

	private final MomentsSystalink moments;

	SystalinkController(CalculTableauDeBord calcul, MomentsSystalink moments) {
		this.calcul = calcul;
		this.moments = moments;
	}

	@GetMapping("/api/systalink/tableau-de-bord")
	ResponseEntity<?> tableauDeBord(@RequestParam(required = false) String periode) {
		return Periode.lire(periode)
			.<ResponseEntity<?>>map(p -> ResponseEntity.ok()
				.cacheControl(CacheControl.maxAge(CalculTableauDeBord.DUREE_CACHE).cachePublic())
				.body(calcul.pour(p)))
			.orElseGet(() -> probleme(HttpStatus.BAD_REQUEST, "validation", "Certains champs sont invalides",
					List.of(Map.of("champ", "periode", "code", "format_invalide"))));
	}

	@PostMapping("/api/systalink/moments/{momentId}/accepter")
	ResponseEntity<?> accepter(@PathVariable UUID momentId) {
		return moments.accepter(momentId) ? ResponseEntity.noContent().build()
				: probleme(HttpStatus.NOT_FOUND, "introuvable", "Ressource introuvable", null);
	}

	/** Erreur au format du contrat (RFC 9457, avec `code`). */
	private static ResponseEntity<ProblemDetail> probleme(HttpStatus statut, String code, String titre,
			List<Map<String, String>> erreurs) {
		ProblemDetail erreur = ProblemDetail.forStatus(statut);
		erreur.setType(URI.create("urn:jamii:erreur:" + code));
		erreur.setTitle(titre);
		erreur.setProperty("code", code);
		if (erreurs != null) {
			erreur.setProperty("erreurs", erreurs);
		}
		return ResponseEntity.status(statut).body(erreur);
	}

}
