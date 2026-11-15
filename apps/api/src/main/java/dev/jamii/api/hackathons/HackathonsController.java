package dev.jamii.api.hackathons;

import dev.jamii.api.questionnaires.Questionnaire;
import java.net.URI;
import java.time.Instant;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.jamii.api.auth.Annuaire;
import dev.jamii.api.auth.Compte;
import dev.jamii.api.auth.UtilisateurConnecte;

/**
 * Créer, modifier, publier, annuler et lire un hackathon (contracts/hackathons.yaml, tag « hackathons »). Les lectures
 * sont publiques (`security: []`) : sans connexion, on ne voit que les hackathons publiés.
 */
@RestController
@RequestMapping("/api/hackathons")
class HackathonsController {

	record PersonneResume(UUID id, String nom, String avatarUrl) {
	}

	record TailleEquipe(int min, int max) {
	}

	record Moi(String role, UUID equipeId, String inscription, List<String> actions) {
	}

	record HackathonResume(UUID id, String titre, String theme, String lieu, String ville, String statut, Instant debutLe,
			Instant finRendusLe, PersonneResume organisateur, int nombreInscrits) {
	}

	record HackathonVue(UUID id, String titre, String theme, String lieu, String ville, String statut, Instant debutLe,
			Instant finRendusLe, PersonneResume organisateur, int nombreInscrits, String description,
			Hackathons.Calendrier calendrier, TailleEquipe tailleEquipe, String modeInscription, boolean deploiementObligatoire,
			String codeDeConduite, List<String> questions, List<Questionnaire.Question> questionnaire, List<Hackathons.Critere> criteres, List<Hackathons.Prix> prix,
			int version, Instant creeLe, Moi moi) {
	}

	record PageHackathons(List<HackathonResume> elements, String curseurSuivant) {
	}

	private static final Set<String> STATUTS = Set.of("brouillon", "publie", "en_cours", "evaluation", "resultats", "annule");

	private static final Set<String> CALENDRIER = Set.of("inscriptionsOuvertesLe", "inscriptionsFermeesLe", "debutLe",
			"finRendusLe", "resultatsLe");

	private final Hackathons hackathons;

	private final Annuaire annuaire;

	HackathonsController(Hackathons hackathons, Annuaire annuaire) {
		this.hackathons = hackathons;
		this.annuaire = annuaire;
	}

	@GetMapping
	PageHackathons lister(@AuthenticationPrincipal UtilisateurConnecte moi, @RequestParam(required = false) String vue,
			@RequestParam(required = false) String statut, @RequestParam(defaultValue = "20") int limite,
			@RequestParam(required = false) String curseur) {
		boolean miens = "miens".equals(vue);
		if (vue != null && !miens) {
			throw ErreurHackathons.validation("vue", "valeur_inconnue");
		}
		if (miens && moi == null) {
			throw ErreurHackathons.nonConnecte();
		}
		if (statut != null && !STATUTS.contains(statut)) {
			throw ErreurHackathons.validation("statut", "valeur_inconnue");
		}
		if (limite < 1 || limite > 100) {
			throw ErreurHackathons.validation("limite", "hors_limites");
		}
		Hackathons.Page page = miens ? hackathons.listerMiens(moi.id(), limite, curseur) : hackathons.lister(statut, limite, curseur);
		Map<UUID, Compte> organisateurs = annuaire.lire(page.elements().stream().map(Hackathons.Hackathon::organisateurId).distinct().toList());
		Instant maintenant = hackathons.maintenant();
		Map<UUID, Integer> nombres = hackathons.nombresInscrits(page.elements().stream().map(Hackathons.Hackathon::id).toList());
		return new PageHackathons(page.elements().stream()
			.map(h -> resume(h, organisateurs.get(h.organisateurId()), maintenant, nombres.getOrDefault(h.id(), 0))).toList(),
			page.curseurSuivant());
	}

	@PostMapping
	ResponseEntity<HackathonVue> creer(@AuthenticationPrincipal UtilisateurConnecte moi, @RequestBody Map<String, Object> corps) {
		UUID id = hackathons.creer(moi.id(), lireNouveau(corps));
		return ResponseEntity.created(URI.create("/api/hackathons/" + id)).body(vue(hackathons.lire(id, moi.id()), moi.id()));
	}

