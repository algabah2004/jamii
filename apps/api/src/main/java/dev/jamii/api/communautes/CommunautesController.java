package dev.jamii.api.communautes;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.jamii.api.auth.Annuaire;
import dev.jamii.api.auth.Compte;
import dev.jamii.api.auth.UtilisateurConnecte;
import dev.jamii.api.projets.AccesAuProjet;
import dev.jamii.api.projets.Projets;

/**
 * Communautés et publications (contracts/communautes.yaml). Toutes les routes demandent une session (SecuriteConfig) ;
 * publier demande d'être membre, vérifié ici à chaque appel. Répondre, accepter et valider arrivent dans la PR suivante.
 */
@RestController
class CommunautesController {

	private static final Set<String> TYPES = Set.of("question", "discussion", "montre_ton_projet");

	record PersonneResume(UUID id, String nom, String avatarUrl) {
	}

	record Communaute(UUID id, String slug, String nom, String description, String theme, String pays,
			int nombreMembres, int questionsOuvertes, boolean membre) {
	}

	record PublicationResume(UUID id, String communaute, String type, String titre, PersonneResume auteur,
			int nombreReponses, Instant accepteeLe, Instant creeLe) {
	}

	record PropositionIa(String statut) {
	}

	record Reponse(UUID id, String contenu, PersonneResume auteur, boolean proposeeParIa, PersonneResume valideePar,
			boolean acceptee, Instant creeLe) {
	}

	record Moi(boolean estAuteur, boolean estMembre) {
	}

	record Publication(UUID id, String communaute, String type, String titre, PersonneResume auteur, int nombreReponses,
			Instant accepteeLe, Instant creeLe, String contenu, UUID projetId, UUID reponseAccepteeId,
			PropositionIa propositionIa, List<Reponse> reponses, Moi moi) {
	}

	record Page<T>(List<T> elements, String curseurSuivant) {
	}

	record NouvellePublication(String type, String titre, String contenu, UUID projetId) {
	}

	private final GestionCommunautes gestion;

	private final Annuaire annuaire;

	private final Projets projets;

	private final VuesPublications vues;

	private final PropositionsIa ia;

	CommunautesController(GestionCommunautes gestion, Annuaire annuaire, Projets projets, VuesPublications vues,
			PropositionsIa ia) {
		this.gestion = gestion;
		this.annuaire = annuaire;
		this.projets = projets;
		this.vues = vues;
		this.ia = ia;
	}

	@GetMapping("/api/communautes")
	ResponseEntity<?> lister(@RequestParam(defaultValue = "20") String limite, @RequestParam(required = false) String curseur,
			@RequestParam(required = false) String membre, @AuthenticationPrincipal UtilisateurConnecte moi) {
		Integer taille = taille(limite);
		var apres = curseur == null ? null : GestionCommunautes.Curseur.decoder(curseur).filter(c -> c.a().matches("\\d{1,9}"));
		if (taille == null || (apres != null && apres.isEmpty()) || (membre != null && !"moi".equals(membre))) {
			return validation(taille == null ? "limite" : membre != null && !"moi".equals(membre) ? "membre" : "curseur");
		}
		var fiches = gestion.lister(moi.id(), membre != null, taille + 1, apres == null ? null : apres.get(), Instant.now());
		boolean suite = fiches.size() > taille;
		var page = fiches.subList(0, Math.min(taille, fiches.size()));
		String suivant = suite ? new GestionCommunautes.Curseur(String.valueOf(page.getLast().activite()), page.getLast().slug()).encoder() : null;
		return ResponseEntity.ok(new Page<>(page.stream().map(CommunautesController::communaute).toList(), suivant));
	}

	@GetMapping("/api/communautes/{slug}")
	ResponseEntity<?> lire(@PathVariable String slug, @AuthenticationPrincipal UtilisateurConnecte moi) {
		return gestion.lire(slug, moi.id(), Instant.now())
			.<ResponseEntity<?>>map(f -> ResponseEntity.ok(communaute(f)))
			.orElseGet(CommunautesController::introuvable);
	}

	@PostMapping("/api/communautes/{slug}/adhesion")
	ResponseEntity<?> rejoindre(@PathVariable String slug, @AuthenticationPrincipal UtilisateurConnecte moi) {
		var fiche = gestion.lire(slug, moi.id(), Instant.now());
		if (fiche.isEmpty()) {
			return introuvable();
		}
		gestion.rejoindre(fiche.get().id(), moi.id(), Instant.now());
		return ResponseEntity.noContent().build();
	}

