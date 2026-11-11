package dev.jamii.api.deploiement;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.jamii.api.auth.UtilisateurConnecte;
import dev.jamii.api.systalink.PropositionSystalink;

/** Routes de contracts/deploiement.yaml. Les règles sont dans {@link MiseEnLigne}. */
@RestController
@RequestMapping("/api/projets/{projetId}/deploiements")
class DeploiementController {

	private final MiseEnLigne miseEnLigne;

	DeploiementController(MiseEnLigne miseEnLigne) {
		this.miseEnLigne = miseEnLigne;
	}

	@GetMapping
	Page lister(@PathVariable UUID projetId, @AuthenticationPrincipal UtilisateurConnecte moi,
			@RequestParam(required = false) String curseur, @RequestParam(defaultValue = "20") int limite) {
		if (limite < 1 || limite > 100) {
			throw MiseEnLigne.Refus.champ("limite", "hors_limites");
		}
		Curseur apres = Curseur.lire(curseur);
		// Un élément de plus que demandé : s'il existe, il y a une page suivante.
		List<Deploiement> lus = miseEnLigne.lister(projetId, moi.id(), apres.le(), apres.id(), limite + 1);
		List<Deploiement> page = lus.subList(0, Math.min(limite, lus.size()));
		String suivant = lus.size() > limite ? Curseur.ecrire(page.get(page.size() - 1)) : null;
		return new Page(page, suivant);
	}

	@PostMapping
	ResponseEntity<Deploiement> declarer(@PathVariable UUID projetId, @AuthenticationPrincipal UtilisateurConnecte moi,
			@RequestBody Declaration corps) {
		Deploiement cree = miseEnLigne.declarer(projetId, moi.id(), corps.url());
		return ResponseEntity.accepted()
			.location(URI.create("/api/projets/" + projetId + "/deploiements/" + cree.id()))
			.body(cree);
	}

	@PostMapping("/proposer-datacloud")
	PropositionSystalink proposerDatacloud(@PathVariable UUID projetId,
			@AuthenticationPrincipal UtilisateurConnecte moi) {
		return miseEnLigne.proposerDatacloud(projetId, moi.id());
	}

	@DeleteMapping("/{deploiementId}")
	ResponseEntity<Void> retirer(@PathVariable UUID projetId, @PathVariable UUID deploiementId,
			@AuthenticationPrincipal UtilisateurConnecte moi) {
		miseEnLigne.retirer(projetId, moi.id(), deploiementId);
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/{deploiementId}/verifier")
	ResponseEntity<Deploiement> verifier(@PathVariable UUID projetId, @PathVariable UUID deploiementId,
			@AuthenticationPrincipal UtilisateurConnecte moi) {
		return ResponseEntity.accepted().body(miseEnLigne.relancer(projetId, moi.id(), deploiementId));
	}

	/** Erreur au format du contrat (RFC 9457, avec `code`). */
	@ExceptionHandler(MiseEnLigne.Refus.class)
	ResponseEntity<ProblemDetail> refus(MiseEnLigne.Refus refus) {
		ProblemDetail erreur = ProblemDetail.forStatus(refus.statut);
		erreur.setType(URI.create("urn:jamii:erreur:" + refus.code));
		erreur.setTitle(refus.statut.getReasonPhrase());
		erreur.setProperty("code", refus.code);
		if (refus.champ != null) {
			erreur.setProperty("erreurs", List.of(Map.of("champ", refus.nomChamp, "code", refus.champ)));
		}
		var reponse = ResponseEntity.status(refus.statut);
		if (refus.statut == HttpStatus.TOO_MANY_REQUESTS) {
			reponse.header("Retry-After", "60");
		}
		return reponse.body(erreur);
	}

	record Declaration(String url) {
	}

	record Page(List<Deploiement> elements, String curseurSuivant) {
	}

	/** Curseur opaque pour le front : (cree_le, id) du dernier élément, encodé en base64. */
	record Curseur(Instant le, UUID id) {

		static Curseur lire(String curseur) {
			if (curseur == null || curseur.isBlank()) {
				return new Curseur(null, null);
			}
			try {
				String[] morceaux = new String(Base64.getUrlDecoder().decode(curseur), StandardCharsets.UTF_8)
					.split("\\|", 2);
				return new Curseur(Instant.parse(morceaux[0]), UUID.fromString(morceaux[1]));
			}
			catch (RuntimeException e) {
				throw MiseEnLigne.Refus.champ("curseur", "format_invalide");
			}
		}

		static String ecrire(Deploiement dernier) {
			String brut = dernier.creeLe() + "|" + dernier.id();
			return Base64.getUrlEncoder().withoutPadding().encodeToString(brut.getBytes(StandardCharsets.UTF_8));
		}

	}

}