	@GetMapping("/{id}")
	HackathonVue lire(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable UUID id) {
		UUID qui = moi == null ? null : moi.id();
		return vue(hackathons.lire(id, qui), qui);
	}

	/** Fusion JSON (RFC 7396) : seuls les champs présents changent ; `version` est obligatoire. */
	@PatchMapping(path = "/{id}", consumes = { "application/merge-patch+json", "application/json" })
	HackathonVue modifier(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable UUID id, @RequestBody Map<String, Object> corps) {
		hackathons.modifier(id, moi.id(), lireModification(corps));
		return vue(hackathons.lire(id, moi.id()), moi.id());
	}

	@PostMapping("/{id}/publier")
	HackathonVue publier(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable UUID id, @RequestBody Map<String, Object> corps) {
		hackathons.publier(id, moi.id(), version(corps));
		return vue(hackathons.lire(id, moi.id()), moi.id());
	}

	@PostMapping("/{id}/annuler")
	HackathonVue annuler(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable UUID id, @RequestBody Map<String, Object> corps) {
		Corps c = new Corps(corps, Set.of("version", "raison"));
		int version = c.entier("version", 0, Integer.MAX_VALUE, true);
		String raison = c.texte("raison", 10, 1000, true);
		c.verifier();
		hackathons.annuler(id, moi.id(), version, raison);
		return vue(hackathons.lire(id, moi.id()), moi.id());
	}

	// ---------------------------------------------------------------- vues

	private HackathonVue vue(Hackathons.Lecture l, UUID qui) {
		Hackathons.Hackathon h = l.hackathon();
		Instant maintenant = hackathons.maintenant();
		String statut = h.statut(maintenant);
		Compte organisateur = annuaire.lire(h.organisateurId()).orElse(null);
		return new HackathonVue(h.id(), h.titre(), h.theme(), h.lieu(), h.ville(), statut, h.calendrier().debutLe(),
				h.calendrier().finRendusLe(), personne(h.organisateurId(), organisateur),
				hackathons.nombresInscrits(List.of(h.id())).getOrDefault(h.id(), 0), h.description(), h.calendrier(),
				new TailleEquipe(h.tailleMin(), h.tailleMax()), h.modeInscription(), h.deploiementObligatoire(), h.codeDeConduite(),
				h.questions(), h.questionnaire(), h.criteres(), h.prix(), h.version(), h.creeLe(),
				new Moi(l.role(), l.equipeId(), l.inscription(), actions(l, statut, qui, h, maintenant)));
	}

	private static HackathonResume resume(Hackathons.Hackathon h, Compte organisateur, Instant maintenant, int inscrits) {
		return new HackathonResume(h.id(), h.titre(), h.theme(), h.lieu(), h.ville(), h.statut(maintenant),
				h.calendrier().debutLe(), h.calendrier().finRendusLe(), personne(h.organisateurId(), organisateur), inscrits);
	}

	private static PersonneResume personne(UUID id, Compte compte) {
		return new PersonneResume(id, compte == null ? "Compte supprimé" : compte.nom(), compte == null ? null : compte.avatarUrl());
	}

