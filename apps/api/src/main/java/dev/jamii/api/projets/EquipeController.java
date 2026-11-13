package dev.jamii.api.projets;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.jamii.api.auth.Annuaire;
import dev.jamii.api.auth.Compte;
import dev.jamii.api.auth.UtilisateurConnecte;

/** Les jalons et les tâches d'un projet, réservés à l'équipe (contracts/projets.yaml, tag « equipe »). */
@RestController
class EquipeController {

	private static final Set<String> STATUTS_TACHE = Set.of("a_faire", "en_cours", "livree");

	private static final Set<String> STATUTS_JALON = Set.of("prevu", "atteint");

	private static final Set<String> CHAMPS_JALON = Set.of("titre", "echeance", "statut", "ordre");

	record Jalon(UUID id, String titre, LocalDate echeance, String statut, int ordre) {
	}

	record Tache(UUID id, String titre, UUID jalonId, ProjetsController.PersonneResume assignee, String statut,
			LocalDate echeance, Instant livreeLe, boolean proposeeParIa, int version, Instant creeLe) {
	}

	record PageTaches(List<Tache> elements, String curseurSuivant) {
	}

	private final TachesEtJalons equipe;

	private final Annuaire annuaire;

	EquipeController(TachesEtJalons equipe, Annuaire annuaire) {
		this.equipe = equipe;
		this.annuaire = annuaire;
	}

	@GetMapping("/api/projets/{projetId}/jalons")
	List<Jalon> jalons(@PathVariable UUID projetId, @AuthenticationPrincipal UtilisateurConnecte moi) {
		return equipe.jalons(projetId, moi.id()).stream().map(EquipeController::vue).toList();
	}

	@GetMapping("/api/projets/{projetId}/taches")
	PageTaches taches(@PathVariable UUID projetId, @RequestParam(required = false) String statut,
			@RequestParam(required = false) String jalonId, @RequestParam(required = false) String assignee,
			@RequestParam(defaultValue = "20") int limite, @RequestParam(required = false) String curseur,
			@AuthenticationPrincipal UtilisateurConnecte moi) {
		if (statut != null && !STATUTS_TACHE.contains(statut)) {
			throw ErreurProjets.validation("statut", "valeur_invalide");
		}
		if (assignee != null && !"moi".equals(assignee)) {
			throw ErreurProjets.validation("assignee", "valeur_invalide");
		}
		if (limite < 1 || limite > 100) {
			throw ErreurProjets.validation("limite", "hors_limites");
		}
		UUID jalon = null;
		if (jalonId != null) {
			try {
				jalon = UUID.fromString(jalonId);
			}
			catch (IllegalArgumentException e) {
				throw ErreurProjets.validation("jalonId", "format_invalide");
			}
		}
		var page = equipe.taches(projetId, moi.id(), statut, jalon, "moi".equals(assignee), limite, curseur);
		Set<UUID> assignes = new HashSet<>();
		page.elements().forEach(t -> {
			if (t.assigneeId() != null) {
				assignes.add(t.assigneeId());
			}
		});
		Map<UUID, Compte> comptes = annuaire.lire(assignes);
		return new PageTaches(page.elements().stream().map(t -> vue(t, comptes)).toList(), page.curseurSuivant());
	}

	@PostMapping("/api/projets/{projetId}/taches")
	ResponseEntity<Tache> creerTache(@PathVariable UUID projetId, @RequestBody Map<String, Object> corps,
			@AuthenticationPrincipal UtilisateurConnecte moi) {
		var lecture = new Lecture(corps, Set.of("titre", "jalonId", "assigneeId", "echeance", "version"));
		String titre = lecture.titre(200, true);
		UUID jalonId = lecture.identifiant("jalonId");
		UUID assigneeId = lecture.identifiant("assigneeId");
		LocalDate echeance = lecture.date("echeance");
		lecture.verifier();
		var tache = equipe.creerTache(projetId, moi.id(), titre, jalonId, assigneeId, echeance, Instant.now());
		return ResponseEntity.created(URI.create("/api/projets/" + projetId + "/taches/" + tache.id())).body(vue(tache));
	}

	/** Fusion JSON (RFC 7396) : seuls les champs présents changent ; `version` est obligatoire. */
	@PatchMapping(path = "/api/projets/{projetId}/taches/{tacheId}", consumes = { "application/merge-patch+json", "application/json" })
	Tache modifierTache(@PathVariable UUID projetId, @PathVariable UUID tacheId, @RequestBody Map<String, Object> patch,
			@AuthenticationPrincipal UtilisateurConnecte moi) {
		var lecture = new Lecture(patch, Set.of("titre", "jalonId", "assigneeId", "echeance", "statut", "version"));
		String titre = lecture.titre(200, false);
		UUID jalonId = lecture.identifiant("jalonId");
		UUID assigneeId = lecture.identifiant("assigneeId");
		LocalDate echeance = lecture.date("echeance");
		String statut = lecture.choix("statut", STATUTS_TACHE);
		Integer version = lecture.entier("version", true);
		lecture.verifier();
		var modification = new TachesEtJalons.Modification(titre, patch.containsKey("jalonId"), jalonId, patch.containsKey("assigneeId"),
				assigneeId, patch.containsKey("echeance"), echeance, statut, version);
		return vue(equipe.modifier(projetId, tacheId, moi.id(), modification, Instant.now()));
	}

