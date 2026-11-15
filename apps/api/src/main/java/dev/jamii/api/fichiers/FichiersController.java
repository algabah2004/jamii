package dev.jamii.api.fichiers;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

import dev.jamii.api.auth.UtilisateurConnecte;

/**
 * Envoyer un fichier, puis le lire (contracts/fichiers.yaml). Réservé aux personnes connectées. Un fichier se lit par
 * son identifiant, impossible à deviner ; il est servi avec son vrai type, jamais interprété par le navigateur
 * (`nosniff`, et une politique de contenu qui bloque tout script), et les vidéos se lisent par morceaux (Range).
 */
@RestController
public class FichiersController {

	private final Fichiers fichiers;

	FichiersController(Fichiers fichiers) {
		this.fichiers = fichiers;
	}

	@PostMapping(path = "/api/fichiers", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	ResponseEntity<Fichiers.Piece> envoyer(@RequestParam("fichier") MultipartFile fichier,
			@RequestParam(required = false) UUID destinataireId, @AuthenticationPrincipal UtilisateurConnecte moi)
			throws IOException {
		Instant maintenant = Instant.now();
		long attente = fichiers.attenteAvantEnvoi(moi.id(), maintenant);
		if (attente > 0) {
			throw new ErreurFichier(429, "trop_de_requetes", "Trop de fichiers envoyés en une heure", attente);
		}
		var piece = fichiers.enregistrer(moi.id(), fichier.getOriginalFilename(), fichier.getBytes(), maintenant, destinataireId);
		return ResponseEntity.created(URI.create(piece.url())).body(piece);
	}

	@GetMapping("/api/fichiers/{id}")
	ResponseEntity<Resource> lire(@PathVariable UUID id, @AuthenticationPrincipal UtilisateurConnecte moi) {
		var contenu = fichiers.lire(id, moi.id()).orElseThrow(() -> new ErreurFichier(404, "introuvable", "Fichier introuvable"));
		var piece = contenu.piece();
		boolean enLigne = !"document".equals(piece.sorte()) || piece.type().equals("application/pdf") || piece.type().startsWith("text/plain");
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(piece.type()))
			.header(HttpHeaders.CONTENT_DISPOSITION, (enLigne ? ContentDisposition.inline() : ContentDisposition.attachment())
				.filename(piece.nom(), java.nio.charset.StandardCharsets.UTF_8).build().toString())
			.header("X-Content-Type-Options", "nosniff")
			.header("Content-Security-Policy", "default-src 'none'; img-src 'self'; media-src 'self'; style-src 'unsafe-inline'; sandbox")
			.cacheControl(contenu.prive() ? CacheControl.noStore() : CacheControl.maxAge(Duration.ofDays(30)).cachePrivate().immutable())
			.body(new ByteArrayResource(contenu.octets()));
	}

	@ExceptionHandler(ErreurFichier.class)
	ResponseEntity<ProblemDetail> erreur(ErreurFichier e) {
		return probleme(e);
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	ResponseEntity<ProblemDetail> tropGros(MaxUploadSizeExceededException e) {
		return probleme(new ErreurFichier(413, "trop_gros", "Ce fichier dépasse 25 Mo"));
	}

	/** La réponse d'une erreur de fichier, au format des autres (ProblemDetail avec `code`). */
	public static ResponseEntity<ProblemDetail> probleme(ErreurFichier e) {
		HttpStatus statut = HttpStatus.valueOf(e.statut);
		ProblemDetail p = ProblemDetail.forStatus(statut);
		p.setType(URI.create("urn:jamii:erreur:" + e.code));
		p.setTitle(e.getMessage());
		p.setProperty("code", e.code);
		var reponse = ResponseEntity.status(statut);
		if (e.attenteSecondes > 0) {
			reponse = reponse.header(HttpHeaders.RETRY_AFTER, String.valueOf(e.attenteSecondes));
		}
		return reponse.body(p);
	}

}