	/** Ce que la personne peut faire ; le front en tire ses boutons, et chaque route revérifie. */
	private static List<String> actions(Hackathons.Lecture l, String statut, UUID qui, Hackathons.Hackathon h, Instant maintenant) {
		if ("organisateur".equals(l.role())) {
			List<String> actions = new ArrayList<>(switch (statut) {
				case "brouillon" -> List.of("modifier", "publier");
				case "publie" -> List.of("modifier", "annuler");
				case "en_cours" -> List.of("annuler");
				case "evaluation" -> List.of("confirmer_resultats");
				default -> List.of();
			});
			if ("candidature".equals(h.modeInscription()) && ("publie".equals(statut) || "en_cours".equals(statut))) {
				actions.add("trier_inscriptions");
			}
			if (!"brouillon".equals(statut) && !"annule".equals(statut) && !"resultats".equals(statut)) {
				actions.add("inviter_jure");
			}
			return actions;
		}
		if ("jure".equals(l.role()) && "evaluation".equals(statut)) {
			return List.of("noter");
		}
		// Un juré inscrit dans une équipe rend aussi son projet.
		if ("en_cours".equals(statut) && l.equipeId() != null) {
			return List.of("rendre");
		}
		boolean inscriptionsOuvertes = "publie".equals(statut) && h.calendrier().inscriptionsOuvertesLe().isBefore(maintenant)
				&& h.calendrier().inscriptionsFermeesLe().isAfter(maintenant);
		if (qui == null || !inscriptionsOuvertes) {
			return List.of();
		}
		if ("participant".equals(l.role()) || l.equipeId() != null) {
			return l.equipeId() == null ? List.of("creer_equipe") : List.of("inviter");
		}
		return List.of("sinscrire");
	}

	// ---------------------------------------------------------------- lecture du corps

	private static int version(Map<String, Object> corps) {
		Corps c = new Corps(corps, Set.of("version"));
		int version = c.entier("version", 0, Integer.MAX_VALUE, true);
		c.verifier();
		return version;
	}

	private static Hackathons.Nouveau lireNouveau(Map<String, Object> corps) {
		Corps c = new Corps(corps, Set.of("titre", "theme", "description", "lieu", "ville", "calendrier", "tailleEquipe", "modeInscription",
				"deploiementObligatoire", "codeDeConduite", "questions", "questionnaire", "criteres", "prix"));
		String titre = c.texte("titre", 3, 120, true);
		String theme = c.texte("theme", 2, 120, true);
		String description = c.texte("description", 20, 8000, true);
		String lieu = c.choix("lieu", List.of("en_ligne", "sur_place", "hybride"), true);
		String ville = c.texte("ville", 1, 80, false);
		Hackathons.Calendrier calendrier = calendrier(c, true);
		Map<String, Object> taille = c.objet("tailleEquipe", true);
		int min = 0;
		int max = 0;
		if (taille != null) {
			Corps t = c.sous(taille, Set.of("min", "max"), "tailleEquipe.");
			min = t.entier("min", 1, 10, true);
			max = t.entier("max", 1, 10, true);
			if (min > max) {
				c.ajouter("tailleEquipe", "min_superieur_au_max");
			}
		}
		String mode = c.choix("modeInscription", List.of("ouvert", "candidature"), true);
		boolean deploiement = c.booleen("deploiementObligatoire", false);
		String code = c.texte("codeDeConduite", 20, 4000, false);
		List<String> questions = c.textes("questions", 5, 200);
		List<Questionnaire.Question> questionnaire = c.questionnaire("questionnaire");
		List<Hackathons.Critere> criteres = criteres(c, true);
		List<Hackathons.Prix> prix = prix(c);
		c.verifier();
		return new Hackathons.Nouveau(titre, theme, description, lieu, ville, calendrier, min, max, mode, deploiement, code,
				questions == null ? List.of() : questions, questionnaire == null ? List.of() : questionnaire, criteres,
				prix == null ? List.of() : prix);
	}

