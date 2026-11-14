package dev.jamii.api.profils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Lit et valide un JSON Merge Patch (RFC 7396) sur mon profil. Un champ absent ne change pas ; un champ à null
 * efface la valeur. D'où la lecture en Map : un record ne ferait pas la différence entre absent et null.
 */
final class ModificationProfil {

	static final Set<String> DISPONIBILITES = Set.of("disponible", "occupe", "indisponible");

	private static final Set<String> CHAMPS = Set.of("bio", "pays", "ville", "disponibilite", "heuresParSemaine",
			"competencesDeclarees", "competencesIaRetenues", "afficherResumeIa", "version", "titre", "linkedin", "siteWeb",
			"langues", "competencesPhares", "realisations");

	static final int LANGUES_MAX = 8;

	static final int PHARES_MAX = 3;

	static final int REALISATIONS_MAX = 6;

	static final int TECHNOLOGIES_MAX = 8;

	private static final Set<String> CHAMPS_REALISATION = Set.of("titre", "role", "description", "technologies", "url",
			"annee");

	private static final Set<String> CODES_LANGUES = Set.of(Locale.getISOLanguages());

	private static final Set<String> CODES_PAYS = Set.of(Locale.getISOCountries());

	private ModificationProfil() {
	}

	/** Valide tout, puis applique tout : soit toute la modification passe, soit rien ne change. */
	static void appliquer(Map<String, Object> patch, Profil profil) {
		List<ErreurProfil.Champ> erreurs = new ArrayList<>();
		List<Runnable> changements = new ArrayList<>();

		patch.keySet().stream().filter(c -> !CHAMPS.contains(c)).forEach(c -> erreurs.add(champ(c, "inconnu")));

		if (!(patch.get("version") instanceof Integer version) || version < 0) {
			erreurs.add(champ("version", "obligatoire"));
		}
		else if (version != profil.version()) {
			throw new ErreurProfil(org.springframework.http.HttpStatus.CONFLICT, "modification_concurrente");
		}

		texte(patch, "bio", 500, erreurs).ifPresent(v -> changements.add(() -> profil.bio(v.valeur())));
		texte(patch, "ville", 80, erreurs).ifPresent(v -> changements.add(() -> profil.ville(v.valeur())));
		texte(patch, "pays", 2, erreurs).ifPresent(v -> {
			if (v.valeur() != null && !CODES_PAYS.contains(v.valeur())) {
				erreurs.add(champ("pays", "format_invalide"));
			}
			else {
				changements.add(() -> profil.pays(v.valeur()));
			}
		});

		texte(patch, "titre", 80, erreurs).ifPresent(v -> changements.add(() -> profil.titre(v.valeur())));
		texte(patch, "linkedin", 300, erreurs).ifPresent(v -> {
			if (v.valeur() != null && !lienValide(v.valeur(), true)) {
				erreurs.add(champ("linkedin", "format_invalide"));
			}
			else {
				changements.add(() -> profil.linkedin(v.valeur()));
			}
		});
		texte(patch, "siteWeb", 300, erreurs).ifPresent(v -> {
			if (v.valeur() != null && !lienValide(v.valeur(), false)) {
				erreurs.add(champ("siteWeb", "format_invalide"));
			}
			else {
				changements.add(() -> profil.siteWeb(v.valeur()));
			}
		});

		if (patch.containsKey("langues")) {
			langues(patch.get("langues"), erreurs).ifPresent(l -> changements.add(() -> profil.langues(l)));
		}

		if (patch.containsKey("competencesPhares")) {
			listeDeCompetences(patch.get("competencesPhares"), "competencesPhares", PHARES_MAX, erreurs)
				.ifPresent(c -> changements.add(() -> profil.competencesPhares(List.copyOf(c))));
		}

		if (patch.containsKey("realisations")) {
			realisations(patch.get("realisations"), erreurs)
				.ifPresent(r -> changements.add(() -> profil.remplacerRealisations(r)));
		}

		if (patch.containsKey("disponibilite")) {
			if (patch.get("disponibilite") instanceof String d && DISPONIBILITES.contains(d)) {
				changements.add(() -> profil.disponibilite(d));
			}
			else {
				erreurs.add(champ("disponibilite", "format_invalide"));
			}
		}

		if (patch.containsKey("heuresParSemaine")) {
			Object h = patch.get("heuresParSemaine");
			if (h == null) {
				changements.add(() -> profil.heuresParSemaine(null));
			}
			else if (h instanceof Integer heures && heures >= 1 && heures <= 60) {
				changements.add(() -> profil.heuresParSemaine(heures));
			}
			else {
				erreurs.add(champ("heuresParSemaine", "hors_limites"));
			}
		}

		if (patch.containsKey("afficherResumeIa")) {
			if (patch.get("afficherResumeIa") instanceof Boolean afficher) {
				changements.add(() -> profil.afficherResumeIa(afficher));
			}
			else {
				erreurs.add(champ("afficherResumeIa", "format_invalide"));
			}
		}

		if (patch.containsKey("competencesDeclarees")) {
			competences(patch.get("competencesDeclarees"), erreurs)
				.ifPresent(c -> changements.add(() -> profil.remplacerCompetencesDeclarees(c)));
		}

		if (patch.containsKey("competencesIaRetenues")) {
			retenues(patch.get("competencesIaRetenues"), profil, erreurs)
				.ifPresent(c -> changements.add(() -> profil.retenirCompetencesIa(c)));
		}

		if (!erreurs.isEmpty()) {
			throw ErreurProfil.validation(erreurs);
		}
		changements.forEach(Runnable::run);
	}

