package dev.jamii.api.demandes;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.jamii.api.auth.UtilisateurConnecte;

/** Tendances du marché (public) et clients suivis (rôle Systalink) : contracts/systalink.yaml v0.2. */
@RestController
class InfosSystalinkController {

	private final InfosSystalink infos;

	InfosSystalinkController(InfosSystalink infos) {
		this.infos = infos;
	}

	@GetMapping("/api/systalink/tendances")
	ResponseEntity<?> tendances(@RequestParam(required = false) String periode) {
		String p = periode == null || periode.isBlank() ? "mois" : periode;
		Duration duree = switch (p) {
			case "semaine" -> Duration.ofDays(7);
			case "mois" -> Duration.ofDays(30);
			case "tout" -> null;
			default -> Duration.ZERO;
		};
		if (Duration.ZERO.equals(duree)) {
			return probleme(HttpStatus.BAD_REQUEST, "validation", List.of(Map.of("champ", "periode", "code", "format_invalide")));
		}
		Instant depuis = duree == null ? null : Instant.now().minus(duree);
		return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
			.body(infos.tendances(p, depuis));
	}

	@GetMapping("/api/systalink/clients-suivis")
	ResponseEntity<?> clientsSuivis(@RequestParam(required = false) String statut,
			@RequestParam(required = false) String curseur, @RequestParam(required = false) Integer limite,
			@RequestHeader(value = HttpHeaders.ACCEPT, required = false) String accept,
			@AuthenticationPrincipal UtilisateurConnecte moi) {
		if (!infos.aAcces(moi)) {
			return probleme(HttpStatus.FORBIDDEN, "interdit", null);
		}
		List<Map<String, String>> erreurs = new ArrayList<>();
		if (statut != null && !InfosSystalink.STATUTS.contains(statut)) {
			erreurs.add(Map.of("champ", "statut", "code", "format_invalide"));
		}
		int taille = limite == null ? 20 : limite;
		if (taille < 1 || taille > 100) {
			erreurs.add(Map.of("champ", "limite", "code", "hors_limites"));
		}
		if (!erreurs.isEmpty()) {
			return probleme(HttpStatus.BAD_REQUEST, "validation", erreurs);
		}
		boolean enCsv = accept != null && accept.contains("text/csv");
		InfosSystalink.Page page;
		try {
			page = enCsv ? tout(statut) : infos.clientsSuivis(statut, curseur, taille);
		}
		catch (IllegalArgumentException | ArrayIndexOutOfBoundsException | java.time.format.DateTimeParseException e) {
			return probleme(HttpStatus.BAD_REQUEST, "validation", List.of(Map.of("champ", "curseur", "code", "format_invalide")));
		}
		if (enCsv) {
			return ResponseEntity.ok().cacheControl(CacheControl.noStore())
				.contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
				.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"clients-suivis-jamii.csv\"")
				.body(InfosSystalink.csv(page.elements()));
		}
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(page);
	}

	record Suivi(String statut) {
	}

	@PatchMapping("/api/systalink/clients-suivis/{demandeId}")
	ResponseEntity<?> suivre(@PathVariable UUID demandeId, @RequestBody Suivi suivi,
			@AuthenticationPrincipal UtilisateurConnecte moi) {
		if (!infos.aAcces(moi)) {
			return probleme(HttpStatus.FORBIDDEN, "interdit", null);
		}
		if (suivi.statut() == null || !InfosSystalink.STATUTS.contains(suivi.statut())) {
			return probleme(HttpStatus.BAD_REQUEST, "validation", List.of(Map.of("champ", "statut", "code", "format_invalide")));
		}
		if (!infos.noterSuivi(demandeId, suivi.statut())) {
			return probleme(HttpStatus.NOT_FOUND, "introuvable", null);
		}
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(infos.client(demandeId).orElseThrow());
	}

	/** Le CSV prend toutes les lignes du filtre, page après page. */
	private InfosSystalink.Page tout(String statut) {
		List<InfosSystalink.ClientSuivi> tous = new ArrayList<>();
		String curseur = null;
		do {
			InfosSystalink.Page page = infos.clientsSuivis(statut, curseur, 100);
			tous.addAll(page.elements());
			curseur = page.curseurSuivant();
		}
		while (curseur != null);
		return new InfosSystalink.Page(tous, null);
	}

	private static ResponseEntity<ProblemDetail> probleme(HttpStatus statut, String code, List<Map<String, String>> erreurs) {
		ProblemDetail erreur = ProblemDetail.forStatus(statut);
		erreur.setType(URI.create("urn:jamii:erreur:" + code));
		erreur.setTitle(switch (code) {
			case "interdit" -> "Accès réservé à Systalink";
			case "introuvable" -> "Ressource introuvable";
			default -> "Certains champs sont invalides";
		});
		erreur.setProperty("code", code);
		if (erreurs != null) {
			erreur.setProperty("erreurs", erreurs);
		}
		return ResponseEntity.status(statut).body(erreur);
	}

}
