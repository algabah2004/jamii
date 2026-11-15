package dev.jamii.api.hackathons;

import dev.jamii.api.questionnaires.Questionnaire;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Lit le corps JSON d'une requête et collecte **toutes** les erreurs de champ, pour un seul 400 (contrat : liste
 * `erreurs` avec `champ` et `code`). Un élément d'une liste (un critère, un prix) a son propre lecteur, qui écrit dans
 * la même liste sous un préfixe (`criteres[0].nom`).
 */
final class Corps {

	private final Map<String, Object> corps;

	private final List<Map<String, String>> liste;

	private final String prefixe;

	Corps(Map<String, Object> corps, Set<String> permis) {
		this(corps, permis, "", new ArrayList<>());
	}

	private Corps(Map<String, Object> corps, Set<String> permis, String prefixe, List<Map<String, String>> liste) {
		this.corps = corps;
		this.prefixe = prefixe;
		this.liste = liste;
		corps.keySet().stream().filter(c -> !permis.contains(c)).sorted().forEach(c -> ajouter(c, "champ_inconnu"));
	}

	/** Le lecteur d'un élément de liste : ses erreurs vont dans la même réponse, sous `prefixeElement`. */
	Corps sous(Map<String, Object> element, Set<String> permis, String prefixeElement) {
		return new Corps(element, permis, prefixeElement, liste);
	}

	boolean contient(String champ) {
		return corps.containsKey(champ);
	}

	void ajouter(String champ, String code) {
		liste.add(Map.of("champ", prefixe + champ, "code", code));
	}

	void verifier() {
		if (!liste.isEmpty()) {
			throw ErreurHackathons.validation(liste);
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

	boolean booleen(String champ, boolean parDefaut) {
		Object v = corps.get(champ);
		if (v == null) {
			return parDefaut;
		}
		if (!(v instanceof Boolean b)) {
			ajouter(champ, "type_invalide");
			return parDefaut;
		}
		return b;
	}

	Instant instant(String champ, boolean obligatoire) {
		Object v = corps.get(champ);
		if (v == null) {
			if (obligatoire) {
				ajouter(champ, "obligatoire");
			}
			return null;
		}
		try {
			return Instant.parse((String) v);
		}
		catch (DateTimeParseException | ClassCastException e) {
			ajouter(champ, "format_invalide");
			return null;
		}
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
		catch (RuntimeException e) {
			ajouter(champ, "format_invalide");
			return null;
		}
	}

	/** Une liste d'objets (critères, prix), de `min` à `max` éléments ; chacun est rendu avec son lecteur. */
	List<Map<String, Object>> objets(String champ, int min, int max, boolean obligatoire) {
		Object v = corps.get(champ);
		if (v == null) {
			if (obligatoire) {
				ajouter(champ, "obligatoire");
			}
			return null;
		}
		if (!(v instanceof List<?> elements)) {
			ajouter(champ, "type_invalide");
			return null;
		}
		if (elements.size() < min) {
			ajouter(champ, "obligatoire");
		}
		else if (elements.size() > max) {
			ajouter(champ, "trop_nombreux");
		}
		List<Map<String, Object>> objets = new ArrayList<>();
		for (int i = 0; i < elements.size(); i++) {
			if (elements.get(i) instanceof Map<?, ?> m) {
				@SuppressWarnings("unchecked")
				Map<String, Object> element = (Map<String, Object>) m;
				objets.add(element);
			}
			else {
				ajouter(champ + "[" + i + "]", "type_invalide");
			}
		}
		return objets;
	}

	/** Un questionnaire personnalisé : `Questionnaire` vérifie tout, ses erreurs vont dans la même réponse. */
	List<Questionnaire.Question> questionnaire(String champ) {
		return Questionnaire.lire(corps.get(champ), prefixe + champ, (c, code) -> liste.add(Map.of("champ", c, "code", code)));
	}

	/** Une liste de textes (les questions posées à chaque rendu). */
	List<String> textes(String champ, int max, int longueurMax) {
		Object v = corps.get(champ);
		if (v == null) {
			return null;
		}
		if (!(v instanceof List<?> elements)) {
			ajouter(champ, "type_invalide");
			return null;
		}
		if (elements.size() > max) {
			ajouter(champ, "trop_nombreux");
		}
		List<String> textes = new ArrayList<>();
		for (Object o : elements) {
			if (o instanceof String s && s.strip().length() >= 3 && s.strip().length() <= longueurMax) {
				textes.add(s.strip());
			}
			else {
				ajouter(champ, "format_invalide");
			}
		}
		return textes;
	}

	/** Un sous-objet (le calendrier, la taille d'équipe). */
	Map<String, Object> objet(String champ, boolean obligatoire) {
		Object v = corps.get(champ);
		if (v == null) {
			if (obligatoire) {
				ajouter(champ, "obligatoire");
			}
			return null;
		}
		if (!(v instanceof Map<?, ?> m)) {
			ajouter(champ, "type_invalide");
			return null;
		}
		@SuppressWarnings("unchecked")
		Map<String, Object> objet = (Map<String, Object>) m;
		return objet;
	}

}