	@DeleteMapping("/api/communautes/{slug}/adhesion")
	ResponseEntity<?> quitter(@PathVariable String slug, @AuthenticationPrincipal UtilisateurConnecte moi) {
		var fiche = gestion.lire(slug, moi.id(), Instant.now());
		if (fiche.isEmpty()) {
			return introuvable();
		}
		gestion.quitter(fiche.get().id(), moi.id());
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/api/communautes/{slug}/publications")
	ResponseEntity<?> publications(@PathVariable String slug, @RequestParam(defaultValue = "20") String limite,
			@RequestParam(required = false) String curseur, @RequestParam(required = false) String type,
			@RequestParam(defaultValue = "false") boolean sansReponseAcceptee, @AuthenticationPrincipal UtilisateurConnecte moi) {
		Integer taille = taille(limite);
		var apres = curseur == null ? null : GestionCommunautes.Curseur.decoder(curseur).filter(CommunautesController::curseurDatee);
		if (taille == null || (apres != null && apres.isEmpty()) || (type != null && !TYPES.contains(type))) {
			return validation(taille == null ? "limite" : type != null && !TYPES.contains(type) ? "type" : "curseur");
		}
		var fiche = gestion.lire(slug, moi.id(), Instant.now());
		if (fiche.isEmpty()) {
			return introuvable();
		}
		var lignes = gestion.publications(fiche.get().id(), type, sansReponseAcceptee, taille + 1, apres == null ? null : apres.get());
		boolean suite = lignes.size() > taille;
		var page = lignes.subList(0, Math.min(taille, lignes.size()));
		Map<UUID, Compte> comptes = annuaire.lire(page.stream().map(GestionCommunautes.Resume::auteurId).filter(id -> id != null).toList());
		String suivant = suite ? new GestionCommunautes.Curseur(page.getLast().creeLe().toString(), page.getLast().id().toString()).encoder() : null;
		return ResponseEntity.ok(new Page<>(page.stream().map(r -> VuesPublications.resume(r, comptes)).toList(), suivant));
	}

	@PostMapping("/api/communautes/{slug}/publications")
	ResponseEntity<?> publier(@PathVariable String slug, @RequestBody NouvellePublication demande,
			@AuthenticationPrincipal UtilisateurConnecte moi) {
		String titre = demande.titre() == null ? "" : demande.titre().strip();
		String contenu = demande.contenu() == null ? "" : demande.contenu().strip();
		List<Map<String, String>> erreurs = new ArrayList<>();
		if (demande.type() == null || !TYPES.contains(demande.type())) {
			erreurs.add(Map.of("champ", "type", "code", "valeur_invalide"));
		}
		if (titre.length() < 10 || titre.length() > 150) {
			erreurs.add(Map.of("champ", "titre", "code", "longueur_invalide"));
		}
		if (contenu.length() < 20 || contenu.length() > 10000) {
			erreurs.add(Map.of("champ", "contenu", "code", "longueur_invalide"));
		}
		boolean projet = "montre_ton_projet".equals(demande.type());
		if (projet != (demande.projetId() != null)) {
			erreurs.add(Map.of("champ", "projetId", "code", projet ? "obligatoire" : "interdit"));
		}
		if (!erreurs.isEmpty()) {
			return probleme(HttpStatus.BAD_REQUEST, "validation", "Certains champs sont invalides", erreurs);
		}
		var fiche = gestion.lire(slug, moi.id(), Instant.now());
		if (fiche.isEmpty()) {
			return introuvable();
		}
		if (!fiche.get().membre()) {
			return probleme(HttpStatus.FORBIDDEN, "pas_membre", "Rejoignez la communauté pour publier", null);
		}
		// On ne montre que son propre projet : il faut faire partie de son équipe.
		if (projet && projets.acces(demande.projetId(), moi.id()) != AccesAuProjet.MEMBRE) {
			return probleme(HttpStatus.BAD_REQUEST, "validation", "Certains champs sont invalides",
					List.of(Map.of("champ", "projetId", "code", "pas_dans_l_equipe")));
		}
		Instant maintenant = Instant.now();
		long attente = gestion.attenteAvantPublication(moi.id(), maintenant);
		if (attente > 0) {
			return Problemes.tropDeRequetes(attente, "Trop de publications en peu de temps");
		}
		boolean question = "question".equals(demande.type());
		UUID id = gestion.publier(fiche.get().id(), moi.id(), demande.type(), titre, contenu, demande.projetId(),
				question ? ia.statutInitial() : null, maintenant);
		if (question) {
			// Après l'enregistrement : la question est visible tout de suite, la réponse de l'IA suit (contrat).
			ia.demander(id, new ReponsesIa.Question(titre, contenu, fiche.get().theme()));
		}
		return ResponseEntity.created(URI.create("/api/publications/" + id)).body(vues.publication(id, moi.id()));
	}

	@GetMapping("/api/publications/{publicationId}")
	ResponseEntity<?> lirePublication(@PathVariable UUID publicationId, @AuthenticationPrincipal UtilisateurConnecte moi) {
		if (gestion.lirePublication(publicationId).isEmpty()) {
			return introuvable();
		}
		return ResponseEntity.ok(vues.publication(publicationId, moi.id()));
	}

	private static Communaute communaute(GestionCommunautes.Fiche f) {
		return new Communaute(f.id(), f.slug(), f.nom(), f.description(), f.theme(), f.pays(), f.nombreMembres(),
				f.questionsOuvertes(), f.membre());
	}

	private static boolean curseurDatee(GestionCommunautes.Curseur c) {
		try {
			Instant.parse(c.a());
			UUID.fromString(c.b());
			return true;
		}
		catch (RuntimeException e) {
			return false;
		}
	}

	/** Entre 1 et 100 (socle.yaml), sinon null. */
	private static Integer taille(String limite) {
		try {
			int n = Integer.parseInt(limite);
			return n >= 1 && n <= 100 ? n : null;
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

	private static ResponseEntity<ProblemDetail> validation(String champ) {
		return Problemes.validation(champ, "valeur_invalide");
	}

	private static ResponseEntity<ProblemDetail> introuvable() {
		return Problemes.introuvable();
	}

	private static ResponseEntity<ProblemDetail> probleme(HttpStatus statut, String code, String titre,
			List<Map<String, String>> erreurs) {
		return Problemes.probleme(statut, code, titre, erreurs);
	}

}