	private record Valeur(String valeur) {
	}

	/** Un texte facultatif : null efface, une chaîne vide aussi, les espaces autour sont retirés. */
	private static java.util.Optional<Valeur> texte(Map<String, Object> patch, String nom, int max,
			List<ErreurProfil.Champ> erreurs) {
		if (!patch.containsKey(nom)) {
			return java.util.Optional.empty();
		}
		Object brut = patch.get(nom);
		if (brut == null) {
			return java.util.Optional.of(new Valeur(null));
		}
		if (!(brut instanceof String s)) {
			erreurs.add(champ(nom, "format_invalide"));
			return java.util.Optional.empty();
		}
		String propre = s.strip();
		if (propre.length() > max) {
			erreurs.add(champ(nom, "trop_long"));
			return java.util.Optional.empty();
		}
		return java.util.Optional.of(new Valeur(propre.isEmpty() ? null : propre));
	}

	private static java.util.Optional<Set<String>> competences(Object brut, List<ErreurProfil.Champ> erreurs) {
		return listeDeCompetences(brut, "competencesDeclarees", 20, erreurs);
	}

	private static java.util.Optional<Set<String>> listeDeCompetences(Object brut, String nom, int max,
			List<ErreurProfil.Champ> erreurs) {
		if (!(brut instanceof List<?> liste)) {
			erreurs.add(champ(nom, "format_invalide"));
			return java.util.Optional.empty();
		}
		if (liste.size() > max) {
			erreurs.add(champ(nom, "trop_d_elements"));
			return java.util.Optional.empty();
		}
		Set<String> propres = new LinkedHashSet<>();
		int avant = erreurs.size();
		for (int i = 0; i < liste.size(); i++) {
			var normalisee = liste.get(i) instanceof String s ? Competences.normaliser(s) : java.util.Optional.<String>empty();
			if (normalisee.isEmpty()) {
				erreurs.add(champ(nom + "[" + i + "]", "format_invalide"));
			}
			else {
				propres.add(normalisee.get());
			}
		}
		return erreurs.size() == avant ? java.util.Optional.of(propres) : java.util.Optional.empty();
	}

	/** Seulement des compétences de la carte proposée : on ne fait pas dire à l'IA ce qu'elle n'a pas proposé. */
	private static java.util.Optional<Set<String>> retenues(Object brut, Profil profil, List<ErreurProfil.Champ> erreurs) {
		if (!(brut instanceof List<?> liste)) {
			erreurs.add(champ("competencesIaRetenues", "format_invalide"));
			return java.util.Optional.empty();
		}
		if (liste.size() > CartesIa.COMPETENCES_MAX) {
			erreurs.add(champ("competencesIaRetenues", "trop_d_elements"));
			return java.util.Optional.empty();
		}
		Set<String> proposees = new java.util.HashSet<>();
		profil.competencesProposees().forEach(p -> proposees.add(p.competence()));
		Set<String> gardees = new LinkedHashSet<>();
		int avant = erreurs.size();
		for (int i = 0; i < liste.size(); i++) {
			if (liste.get(i) instanceof String s && proposees.contains(s)) {
				gardees.add(s);
			}
			else {
				erreurs.add(champ("competencesIaRetenues[" + i + "]", "non_proposee"));
			}
		}
		return erreurs.size() == avant ? java.util.Optional.of(gardees) : java.util.Optional.empty();
	}

	/**
	 * Un lien affichable sans risque : http(s) seulement (jamais javascript: ni data:), avec un vrai nom d'hôte.
	 * `linkedin` : l'hôte doit être linkedin.com ou l'un de ses sous-domaines.
	 */
	static boolean lienValide(String lien, boolean linkedin) {
		try {
			java.net.URI uri = new java.net.URI(lien);
			String schema = uri.getScheme();
			String hote = uri.getHost();
			if (schema == null || hote == null || uri.getUserInfo() != null || !hote.contains(".")) {
				return false;
			}
			schema = schema.toLowerCase(Locale.ROOT);
			hote = hote.toLowerCase(Locale.ROOT);
			if (linkedin) {
				return schema.equals("https") && (hote.equals("linkedin.com") || hote.endsWith(".linkedin.com"));
			}
			return schema.equals("https") || schema.equals("http");
		}
		catch (java.net.URISyntaxException e) {
			return false;
		}
	}

