package dev.jamii.api.demandes;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import dev.jamii.api.auth.UtilisateurConnecte;
import tools.jackson.databind.JsonNode;

/** POST /api/demandes et GET /api/demandes/{demandeId} (contracts/demandes.yaml). */
@RestController
class DemandesController {

	record NouvelleDemande(String titre, String description, Integer dureeSemaines) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	record PlanDeLaDemande(String statut, String origine, JsonNode resultat, String erreurCode) {
	}

	record Demande(UUID id, String titre, String description, Integer dureeSemaines, Instant creeLe, PlanDeLaDemande plan) {
	}

	private final GestionDemandes gestion;

	DemandesController(GestionDemandes gestion) {
		this.gestion = gestion;
	}

	@PostMapping("/api/demandes")
	ResponseEntity<?> creer(@RequestBody NouvelleDemande demande, @AuthenticationPrincipal UtilisateurConnecte moi) {
		String titre = demande.titre() == null ? "" : demande.titre().strip();
		String description = demande.description() == null ? "" : demande.description().strip();
		List<Map<String, String>> erreurs = new ArrayList<>();
		if (titre.length() < 3 || titre.length() > 120) {
			erreurs.add(Map.of("champ", "titre", "code", "longueur_invalide"));
		}
		if (description.length() < 20 || description.length() > 4000) {
			erreurs.add(Map.of("champ", "description", "code", "longueur_invalide"));
		}
		if (demande.dureeSemaines() != null && (demande.dureeSemaines() < 1 || demande.dureeSemaines() > 26)) {
			erreurs.add(Map.of("champ", "dureeSemaines", "code", "hors_limites"));
		}
		if (!erreurs.isEmpty()) {
			return probleme(HttpStatus.BAD_REQUEST, "validation", "Certains champs sont invalides", erreurs);
		}
		UUID id;
		try {
			id = gestion.creer(moi.id(), titre, description, demande.dureeSemaines(), Instant.now());
		}
		catch (GestionDemandes.TropDePlans trop) {
			return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
				.header(HttpHeaders.RETRY_AFTER, String.valueOf(trop.attenteSecondes))
				.body(problemeSimple(HttpStatus.TOO_MANY_REQUESTS, "trop_de_requetes", "Trop de plans demandés, réessaie plus tard"));
		}
		return ResponseEntity.created(URI.create("/api/demandes/" + id)).cacheControl(CacheControl.noStore())
			.body(vue(gestion.lire(id, moi.id()).orElseThrow()));
	}

	@GetMapping("/api/demandes/{demandeId}")
	ResponseEntity<?> lire(@PathVariable UUID demandeId, @AuthenticationPrincipal UtilisateurConnecte moi) {
		// Pour tout autre utilisateur que l'auteur, la demande n'existe pas : 404, jamais 403.
		return gestion.lire(demandeId, moi.id())
			.<ResponseEntity<?>>map(fiche -> ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(vue(fiche)))
			.orElseGet(() -> probleme(HttpStatus.NOT_FOUND, "introuvable", "Ressource introuvable", null));
	}

	private Demande vue(GestionDemandes.Fiche fiche) {
		var plan = gestion.plan(fiche, Instant.now());
		return new Demande(fiche.id(), fiche.titre(), fiche.description(), fiche.dureeSemaines(), fiche.creeLe(),
				new PlanDeLaDemande(plan.statut(), plan.origine(), plan.resultat(), plan.erreurCode()));
	}

	private static ProblemDetail problemeSimple(HttpStatus statut, String code, String titre) {
		ProblemDetail erreur = ProblemDetail.forStatus(statut);
		erreur.setType(URI.create("urn:jamii:erreur:" + code));
		erreur.setTitle(titre);
		erreur.setProperty("code", code);
		return erreur;
	}

	private static ResponseEntity<ProblemDetail> probleme(HttpStatus statut, String code, String titre,
			List<Map<String, String>> erreurs) {
		ProblemDetail erreur = problemeSimple(statut, code, titre);
		if (erreurs != null) {
			erreur.setProperty("erreurs", erreurs);
		}
		return ResponseEntity.status(statut).body(erreur);
	}

}
