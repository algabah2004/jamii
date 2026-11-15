package dev.jamii.api.missions;

import dev.jamii.api.questionnaires.Questionnaire;
import java.net.URI;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.jamii.api.auth.Compte;
import dev.jamii.api.auth.Role;
import dev.jamii.api.auth.UtilisateurConnecte;

/**
 * Publier une mission et la lire (contracts/missions.yaml, tag « missions »). Les lectures sont publiques
 * (`security: []` dans le contrat) : sans connexion, on ne voit que les annonces publiques ouvertes.
 */
@RestController
@RequestMapping("/api/missions")
class MissionsController {

	record PageMissions(List<VuesMissions.MissionResume> elements, String curseurSuivant) {
	}

	private static final Pattern COMPETENCE = Pattern.compile("^[a-z0-9][a-z0-9.+#-]*$");

	private static final Set<String> CHAMPS = Set.of("demandeId", "titre", "besoin", "competences", "budget", "dateSouhaitee",
			"visibilite", "ville", "pays", "aDistance", "questionnaire");

	private final Missions missions;

	private final VuesMissions vues;

	MissionsController(Missions missions, VuesMissions vues) {
		this.missions = missions;
		this.vues = vues;
	}

	@GetMapping
	PageMissions lister(@AuthenticationPrincipal UtilisateurConnecte moi, @RequestParam(defaultValue = "publiques") String vue,
			@RequestParam(required = false) String competence, @RequestParam(required = false) String pays,
			@RequestParam(defaultValue = "20") int limite, @RequestParam(required = false) String curseur) {
		boolean miennes = "miennes".equals(vue);
		if (!miennes && !"publiques".equals(vue)) {
			throw ErreurMissions.validation(List.of(Map.of("champ", "vue", "code", "valeur_inconnue")));
		}
		if (miennes && moi == null) {
			throw ErreurMissions.nonConnecte();
		}
		if (limite < 1 || limite > 100) {
			throw ErreurMissions.validation(List.of(Map.of("champ", "limite", "code", "hors_limites")));
		}
		Missions.Page page = missions.lister(moi == null ? null : moi.id(), miennes, competence, pays, limite, curseur);
		Map<UUID, Compte> clients = vues.comptes(page.elements().stream().map(Missions.Mission::clientId).distinct().toList());
		Map<UUID, Integer> nombres = missions.nombresDePropositions(page.elements().stream().map(Missions.Mission::id).toList());
		return new PageMissions(page.elements().stream()
			.map(m -> vues.resume(m, clients.get(m.clientId()), nombres.getOrDefault(m.id(), 0), miennes && vues.aFaire(m.id(), moi.id())))
			.toList(), page.curseurSuivant());
	}

	@PostMapping
	ResponseEntity<VuesMissions.MissionVue> creer(@AuthenticationPrincipal UtilisateurConnecte moi, @RequestBody Map<String, Object> corps) {
		UUID id = missions.creer(moi.id(), lire(corps));
		return ResponseEntity.created(URI.create("/api/missions/" + id)).body(vues.vue(missions.lire(id, moi.id()), moi.id()));
	}

	@GetMapping("/{id}")
	VuesMissions.MissionVue lire(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable UUID id) {
		UUID qui = moi == null ? null : moi.id();
		boolean moderateur = moi != null && moi.roles().contains(Role.MODERATEUR);
		return vues.vue(missions.lire(id, qui, moderateur), qui);
	}

	@PostMapping("/{id}/publier")
	VuesMissions.MissionVue publier(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable UUID id, @RequestBody Map<String, Object> corps) {
		missions.publier(id, moi.id(), version(corps));
		return vues.vue(missions.lire(id, moi.id()), moi.id());
	}

	@PostMapping("/{id}/fermer")
	VuesMissions.MissionVue fermer(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable UUID id, @RequestBody Map<String, Object> corps) {
		missions.fermer(id, moi.id(), version(corps));
		return vues.vue(missions.lire(id, moi.id()), moi.id());
	}