	@PostMapping("/api/projets/{projetId}/jalons")
	ResponseEntity<Jalon> creerJalon(@PathVariable UUID projetId, @RequestBody Map<String, Object> corps,
			@AuthenticationPrincipal UtilisateurConnecte moi) {
		var lecture = new Lecture(corps, CHAMPS_JALON);
		String titre = lecture.titre(120, true);
		LocalDate echeance = lecture.date("echeance");
		String statut = lecture.choix("statut", STATUTS_JALON);
		Integer ordre = lecture.entier("ordre", false);
		lecture.verifier();
		var jalon = vue(equipe.creerJalon(projetId, moi.id(), titre, echeance, statut, ordre, Instant.now()));
		return ResponseEntity.created(URI.create("/api/projets/" + projetId + "/jalons/" + jalon.id())).body(jalon);
	}

	/** Fusion JSON (RFC 7396) : seuls les champs présents changent ; seule l'échéance accepte `null`. */
	@PatchMapping(path = "/api/projets/{projetId}/jalons/{jalonId}", consumes = { "application/merge-patch+json", "application/json" })
	Jalon modifierJalon(@PathVariable UUID projetId, @PathVariable UUID jalonId, @RequestBody Map<String, Object> patch,
			@AuthenticationPrincipal UtilisateurConnecte moi) {
		var lecture = new Lecture(patch, CHAMPS_JALON);
		String titre = lecture.titre(120, false);
		LocalDate echeance = lecture.date("echeance");
		String statut = lecture.choix("statut", STATUTS_JALON);
		Integer ordre = lecture.entier("ordre", false);
		lecture.verifier();
		return vue(equipe.modifierJalon(projetId, jalonId, moi.id(), titre, patch.containsKey("echeance"), echeance, statut, ordre,
				Instant.now()));
	}

	/**
	 * Lit et valide un corps JSON champ par champ, en réunissant toutes les erreurs dans une seule réponse 400.
	 * Un champ absent donne null ; un champ facultatif à null aussi (l'appelant regarde `containsKey` s'il doit le vider).
	 */
	private static final class Lecture {

		private final Map<String, Object> corps;

		private final List<Map<String, String>> erreurs = new ArrayList<>();

		Lecture(Map<String, Object> corps, Set<String> connus) {
			this.corps = corps;
			corps.keySet().stream().filter(cle -> !connus.contains(cle)).sorted().forEach(cle -> erreur(cle, "inconnu"));
		}

		/** Présent, le titre ne peut pas être null ; à la création, il est obligatoire. */
		String titre(int max, boolean obligatoire) {
			if (!corps.containsKey("titre") && !obligatoire) {
				return null;
			}
			String titre = corps.get("titre") instanceof String s ? s.strip() : "";
			if (titre.length() < 3 || titre.length() > max) {
				erreur("titre", "longueur_invalide");
			}
			return titre;
		}

		UUID identifiant(String champ) {
			Object valeur = corps.get(champ);
			if (valeur == null) {
				return null;
			}
			try {
				return UUID.fromString((String) valeur);
			}
			catch (RuntimeException e) {
				erreur(champ, "format_invalide");
				return null;
			}
		}

		LocalDate date(String champ) {
			Object valeur = corps.get(champ);
			if (valeur == null) {
				return null;
			}
			try {
				return LocalDate.parse((String) valeur);
			}
			catch (RuntimeException e) {
				erreur(champ, "format_invalide");
				return null;
			}
		}

		/** Présent, le champ doit valoir l'une des valeurs (null compris : il n'accepte pas `null`). */
		String choix(String champ, Set<String> valeurs) {
			if (!corps.containsKey(champ)) {
				return null;
			}
			String valeur = corps.get(champ) instanceof String s ? s : "";
			if (!valeurs.contains(valeur)) {
				erreur(champ, "valeur_invalide");
			}
			return valeur;
		}

		/** Un entier positif ou nul ; `null` n'est pas accepté quand le champ est présent. */
		Integer entier(String champ, boolean obligatoire) {
			if (!corps.containsKey(champ)) {
				if (obligatoire) {
					erreur(champ, "obligatoire");
				}
				return null;
			}
			if (!(corps.get(champ) instanceof Integer n) || n < 0) {
				erreur(champ, "format_invalide");
				return null;
			}
			return n;
		}

		void verifier() {
			if (!erreurs.isEmpty()) {
				throw ErreurProjets.validation(erreurs);
			}
		}

		private void erreur(String champ, String code) {
			erreurs.add(Map.of("champ", champ, "code", code));
		}

	}

	private Tache vue(TachesEtJalons.Tache t) {
		return vue(t, t.assigneeId() == null ? Map.of() : annuaire.lire(Set.of(t.assigneeId())));
	}

	private static Jalon vue(TachesEtJalons.Jalon j) {
		return new Jalon(j.id(), j.titre(), j.echeance(), j.statut(), j.ordre());
	}

	private static Tache vue(TachesEtJalons.Tache t, Map<UUID, Compte> comptes) {
		Compte c = t.assigneeId() == null ? null : comptes.get(t.assigneeId());
		var personne = c == null ? null : new ProjetsController.PersonneResume(c.id(), c.nom(), c.avatarUrl());
		return new Tache(t.id(), t.titre(), t.jalonId(), personne, t.statut(), t.echeance(), t.livreeLe(), t.proposeeParIa(),
				t.version(), t.creeLe());
	}

}
