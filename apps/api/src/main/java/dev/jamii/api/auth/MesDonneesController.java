package dev.jamii.api.auth;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/me/export et DELETE /api/me (contracts/socle.yaml, `exporterMesDonnees` et `supprimerMonCompte`). */
@RestController
class MesDonneesController {

	static final int EXPORTS_PAR_JOUR = 5;

	private final MesDonnees donnees;

	private final LimiteurDemandes limiteur;

	MesDonneesController(MesDonnees donnees, LimiteurDemandes limiteur) {
		this.donnees = donnees;
		this.limiteur = limiteur;
	}

	@GetMapping("/api/me/export")
	ResponseEntity<?> exporter(@AuthenticationPrincipal UtilisateurConnecte moi, HttpServletRequest requete)
			throws ServletException {
		Instant maintenant = Instant.now();
		long attente = limiteur.essayer("export:" + moi.id(), EXPORTS_PAR_JOUR, Duration.ofDays(1), maintenant);
		if (attente > 0) {
			ProblemDetail erreur = ProblemDetail.forStatus(HttpStatus.TOO_MANY_REQUESTS);
			erreur.setType(URI.create("urn:jamii:erreur:trop_de_requetes"));
			erreur.setTitle("Trop d'exports aujourd'hui, réessaie plus tard");
			erreur.setProperty("code", "trop_de_requetes");
			return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
				.header(HttpHeaders.RETRY_AFTER, String.valueOf(attente))
				.body(erreur);
		}
		var export = donnees.exporter(moi.id(), maintenant);
		if (export.isEmpty()) {
			return compteDisparu(requete);
		}
		return ResponseEntity.ok()
			.header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename("jamii-export.json").build().toString())
			// Données personnelles : ni le navigateur ni un intermédiaire ne gardent de copie.
			.header(HttpHeaders.CACHE_CONTROL, "no-store")
			.contentType(MediaType.APPLICATION_JSON)
			.body(export.get());
	}

	/**
	 * Le contrat annonce `202` : la suppression est déjà faite quand la réponse part (une transaction, quelques
	 * requêtes), donc rien ne reste à moitié supprimé si l'API redémarre.
	 */
	@DeleteMapping("/api/me")
	ResponseEntity<Void> supprimer(@AuthenticationPrincipal UtilisateurConnecte moi, HttpServletRequest requete)
			throws ServletException {
		donnees.supprimer(moi.id(), Instant.now());
		// Ferme la session en cours et efface son cookie, même si le compte avait déjà disparu.
		requete.logout();
		return ResponseEntity.accepted().build();
	}

	private static ResponseEntity<Map<String, Object>> compteDisparu(HttpServletRequest requete) throws ServletException {
		requete.logout();
		throw new InsufficientAuthenticationException("Compte introuvable");
	}

}