	private static Hackathons.Modification lireModification(Map<String, Object> corps) {
		Corps c = new Corps(corps, Set.of("version", "titre", "theme", "description", "lieu", "ville", "calendrier",
				"deploiementObligatoire", "codeDeConduite", "questions", "questionnaire", "criteres", "prix"));
		int version = c.entier("version", 0, Integer.MAX_VALUE, true);
		Set<String> presents = new HashSet<>(corps.keySet());
		presents.remove("version");
		String titre = c.texte("titre", 3, 120, false);
		String theme = c.texte("theme", 2, 120, false);
		String description = c.texte("description", 20, 8000, false);
		String lieu = c.choix("lieu", List.of("en_ligne", "sur_place", "hybride"), false);
		String ville = c.texte("ville", 1, 80, false);
		Hackathons.Calendrier calendrier = calendrier(c, false);
		Boolean deploiement = corps.containsKey("deploiementObligatoire") ? c.booleen("deploiementObligatoire", false) : null;
		String code = c.texte("codeDeConduite", 20, 4000, false);
		List<String> questions = c.textes("questions", 5, 200);
		List<Questionnaire.Question> questionnaire = c.questionnaire("questionnaire");
		List<Hackathons.Critere> criteres = criteres(c, false);
		List<Hackathons.Prix> prix = prix(c);
		// Un champ présent mais vide n'est pas « absent » : `titre`, `theme`, `description` et `lieu` ne se vident pas.
		for (String obligatoire : List.of("titre", "theme", "description", "lieu", "deploiementObligatoire", "questions", "questionnaire", "criteres", "prix")) {
			if (corps.containsKey(obligatoire) && corps.get(obligatoire) == null) {
				c.ajouter(obligatoire, "ne_peut_pas_etre_vide");
			}
		}
		c.verifier();
		return new Hackathons.Modification(version, presents, titre, theme, description, lieu, ville, calendrier, deploiement, code,
				questions == null ? List.of() : questions, questionnaire == null ? List.of() : questionnaire, criteres,
				prix == null ? List.of() : prix);
	}

	private static Hackathons.Calendrier calendrier(Corps c, boolean obligatoire) {
		Map<String, Object> objet = c.objet("calendrier", obligatoire);
		if (objet == null) {
			return null;
		}
		Corps k = c.sous(objet, CALENDRIER, "calendrier.");
		Instant ouvertes = k.instant("inscriptionsOuvertesLe", true);
		Instant fermees = k.instant("inscriptionsFermeesLe", true);
		Instant debut = k.instant("debutLe", true);
		Instant fin = k.instant("finRendusLe", true);
		Instant resultats = k.instant("resultatsLe", true);
		if (ouvertes == null || fermees == null || debut == null || fin == null || resultats == null) {
			return null;
		}
		Hackathons.Calendrier calendrier = new Hackathons.Calendrier(ouvertes, fermees, debut, fin, resultats);
		if (!calendrier.ordonne()) {
			c.ajouter("calendrier", "dates_dans_le_desordre");
			return null;
		}
		return calendrier;
	}

	private static List<Hackathons.Critere> criteres(Corps c, boolean obligatoire) {
		List<Map<String, Object>> objets = c.objets("criteres", 1, 8, obligatoire);
		if (objets == null) {
			return null;
		}
		List<Hackathons.Critere> criteres = new ArrayList<>();
		for (int i = 0; i < objets.size(); i++) {
			Corps k = c.sous(objets.get(i), Set.of("nom", "poids"), "criteres[" + i + "].");
			String nom = k.texte("nom", 2, 60, true);
			int poids = k.entier("poids", 1, 100, true);
			criteres.add(new Hackathons.Critere(null, nom, poids));
		}
		return criteres;
	}

	private static List<Hackathons.Prix> prix(Corps c) {
		List<Map<String, Object>> objets = c.objets("prix", 0, 10, false);
		if (objets == null) {
			return null;
		}
		List<Hackathons.Prix> prix = new ArrayList<>();
		for (int i = 0; i < objets.size(); i++) {
			Corps k = c.sous(objets.get(i), Set.of("nom", "montant", "sponsor"), "prix[" + i + "].");
			String nom = k.texte("nom", 2, 80, true);
			String sponsor = k.texte("sponsor", 1, 80, false);
			Hackathons.Montant montant = null;
			Map<String, Object> brut = k.objet("montant", false);
			if (brut != null) {
				Corps m = k.sous(brut, Set.of("centimes", "devise"), "prix[" + i + "].montant.");
				Object centimes = brut.get("centimes");
				String devise = m.texte("devise", 3, 3, true);
				if (!(centimes instanceof Integer || centimes instanceof Long) || ((Number) centimes).longValue() < 0) {
					m.ajouter("centimes", "montant_invalide");
				}
				else if (devise != null && devise.matches("^[A-Z]{3}$")) {
					montant = new Hackathons.Montant(((Number) centimes).longValue(), devise);
				}
				else if (devise != null) {
					m.ajouter("devise", "devise_invalide");
				}
			}
			prix.add(new Hackathons.Prix(null, nom, montant, sponsor));
		}
		return prix;
	}

}