	private static java.util.Optional<List<String>> langues(Object brut, List<ErreurProfil.Champ> erreurs) {
		if (!(brut instanceof List<?> liste)) {
			erreurs.add(champ("langues", "format_invalide"));
			return java.util.Optional.empty();
		}
		if (liste.size() > LANGUES_MAX) {
			erreurs.add(champ("langues", "trop_d_elements"));
			return java.util.Optional.empty();
		}
		Set<String> propres = new LinkedHashSet<>();
		int avant = erreurs.size();
		for (int i = 0; i < liste.size(); i++) {
			if (liste.get(i) instanceof String code && CODES_LANGUES.contains(code)) {
				propres.add(code);
			}
			else {
				erreurs.add(champ("langues[" + i + "]", "format_invalide"));
			}
		}
		return erreurs.size() == avant ? java.util.Optional.of(List.copyOf(propres)) : java.util.Optional.empty();
	}

	/** Remplace la liste entière. Chaque réalisation est validée champ par champ, comme le profil. */
	private static java.util.Optional<List<Profil.Realisation>> realisations(Object brut,
			List<ErreurProfil.Champ> erreurs) {
		if (!(brut instanceof List<?> liste)) {
			erreurs.add(champ("realisations", "format_invalide"));
			return java.util.Optional.empty();
		}
		if (liste.size() > REALISATIONS_MAX) {
			erreurs.add(champ("realisations", "trop_d_elements"));
			return java.util.Optional.empty();
		}
		int avant = erreurs.size();
		List<Profil.Realisation> propres = new ArrayList<>();
		for (int i = 0; i < liste.size(); i++) {
			String prefixe = "realisations[" + i + "].";
			if (!(liste.get(i) instanceof Map<?, ?> brute)) {
				erreurs.add(champ("realisations[" + i + "]", "format_invalide"));
				continue;
			}
			@SuppressWarnings("unchecked")
			Map<String, Object> r = (Map<String, Object>) brute;
			r.keySet().stream().filter(c -> !CHAMPS_REALISATION.contains(c))
				.forEach(c -> erreurs.add(champ(prefixe + c, "inconnu")));
			String titre = texteDe(r, "titre", 80, prefixe, erreurs);
			if (titre == null || titre.length() < 2) {
				erreurs.add(champ(prefixe + "titre", "obligatoire"));
			}
			String role = texteDe(r, "role", 60, prefixe, erreurs);
			String description = texteDe(r, "description", 300, prefixe, erreurs);
			String url = texteDe(r, "url", 300, prefixe, erreurs);
			if (url != null && !lienValide(url, false)) {
				erreurs.add(champ(prefixe + "url", "format_invalide"));
			}
			Integer annee = null;
			Object a = r.get("annee");
			if (a instanceof Integer n && n >= 1990 && n <= java.time.Year.now().getValue() + 1) {
				annee = n;
			}
			else if (a != null) {
				erreurs.add(champ(prefixe + "annee", "hors_limites"));
			}
			String technologies = null;
			if (r.get("technologies") != null) {
				var techs = listeDeCompetences(r.get("technologies"), prefixe + "technologies", TECHNOLOGIES_MAX, erreurs);
				technologies = techs.filter(t -> !t.isEmpty()).map(t -> String.join(",", t)).orElse(null);
			}
			propres.add(new Profil.Realisation(titre, role, description, technologies, url, annee));
		}
		return erreurs.size() == avant ? java.util.Optional.of(propres) : java.util.Optional.empty();
	}

	/** Un texte facultatif d'un objet imbriqué : vide ou null donne null, les espaces autour sont retirés. */
	private static String texteDe(Map<String, Object> objet, String nom, int max, String prefixe,
			List<ErreurProfil.Champ> erreurs) {
		Object brut = objet.get(nom);
		if (brut == null) {
			return null;
		}
		if (!(brut instanceof String s)) {
			erreurs.add(champ(prefixe + nom, "format_invalide"));
			return null;
		}
		String propre = s.strip();
		if (propre.length() > max) {
			erreurs.add(champ(prefixe + nom, "trop_long"));
			return null;
		}
		return propre.isEmpty() ? null : propre;
	}

	private static ErreurProfil.Champ champ(String nom, String code) {
		return new ErreurProfil.Champ(nom, code);
	}

}
