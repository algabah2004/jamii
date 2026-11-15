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
import dev.jamii.api.systalink.ContexteMoment;
import dev.jamii.api.systalink.MomentsSystalink;
import dev.jamii.api.systalink.ServiceSystalink;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/** Les routes de contracts/demandes.yaml v0.2 : décrire un besoin, lire son orientation, choisir les offres Systalink. */
@RestController
class DemandesController {

	private static final Pattern PAYS = Pattern.compile("^[A-Z]{2}$");

	record NouvelleDemande(String titre, String description, Integer dureeSemaines, String secteur, String ville,
			String pays, Boolean offresSystalink) {
	}

	record ChoixOffres(Boolean accepte) {
	}

	record NouvellePrecision(String texte) {
	}

	record Precision(String texte, Instant creeLe) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	record EtatDeLaTache(String statut, String origine, JsonNode resultat, String erreurCode) {
	}

	/** Ce que la communauté voit de la demande une fois publiée. */
	record Publication(Instant publieeLe, String resume) {
	}

	record ResumePublic(String resume) {
	}

	record Demande(UUID id, String titre, String description, Integer dureeSemaines, String secteur, String ville,
			String pays, boolean offresSystalink, Instant creeLe, EtatDeLaTache plan, EtatDeLaTache orientation,
			List<Recommandations.Recommandation> recommandations, List<Precision> precisions, Publication publication,
			int conseilsRestants) {
	}

	private final GestionDemandes gestion;

	private final Recommandations recommandations;

	private final WebhookSystalink webhook;

	private final PublicationsDemandes publications;

	private final MomentsSystalink moments;