	@PostMapping("/{id}/invitations")
	ResponseEntity<Void> inviter(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable UUID id, @RequestBody Map<String, Object> corps) {
		Erreurs erreurs = new Erreurs(corps, Set.of("utilisateurId"));
		UUID invite = erreurs.identifiant("utilisateurId", true);
		erreurs.verifier();
		missions.inviter(id, moi.id(), invite);
		return ResponseEntity.ok().build();
	}

	// ---------------------------------------------------------------- lecture du corps

	/** Le corps `{ "version": n }` des actions sur une mission. */
	static int version(Map<String, Object> corps) {
		Erreurs erreurs = new Erreurs(corps, Set.of("version"));
		int version = erreurs.entier("version", 0, Integer.MAX_VALUE, true);
		erreurs.verifier();
		return version;
	}

	private static Missions.Nouvelle lire(Map<String, Object> corps) {
		Erreurs e = new Erreurs(corps, CHAMPS);
		UUID demandeId = e.identifiant("demandeId", false);
		String titre = e.texte("titre", 3, 120, true);
		String besoin = e.texte("besoin", 20, 8000, true);
		List<String> competences = e.competences();
		Missions.Montant budget = e.montant("budget", false);
		LocalDate date = e.date("dateSouhaitee");
		String visibilite = e.choix("visibilite", List.of("publique", "invitation"), true);
		String ville = e.texte("ville", 1, 80, false);
		String pays = e.motif("pays", "^[A-Z]{2}$");
		boolean aDistance = !(corps.get("aDistance") instanceof Boolean b) || b;
		if (corps.containsKey("aDistance") && !(corps.get("aDistance") instanceof Boolean)) {
			e.ajouter("aDistance", "type_invalide");
		}
		List<Questionnaire.Question> questionnaire = Questionnaire.lire(corps.get("questionnaire"), "questionnaire", e::ajouter);
		e.verifier();
		return new Missions.Nouvelle(demandeId, titre, besoin, competences, budget, date, visibilite, ville, pays, aDistance,
				questionnaire == null ? List.of() : questionnaire);
	}

	/**
	 * Collecte toutes les erreurs de champ, pour un seul 400 (contrat : liste `erreurs` avec `champ` et `code`).
	 * Un élément d'une liste (un jalon) a son propre validateur, qui écrit dans la même liste avec un préfixe
	 * (`jalons[0].titre`).
	 */
	static final class Erreurs {

		private final Map<String, Object> corps;

		private final List<Map<String, String>> liste;

		private final String prefixe;

		Erreurs(Map<String, Object> corps, Set<String> permis) {
			this(corps, permis, "", new ArrayList<>());
		}

		private Erreurs(Map<String, Object> corps, Set<String> permis, String prefixe, List<Map<String, String>> liste) {
			this.corps = corps;
			this.prefixe = prefixe;
			this.liste = liste;
			corps.keySet().stream().filter(c -> !permis.contains(c)).sorted().forEach(c -> ajouter(c, "champ_inconnu"));
		}

		/** Le validateur d'un élément de liste : ses erreurs vont dans la même réponse, sous `prefixe`. */
		Erreurs sous(Map<String, Object> element, Set<String> permis, String prefixeElement) {
			return new Erreurs(element, permis, prefixeElement, liste);
		}

		void ajouter(String champ, String code) {
			liste.add(Map.of("champ", prefixe + champ, "code", code));
		}

		void verifier() {
			if (!liste.isEmpty()) {
				throw ErreurMissions.validation(liste);
			}
		}

		String texte(String champ, int min, int max, boolean obligatoire) {
			Object v = corps.get(champ);
			if (v == null) {
				if (obligatoire) {
					ajouter(champ, "obligatoire");
				}
				return null;
			}
			if (!(v instanceof String s)) {
				ajouter(champ, "type_invalide");
				return null;
			}
			String propre = s.strip();
			if (propre.length() < min) {
				ajouter(champ, "trop_court");
			}
			else if (propre.length() > max) {
				ajouter(champ, "trop_long");
			}
			return propre;
		}

