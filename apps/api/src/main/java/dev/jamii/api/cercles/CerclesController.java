package dev.jamii.api.cercles;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.jamii.api.auth.Annuaire;
import dev.jamii.api.auth.Compte;
import dev.jamii.api.auth.UtilisateurConnecte;

/** Les 7 routes de contracts/cercles.yaml. */
@RestController
class CerclesController {

	record CreationCercle(String nom, String ville, Integer taille) {
	}

	record Idee(String titre, String description) {
	}

	record PersonneResume(UUID id, String nom, String avatarUrl) {
	}

	record CercleResume(UUID id, String nom, String ville, int taille, String statut, int nombreMembres,
			PersonneResume createur, Instant creeLe) {
	}

	record MembreCercle(PersonneResume personne, Integer rang, boolean aDeposeIdee, Idee idee, Instant rejointLe) {
	}

	record Depot(String fournisseur, String url, String cloneUrl) {
	}

	record Tour(int numero, String statut, PersonneResume beneficiaire, UUID projetId, Depot depot, Instant livreLe) {
	}

	record Tirage(Instant effectueLe, UUID parId) {
	}

	record Moi(boolean estCreateur, boolean estMembre) {
	}

	record Cercle(UUID id, String nom, String ville, int taille, String statut, int nombreMembres, PersonneResume createur,
			Instant creeLe, List<MembreCercle> membres, List<Tour> tours, Integer tourEnCours, Tirage tirage, Moi moi) {
	}

	record Page(List<CercleResume> elements, String curseurSuivant) {
	}

	private final GestionCercles gestion;

	private final DepotsDesTours depots;

	private final Annuaire annuaire;

	CerclesController(GestionCercles gestion, DepotsDesTours depots, Annuaire annuaire) {
		this.gestion = gestion;
		this.depots = depots;
		this.annuaire = annuaire;
	}

	@GetMapping("/api/cercles")
	Page lister(@RequestParam(defaultValue = "ouverts") String portee, @RequestParam(defaultValue = "20") int limite,
			@RequestParam(required = false) String curseur, @AuthenticationPrincipal UtilisateurConnecte moi) {
		if (!"mes".equals(portee) && !"ouverts".equals(portee)) {
			throw validation("portee", "valeur_invalide");
		}
		if (limite < 1 || limite > 100) {
			throw validation("limite", "hors_limites");
		}
		var page = gestion.lister(moi.id(), "mes".equals(portee), limite, curseur);
		Set<UUID> ids = new HashSet<>();
		page.elements().forEach(f -> ids.add(f.createurId()));
		Map<UUID, Compte> comptes = annuaire.lire(ids);
		return new Page(page.elements().stream().map(f -> resume(f, comptes)).toList(), page.curseurSuivant());
	}

	@PostMapping("/api/cercles")
	ResponseEntity<Cercle> creer(@RequestBody CreationCercle demande, @AuthenticationPrincipal UtilisateurConnecte moi) {
		String nom = demande.nom() == null ? "" : demande.nom().strip();
		String ville = demande.ville() == null ? "" : demande.ville().strip();
		List<Map<String, String>> erreurs = new ArrayList<>();
		if (nom.length() < 3 || nom.length() > 80) {
			erreurs.add(Map.of("champ", "nom", "code", "longueur_invalide"));
		}
		if (ville.length() < 2 || ville.length() > 80) {
			erreurs.add(Map.of("champ", "ville", "code", "longueur_invalide"));
		}
		if (demande.taille() == null || demande.taille() < 4 || demande.taille() > 6) {
			erreurs.add(Map.of("champ", "taille", "code", "hors_limites"));
		}
		if (!erreurs.isEmpty()) {
			throw new ErreurCercle(HttpStatus.BAD_REQUEST, "validation", "Certains champs sont invalides", erreurs, 0);
		}
		UUID id = gestion.creer(moi.id(), nom, ville, demande.taille(), Instant.now());
		return ResponseEntity.created(URI.create("/api/cercles/" + id)).body(vue(id, moi.id()));
	}

	@GetMapping("/api/cercles/{cercleId}")
	Cercle lire(@PathVariable UUID cercleId, @AuthenticationPrincipal UtilisateurConnecte moi) {
		return vue(cercleId, moi.id());
	}

	@PostMapping("/api/cercles/{cercleId}/membres")
	Cercle rejoindre(@PathVariable UUID cercleId, @AuthenticationPrincipal UtilisateurConnecte moi) {
		gestion.rejoindre(cercleId, moi.id(), Instant.now());
		return vue(cercleId, moi.id());
	}

	@PutMapping("/api/cercles/{cercleId}/membres/moi/idee")
	MembreCercle deposerIdee(@PathVariable UUID cercleId, @RequestBody Idee idee, @AuthenticationPrincipal UtilisateurConnecte moi) {
		String titre = idee.titre() == null ? "" : idee.titre().strip();
		String description = idee.description() == null ? "" : idee.description().strip();
		List<Map<String, String>> erreurs = new ArrayList<>();
		if (titre.length() < 3 || titre.length() > 120) {
			erreurs.add(Map.of("champ", "titre", "code", "longueur_invalide"));
		}
		if (description.length() < 10 || description.length() > 5000) {
			erreurs.add(Map.of("champ", "description", "code", "longueur_invalide"));
		}
		if (!erreurs.isEmpty()) {
			throw new ErreurCercle(HttpStatus.BAD_REQUEST, "validation", "Certains champs sont invalides", erreurs, 0);
		}
		gestion.deposerIdee(cercleId, moi.id(), titre, description);
		return vue(cercleId, moi.id()).membres().stream().filter(m -> m.personne().id().equals(moi.id())).findFirst().orElseThrow();
	}