	DemandesController(GestionDemandes gestion, Recommandations recommandations, WebhookSystalink webhook,
			PublicationsDemandes publications, MomentsSystalink moments) {
		this.gestion = gestion;
		this.recommandations = recommandations;
		this.webhook = webhook;
		this.publications = publications;
		this.moments = moments;
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
			return tropDeConseils(trop);
		}
		return ResponseEntity.created(URI.create("/api/demandes/" + id)).cacheControl(CacheControl.noStore())
			.body(vue(gestion.lire(id, moi.id()).orElseThrow()));
	}

	/** « Tes demandes » : pour retrouver un besoin déjà décrit, même après avoir quitté la page. */
	@GetMapping("/api/demandes")
	ResponseEntity<List<GestionDemandes.Resume>> lister(@AuthenticationPrincipal UtilisateurConnecte moi) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(gestion.mesDemandes(moi.id()));
	}

	/**
	 * Préciser son besoin après avoir lu la réponse (« ce n'est pas tout à fait ça »). L'analyse repart avec le besoin
	 * de départ et toutes les précisions. 5 au plus par demande ; elles comptent aussi dans la limite de l'heure.
	 */
	@PostMapping("/api/demandes/{demandeId}/precisions")
	ResponseEntity<?> preciser(@PathVariable UUID demandeId, @RequestBody NouvellePrecision precision,
			@AuthenticationPrincipal UtilisateurConnecte moi) {
		String texte = propre(precision.texte());
		if (texte.length() < 3 || texte.length() > 500) {
			return probleme(HttpStatus.BAD_REQUEST, "validation", "Certains champs sont invalides",
					List.of(Map.of("champ", "texte", "code", "longueur_invalide")));
		}
		GestionDemandes.Ajout ajout;
		try {
			ajout = gestion.preciser(demandeId, moi.id(), texte, Instant.now());
		}
		catch (GestionDemandes.TropDePlans trop) {
			return tropDeConseils(trop);
		}
		return switch (ajout) {
			case INTROUVABLE -> probleme(HttpStatus.NOT_FOUND, "introuvable", "Ressource introuvable", null);
			case LIMITE -> probleme(HttpStatus.CONFLICT, "trop_de_precisions",
					"Cette demande a déjà " + GestionDemandes.PRECISIONS_MAX + " précisions : décris un nouveau besoin", null);
			case AJOUTEE -> ResponseEntity.ok().cacheControl(CacheControl.noStore())
				.body(vue(gestion.lire(demandeId, moi.id()).orElseThrow()));
		};
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

	/**
	 * Publier sa demande : l'auteur a relu son cahier des charges et choisit le résumé que la communauté verra.
	 * Possible seulement quand le plan est prêt (409 `plan_non_pret`) et une seule fois (409 `deja_publiee`).
	 */
	@PostMapping("/api/demandes/{demandeId}/publication")
	ResponseEntity<?> publier(@PathVariable UUID demandeId, @RequestBody ResumePublic corps,
			@AuthenticationPrincipal UtilisateurConnecte moi) {
		var fiche = gestion.lire(demandeId, moi.id());
		if (fiche.isEmpty()) {
			return probleme(HttpStatus.NOT_FOUND, "introuvable", "Ressource introuvable", null);
		}
		// Le résumé est public : ni e-mail ni téléphone, même si la personne les a écrits.
		String resume = GestionDemandes.sansCoordonnees(propre(corps.resume())).strip();
		if (resume.length() < 20 || resume.length() > 600) {
			return probleme(HttpStatus.BAD_REQUEST, "validation", "Certains champs sont invalides",
					List.of(Map.of("champ", "resume", "code", "longueur_invalide")));
		}
		Instant maintenant = Instant.now();
		var etat = gestion.orientation(fiche.get(), maintenant);
		if (!"terminee".equals(etat.statut()) || etat.resultat() == null) {
			return probleme(HttpStatus.CONFLICT, "plan_non_pret", "Le cahier des charges n'est pas encore prêt", null);
		}
		try {
			publications.publier(fiche.get(), resume, etat.resultat(), maintenant);
		}
		catch (PublicationsDemandes.DejaPubliee deja) {
			return probleme(HttpStatus.CONFLICT, "deja_publiee", "Cette demande est déjà publiée", null);
		}
		return ResponseEntity.ok().cacheControl(CacheControl.noStore())
			.body(vue(gestion.lire(demandeId, moi.id()).orElseThrow()));
	}

	private Demande vue(GestionDemandes.Fiche fiche) {
		var etat = gestion.orientation(fiche, Instant.now());
		if (fiche.offresSystalink() || "terminee".equals(etat.statut())) {
			webhook.signalerSiPret(fiche.id());
		}
		var orientation = new EtatDeLaTache(etat.statut(), etat.origine(), avecMoments(fiche.id(), etat.resultat()),
				etat.erreurCode());
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
				fiche.ville(), fiche.pays(), fiche.offresSystalink(), fiche.creeLe(), etatDuPlan, orientation, recommandes,
				fiche.precisions().stream().map(p -> new Precision(p.texte(), p.creeLe())).toList(),
				fiche.publieeLe() == null ? null : new Publication(fiche.publieeLe(), fiche.publieeResume()),
				gestion.analysesRestantes(fiche.auteurId(), Instant.now()));
	}

	/**
	 * Chaque service Systalink montré à la personne compte une fois, par service et par demande, dans le tableau de bord
	 * Systalink (#65). Le front reçoit `momentId` pour signaler le clic sur « Voir le service ». Aucun identifiant de
	 * personne n'est enregistré dans le moment.
	 */
	private JsonNode avecMoments(UUID demandeId, JsonNode resultat) {
		if (resultat == null || !resultat.path("services").isArray() || resultat.path("services").isEmpty()) {
			return resultat;
		}
		ObjectNode copie = (ObjectNode) resultat.deepCopy();
		for (JsonNode service : copie.path("services")) {
			ServiceSystalink.depuisCatalogue(service.path("id").asString(null))
				.ifPresent(mesure -> ((ObjectNode) service).put("momentId",
						moments.proposerUneFois(mesure, ContexteMoment.DEMANDE, demandeId).toString()));
		}
		return copie;
	}

	/** 429 : la limite de l'heure (`trop_de_requetes`) ou les conseils du jour épuisés (`quota_du_jour`). */
	private ResponseEntity<?> tropDeConseils(GestionDemandes.TropDePlans trop) {
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
			.header(HttpHeaders.RETRY_AFTER, String.valueOf(trop.attenteSecondes))
			.body(trop.duJour
					? problemeSimple(HttpStatus.TOO_MANY_REQUESTS, "quota_du_jour",
							"Tu as utilisé tes " + GestionDemandes.ANALYSES_PAR_JOUR + " conseils du jour, reviens demain")
					: problemeSimple(HttpStatus.TOO_MANY_REQUESTS, "trop_de_requetes", "Trop de plans demandés, réessaie plus tard"));
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
