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
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.jamii.api.auth.UtilisateurConnecte;

/** Routes de contracts/systalink.yaml, réservées au rôle Systalink (sauf « accepter », appelé par tout utilisateur). */
@RestController
@EnableConfigurationProperties(LiensSystalink.class)
class SystalinkController {

	private final CalculTableauDeBord calcul;

	private final MomentsSystalink moments;

	private final AccesSystalink acces;

	SystalinkController(CalculTableauDeBord calcul, MomentsSystalink moments, AccesSystalink acces) {
		this.calcul = calcul;
		this.moments = moments;
		this.acces = acces;
	}

	/** Pour le menu : le lien vers l'espace Systalink ne s'affiche qu'à son équipe. */
	@GetMapping("/api/systalink/acces")
	Map<String, Boolean> acces(@AuthenticationPrincipal UtilisateurConnecte moi) {
		return Map.of("acces", acces.aAcces(moi), "codeJury", acces.codeJuryOuvert());
	}

	record CodeJury(String code) {
	}

	/** Le jury de la CADEV entre le code donné avec la soumission et voit l'espace Systalink. */
	@PostMapping("/api/systalink/code-jury")
	ResponseEntity<?> codeJury(@RequestBody CodeJury corps, @AuthenticationPrincipal UtilisateurConnecte moi) {
		if (moi == null) {
			return probleme(HttpStatus.UNAUTHORIZED, "non_connecte", "Connexion requise", null);
		}
		return switch (acces.entrerCode(moi, corps == null ? null : corps.code())) {
			case ACCORDE -> ResponseEntity.noContent().build();
			case FAUX -> probleme(HttpStatus.BAD_REQUEST, "validation", "Certains champs sont invalides",
					List.of(Map.of("champ", "code", "code", "code_faux")));
			case TROP_D_ESSAIS -> probleme(HttpStatus.TOO_MANY_REQUESTS, "trop_de_requetes",
					"Trop d'essais, demande de l'aide à l'équipe Jamii", null);
			case FERME -> probleme(HttpStatus.NOT_FOUND, "introuvable", "Ressource introuvable", null);
		};
	}

	@GetMapping("/api/systalink/tableau-de-bord")
	ResponseEntity<?> tableauDeBord(@RequestParam(required = false) String periode,
			@AuthenticationPrincipal UtilisateurConnecte moi) {
		if (!acces.aAcces(moi)) {
			return probleme(HttpStatus.FORBIDDEN, "interdit", "Réservé à l'équipe Systalink", null);
		}
		return Periode.lire(periode)
			.<ResponseEntity<?>>map(p -> ResponseEntity.ok()
				.cacheControl(CacheControl.maxAge(CalculTableauDeBord.DUREE_CACHE).cachePrivate())
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