	@PostMapping("/api/cercles/{cercleId}/tirage")
	Cercle tirer(@PathVariable UUID cercleId, @AuthenticationPrincipal UtilisateurConnecte moi) {
		int ouvert = gestion.tirer(cercleId, moi.id(), Instant.now());
		depots.creer(cercleId, ouvert);
		return vue(cercleId, moi.id());
	}

	@PostMapping("/api/cercles/{cercleId}/tours/{numero}/livrer")
	Cercle livrer(@PathVariable UUID cercleId, @PathVariable int numero, @AuthenticationPrincipal UtilisateurConnecte moi) {
		int ouvert = gestion.livrer(cercleId, numero, moi.id(), Instant.now());
		depots.creer(cercleId, ouvert);
		return vue(cercleId, moi.id());
	}

	// ---------------------------------------------------------------- vues

	private Cercle vue(UUID cercleId, UUID moi) {
		var vue = gestion.lire(cercleId).orElseThrow(ErreurCercle::introuvable);
		var cercle = vue.cercle();
		Set<UUID> ids = new HashSet<>();
		ids.add(cercle.createurId());
		vue.membres().forEach(m -> ids.add(m.utilisateurId()));
		Map<UUID, Compte> comptes = annuaire.lire(ids);
		boolean estMembre = vue.membres().stream().anyMatch(m -> m.utilisateurId().equals(moi));
		List<MembreCercle> membres = vue.membres().stream()
			.filter(m -> comptes.containsKey(m.utilisateurId()))
			.map(m -> new MembreCercle(personne(comptes.get(m.utilisateurId())), m.rang(), m.ideeTitre() != null,
					// Les idées des autres ne sont visibles que des membres du cercle.
					estMembre && m.ideeTitre() != null ? new Idee(m.ideeTitre(), m.ideeDescription()) : null, m.rejointLe()))
			.toList();
		List<Tour> tours = cercle.tirageLe() == null ? List.of() : vue.membres().stream()
			.filter(m -> m.rang() != null && comptes.containsKey(m.utilisateurId()))
			.map(m -> new Tour(m.rang(), statutTour(m, cercle), personne(comptes.get(m.utilisateurId())), m.projetId(),
					m.depotUrl() == null ? null : new Depot(m.depotFournisseur(), m.depotUrl(), m.depotCloneUrl()), m.livreLe()))
			.toList();
		Tirage tirage = cercle.tirageLe() == null ? null : new Tirage(cercle.tirageLe(), cercle.createurId());
		return new Cercle(cercle.id(), cercle.nom(), cercle.ville(), cercle.taille(), cercle.statut(), cercle.nombreMembres(),
				personne(comptes.get(cercle.createurId())), cercle.creeLe(), membres, tours, cercle.tourEnCours(), tirage,
				new Moi(cercle.createurId().equals(moi), estMembre));
	}

	private static String statutTour(GestionCercles.Membre membre, GestionCercles.Fiche cercle) {
		if (membre.livreLe() != null) {
			return "livre";
		}
		return cercle.tourEnCours() != null && cercle.tourEnCours().equals(membre.rang()) ? "en_cours" : "a_venir";
	}

	private static CercleResume resume(GestionCercles.Fiche f, Map<UUID, Compte> comptes) {
		return new CercleResume(f.id(), f.nom(), f.ville(), f.taille(), f.statut(), f.nombreMembres(),
				personne(comptes.get(f.createurId())), f.creeLe());
	}

	private static PersonneResume personne(Compte compte) {
		return new PersonneResume(compte.id(), compte.nom(), compte.avatarUrl());
	}

	private static ErreurCercle validation(String champ, String code) {
		return new ErreurCercle(HttpStatus.BAD_REQUEST, "validation", "Certains champs sont invalides",
				List.of(Map.of("champ", champ, "code", code)), 0);
	}

	// ---------------------------------------------------------------- erreurs

	@ExceptionHandler(ErreurCercle.class)
	ResponseEntity<ProblemDetail> erreur(ErreurCercle e) {
		ProblemDetail probleme = ProblemDetail.forStatus(e.statut);
		probleme.setType(URI.create("urn:jamii:erreur:" + e.code));
		probleme.setTitle(e.getMessage());
		probleme.setProperty("code", e.code);
		if (e.erreurs != null) {
			probleme.setProperty("erreurs", e.erreurs);
		}
		var reponse = ResponseEntity.status(e.statut);
		if (e.attenteSecondes > 0) {
			reponse = reponse.header(HttpHeaders.RETRY_AFTER, String.valueOf(e.attenteSecondes));
		}
		return reponse.body(probleme);
	}

}
