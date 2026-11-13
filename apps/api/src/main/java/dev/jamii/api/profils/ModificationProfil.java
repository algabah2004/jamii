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
			"competencesDeclarees", "competencesIaRetenues", "afficherResumeIa", "version");

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
		if (!(brut instanceof List<?> liste)) {
			erreurs.add(champ("competencesDeclarees", "format_invalide"));
			return java.util.Optional.empty();
		}
		if (liste.size() > 20) {
			erreurs.add(champ("competencesDeclarees", "trop_d_elements"));
			return java.util.Optional.empty();
		}
		Set<String> propres = new LinkedHashSet<>();
		int avant = erreurs.size();
		for (int i = 0; i < liste.size(); i++) {
			var normalisee = liste.get(i) instanceof String s ? Competences.normaliser(s) : java.util.Optional.<String>empty();
			if (normalisee.isEmpty()) {
				erreurs.add(champ("competencesDeclarees[" + i + "]", "format_invalide"));
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

	private static ErreurProfil.Champ champ(String nom, String code) {
		return new ErreurProfil.Champ(nom, code);
	}

}
