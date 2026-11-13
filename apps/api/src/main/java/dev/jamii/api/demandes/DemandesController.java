package dev.jamii.api.demandes;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import dev.jamii.api.auth.UtilisateurConnecte;
import tools.jackson.databind.JsonNode;

/** Les routes de contracts/demandes.yaml v0.2 : décrire un besoin, lire son orientation, choisir les offres Systalink. */
@RestController
class DemandesController {

	private static final Pattern PAYS = Pattern.compile("^[A-Z]{2}$");

	record NouvelleDemande(String titre, String description, Integer dureeSemaines, String secteur, String ville,
			String pays, Boolean offresSystalink) {
	}

	record ChoixOffres(Boolean accepte) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	record EtatDeLaTache(String statut, String origine, JsonNode resultat, String erreurCode) {
	}

	record Demande(UUID id, String titre, String description, Integer dureeSemaines, String secteur, String ville,
			String pays, boolean offresSystalink, Instant creeLe, EtatDeLaTache plan, EtatDeLaTache orientation,
			List<Recommandations.Recommandation> recommandations) {
	}

	private final GestionDemandes gestion;

	private final Recommandations recommandations;

	DemandesController(GestionDemandes gestion, Recommandations recommandations) {
		this.gestion = gestion;
		this.recommandations = recommandations;
	}

	@PostMapping("/api/demandes")
	ResponseEntity<?> creer(@RequestBody NouvelleDemande demande, @AuthenticationPrincipal UtilisateurConnecte moi) {
		String titre = propre(demande.titre());
		String description = propre(demande.description());
		String secteur = facultatif(demande.secteur());
		String ville = facultatif(demande.ville());
		String pays = facultatif(demande.pays());
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
		if (secteur != null && secteur.length() > 60) {
			erreurs.add(Map.of("champ", "secteur", "code", "longueur_invalide"));
		}
		if (ville != null && ville.length() > 80) {
			erreurs.add(Map.of("champ", "ville", "code", "longueur_invalide"));
		}
		if (pays != null && !PAYS.matcher(pays).matches()) {
			erreurs.add(Map.of("champ", "pays", "code", "format_invalide"));
		}
		if (!erreurs.isEmpty()) {
			return probleme(HttpStatus.BAD_REQUEST, "validation", "Certains champs sont invalides", erreurs);
		}
		// Jamais vrai par défaut : seulement si la personne a coché la case.
		var contexte = new GestionDemandes.Contexte(secteur, ville, pays, Boolean.TRUE.equals(demande.offresSystalink()));
		UUID id;
		try {
			id = gestion.creer(moi.id(), titre, description, demande.dureeSemaines(), contexte, Instant.now());
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

	@PutMapping("/api/demandes/{demandeId}/offres-systalink")
	ResponseEntity<?> choisirOffres(@PathVariable UUID demandeId, @RequestBody ChoixOffres choix,
			@AuthenticationPrincipal UtilisateurConnecte moi) {
		if (choix.accepte() == null) {
			return probleme(HttpStatus.BAD_REQUEST, "validation", "Certains champs sont invalides",
					List.of(Map.of("champ", "accepte", "code", "obligatoire")));
		}
		if (!gestion.choisirOffres(demandeId, moi.id(), choix.accepte(), Instant.now())) {
			return probleme(HttpStatus.NOT_FOUND, "introuvable", "Ressource introuvable", null);
		}
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
			.body(vue(gestion.lire(demandeId, moi.id()).orElseThrow()));
	}

	private Demande vue(GestionDemandes.Fiche fiche) {
		var etat = gestion.orientation(fiche, Instant.now());
		var orientation = new EtatDeLaTache(etat.statut(), etat.origine(), etat.resultat(), etat.erreurCode());
		// `plan` reste pour les écrans existants : c'est le plan de l'orientation.
		JsonNode plan = etat.resultat() == null || etat.resultat().path("plan").isNull() ? null : etat.resultat().get("plan");
		var etatDuPlan = new EtatDeLaTache(etat.statut(), etat.origine(), plan, etat.erreurCode());
		List<Recommandations.Recommandation> recommandes = List.of();
		if (etat.resultat() != null && etat.resultat().path("developpeursNecessaires").asBoolean(false)) {
			List<String> competences = new ArrayList<>();
			etat.resultat().path("competences").forEach(c -> competences.add(c.asString("")));
			recommandes = recommandations.pour(competences, fiche.ville(), fiche.pays(), fiche.auteurId());
		}
		return new Demande(fiche.id(), fiche.titre(), fiche.description(), fiche.dureeSemaines(), fiche.secteur(),
				fiche.ville(), fiche.pays(), fiche.offresSystalink(), fiche.creeLe(), etatDuPlan, orientation, recommandes);
	}

	private static String propre(String texte) {
		return texte == null ? "" : texte.strip();
	}

	private static String facultatif(String texte) {
		String propre = propre(texte);
		return propre.isEmpty() ? null : propre;
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