		String choix(String champ, List<String> permises, boolean obligatoire) {
			Object v = corps.get(champ);
			if (v == null) {
				if (obligatoire) {
					ajouter(champ, "obligatoire");
				}
				return null;
			}
			if (!(v instanceof String s) || !permises.contains(s)) {
				ajouter(champ, "valeur_inconnue");
				return null;
			}
			return s;
		}

		String motif(String champ, String regex) {
			Object v = corps.get(champ);
			if (v == null) {
				return null;
			}
			if (!(v instanceof String s) || !s.matches(regex)) {
				ajouter(champ, "format_invalide");
				return null;
			}
			return s;
		}

		UUID identifiant(String champ, boolean obligatoire) {
			Object v = corps.get(champ);
			if (v == null) {
				if (obligatoire) {
					ajouter(champ, "obligatoire");
				}
				return null;
			}
			try {
				return UUID.fromString((String) v);
			}
			catch (RuntimeException ex) {
				ajouter(champ, "format_invalide");
				return null;
			}
		}

		int entier(String champ, int min, int max, boolean obligatoire) {
			Object v = corps.get(champ);
			if (v == null && obligatoire) {
				ajouter(champ, "obligatoire");
			}
			else if (v != null && !(v instanceof Integer || v instanceof Long)) {
				ajouter(champ, "type_invalide");
			}
			else if (v instanceof Number n && (n.longValue() < min || n.longValue() > max)) {
				ajouter(champ, "hors_limites");
			}
			else if (v instanceof Number n) {
				return n.intValue();
			}
			return 0;
		}

		LocalDate date(String champ) {
			Object v = corps.get(champ);
			if (v == null) {
				return null;
			}
			try {
				return LocalDate.parse((String) v);
			}
			catch (DateTimeParseException | ClassCastException ex) {
				ajouter(champ, "format_invalide");
				return null;
			}
		}

		List<String> competences() {
			Object v = corps.get("competences");
			if (!(v instanceof List<?> elements)) {
				ajouter("competences", v == null ? "obligatoire" : "type_invalide");
				return List.of();
			}
			Set<String> vues = new HashSet<>();
			List<String> propres = new ArrayList<>();
			for (Object o : elements) {
				if (o instanceof String s && s.length() <= 50 && COMPETENCE.matcher(s).matches()) {
					if (vues.add(s)) {
						propres.add(s);
					}
					else {
						ajouter("competences", "doublon");
					}
				}
				else {
					ajouter("competences", "format_invalide");
				}
			}
			if (elements.isEmpty()) {
				ajouter("competences", "obligatoire");
			}
			else if (elements.size() > 10) {
				ajouter("competences", "trop_nombreuses");
			}
			return propres;
		}

		/**
		 * Un montant `{ centimes, devise }` : un entier d'unité mineure positif ou nul, et une devise ISO 4217 en
		 * majuscules. Absent : `null` (« à discuter ») s'il est facultatif.
		 */
		Missions.Montant montant(String champ, boolean obligatoire) {
			Object v = corps.get(champ);
			if (v == null) {
				if (obligatoire) {
					ajouter(champ, "obligatoire");
				}
				return null;
			}
			if (!(v instanceof Map<?, ?> m) || !Set.of("centimes", "devise").containsAll(m.keySet())) {
				ajouter(champ, "type_invalide");
				return null;
			}
			Object centimes = m.get("centimes");
			Object devise = m.get("devise");
			if (!(centimes instanceof Integer || centimes instanceof Long) || ((Number) centimes).longValue() < 0) {
				ajouter(champ, "montant_invalide");
				return null;
			}
			if (!(devise instanceof String d) || !d.matches("^[A-Z]{3}$")) {
				ajouter(champ, "devise_invalide");
				return null;
			}
			return new Missions.Montant(((Number) centimes).longValue(), d);
		}

	}

}
