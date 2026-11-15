package dev.jamii.api.questionnaires;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Les questionnaires personnalisés (contracts/socle.yaml, `Question` et `Reponse`) : l'organisateur d'un hackathon ou le
 * client d'une mission pose ses propres questions, à l'inscription ou dans une proposition.
 *
 * <p>
 * Ici, et seulement ici : la lecture d'un questionnaire envoyé par le front, la vérification des réponses contre ce
 * questionnaire, et leur stockage en JSON. Rien n'est cru du front : un type inconnu, un choix qui n'existe pas, une
 * réponse obligatoire manquante ou trop longue donnent une erreur de champ, toutes collectées pour un seul 400.
 */
public final class Questionnaire {

	public static final int QUESTIONS_MAX = 10;

	public static final int INTITULE_MIN = 3;

	public static final int INTITULE_MAX = 200;

	public static final int AIDE_MAX = 300;

	public static final int CHOIX_MIN = 2;

	public static final int CHOIX_MAX = 8;

	public static final int CHOIX_LONGUEUR_MAX = 80;

	public static final int TEXTE_COURT_MAX = 200;

	public static final int TEXTE_LONG_MAX = 2000;

	public static final List<String> TYPES = List.of("texte_court", "texte_long", "choix_unique", "choix_multiples", "oui_non");

	private static final Pattern IDENTIFIANT = Pattern.compile("^[a-z0-9]{1,12}$");

	private static final Set<String> CHAMPS_QUESTION = Set.of("id", "intitule", "aide", "type", "obligatoire", "choix");

	private static final Set<String> CHAMPS_REPONSE = Set.of("questionId", "valeur");

	private static final JsonMapper JSON = new JsonMapper();

	/** `choix` : vide sauf pour `choix_unique` et `choix_multiples`. */
	public record Question(String id, String intitule, String aide, String type, boolean obligatoire, List<String> choix) {
	}

	/** `valeur` : un texte, ou une liste de textes pour `choix_multiples`. */
	public record Reponse(String questionId, Object valeur) {
	}

	/** Où écrire une erreur de champ : chaque module a son propre format d'erreur. */
	@FunctionalInterface
	public interface Erreurs {

		void ajouter(String champ, String code);

	}

	private Questionnaire() {
	}

	/** Le questionnaire envoyé sous `champ` ; `null` s'il est absent. Les erreurs vont dans `erreurs`. */
	public static List<Question> lire(Object brut, String champ, Erreurs erreurs) {
		if (brut == null) {
			return null;
		}
		if (!(brut instanceof List<?> elements)) {
			erreurs.ajouter(champ, "type_invalide");
			return null;
		}
		if (elements.size() > QUESTIONS_MAX) {
			erreurs.ajouter(champ, "trop_nombreux");
		}
		List<Question> questions = new ArrayList<>();
		Set<String> ids = new HashSet<>();
		for (int i = 0; i < elements.size(); i++) {
			String ici = champ + "[" + i + "]";
			if (!(elements.get(i) instanceof Map<?, ?> m)) {
				erreurs.ajouter(ici, "type_invalide");
				continue;
			}
			m.keySet().stream().map(String::valueOf).filter(c -> !CHAMPS_QUESTION.contains(c)).sorted()
				.forEach(c -> erreurs.ajouter(ici + "." + c, "champ_inconnu"));
			String id = m.get("id") instanceof String s ? s : null;
			if (id == null || !IDENTIFIANT.matcher(id).matches()) {
				erreurs.ajouter(ici + ".id", id == null ? "obligatoire" : "format_invalide");
			}
			else if (!ids.add(id)) {
				erreurs.ajouter(ici + ".id", "doublon");
			}
			String intitule = texte(m.get("intitule"), ici + ".intitule", INTITULE_MIN, INTITULE_MAX, true, erreurs);
			String aide = texte(m.get("aide"), ici + ".aide", 0, AIDE_MAX, false, erreurs);
			String type = m.get("type") instanceof String s && TYPES.contains(s) ? s : null;
			if (type == null) {
				erreurs.ajouter(ici + ".type", m.get("type") == null ? "obligatoire" : "valeur_inconnue");
			}
			boolean obligatoire = false;
			if (m.get("obligatoire") instanceof Boolean b) {
				obligatoire = b;
			}
			else if (m.get("obligatoire") != null) {
				erreurs.ajouter(ici + ".obligatoire", "type_invalide");
			}
			List<String> choix = type != null && type.startsWith("choix_") ? choix(m.get("choix"), ici + ".choix", erreurs) : List.of();
			if (id != null && intitule != null && type != null) {
				questions.add(new Question(id, intitule, aide == null || aide.isEmpty() ? null : aide, type, obligatoire, choix));
			}
		}
		return questions;
	}

	private static List<String> choix(Object brut, String champ, Erreurs erreurs) {
		if (!(brut instanceof List<?> elements)) {
			erreurs.ajouter(champ, brut == null ? "obligatoire" : "type_invalide");
			return List.of();
		}
		if (elements.size() < CHOIX_MIN) {
			erreurs.ajouter(champ, "trop_peu");
		}
		else if (elements.size() > CHOIX_MAX) {
			erreurs.ajouter(champ, "trop_nombreux");
		}
		List<String> choix = new ArrayList<>();
		for (int i = 0; i < elements.size(); i++) {
			String c = texte(elements.get(i), champ + "[" + i + "]", 1, CHOIX_LONGUEUR_MAX, true, erreurs);
			if (c != null && choix.contains(c)) {
				erreurs.ajouter(champ + "[" + i + "]", "doublon");
			}
			else if (c != null) {
				choix.add(c);
			}
		}
		return List.copyOf(choix);
	}

	/**
	 * Les réponses envoyées sous `champ`, vérifiées contre `questions` et rangées dans l'ordre du questionnaire. Une réponse
	 * vide compte comme absente. Les erreurs de valeur sont écrites sous `champ.<id de la question>`, pour que le front
	 * les place à côté de la bonne question.
	 */
	public static List<Reponse> verifier(Object brut, List<Question> questions, String champ, Erreurs erreurs) {
		Map<String, Object> valeurs = new LinkedHashMap<>();
		if (brut != null && !(brut instanceof List<?>)) {
			erreurs.ajouter(champ, "type_invalide");
		}
		else if (brut instanceof List<?> elements) {
			for (int i = 0; i < elements.size(); i++) {
				String ici = champ + "[" + i + "]";
				if (!(elements.get(i) instanceof Map<?, ?> m)) {
					erreurs.ajouter(ici, "type_invalide");
					continue;
				}
				m.keySet().stream().map(String::valueOf).filter(c -> !CHAMPS_REPONSE.contains(c)).sorted()
					.forEach(c -> erreurs.ajouter(ici + "." + c, "champ_inconnu"));
				Object id = m.get("questionId");
				if (!(id instanceof String s) || questions.stream().noneMatch(q -> q.id().equals(s))) {
					erreurs.ajouter(ici + ".questionId", id == null ? "obligatoire" : "valeur_inconnue");
				}
				else if (valeurs.containsKey(s)) {
					erreurs.ajouter(ici + ".questionId", "doublon");
				}
				else {
					valeurs.put(s, m.get("valeur"));
				}
			}
		}
		List<Reponse> reponses = new ArrayList<>();
		for (Question q : questions) {
			String ici = champ + "." + q.id();
			Object valeur = valeur(q, valeurs.get(q.id()), ici, erreurs);
			if (valeur != null) {
				reponses.add(new Reponse(q.id(), valeur));
			}
			else if (q.obligatoire() && !erreurDejaSignalee(valeurs.get(q.id()))) {
				erreurs.ajouter(ici, "obligatoire");
			}
		}
		return List.copyOf(reponses);
	}

	/** Une valeur présente mais mal formée a déjà son erreur : on n'y ajoute pas « obligatoire ». */
	private static boolean erreurDejaSignalee(Object brute) {
		return brute != null && !(brute instanceof String s && s.isBlank()) && !(brute instanceof List<?> l && l.isEmpty());
	}

	/** La valeur propre d'une réponse, ou `null` si elle est vide ou invalide (l'erreur est alors écrite). */
	private static Object valeur(Question q, Object brute, String champ, Erreurs erreurs) {
		if (brute == null) {
			return null;
		}
		if ("choix_multiples".equals(q.type())) {
			if (!(brute instanceof List<?> elements)) {
				erreurs.ajouter(champ, "type_invalide");
				return null;
			}
			List<String> choisis = new ArrayList<>();
			for (Object e : elements) {
				if (!(e instanceof String s) || !q.choix().contains(s)) {
					erreurs.ajouter(champ, "valeur_inconnue");
					return null;
				}
				if (!choisis.contains(s)) {
					choisis.add(s);
				}
			}
			return choisis.isEmpty() ? null : List.copyOf(choisis);
		}
		if (!(brute instanceof String s)) {
			erreurs.ajouter(champ, "type_invalide");
			return null;
		}
		String propre = s.strip();
		if (propre.isEmpty()) {
			return null;
		}
		boolean valide = switch (q.type()) {
			case "choix_unique" -> q.choix().contains(propre);
			case "oui_non" -> "oui".equals(propre) || "non".equals(propre);
			default -> true;
		};
		if (!valide) {
			erreurs.ajouter(champ, "valeur_inconnue");
			return null;
		}
		int max = "texte_long".equals(q.type()) ? TEXTE_LONG_MAX : TEXTE_COURT_MAX;
		if (propre.length() > max) {
			erreurs.ajouter(champ, "trop_long");
			return null;
		}
		return propre;
	}

	private static String texte(Object brut, String champ, int min, int max, boolean obligatoire, Erreurs erreurs) {
		if (brut == null) {
			if (obligatoire) {
				erreurs.ajouter(champ, "obligatoire");
			}
			return null;
		}
		if (!(brut instanceof String s)) {
			erreurs.ajouter(champ, "type_invalide");
			return null;
		}
		String propre = s.strip();
		if (propre.length() < min) {
			erreurs.ajouter(champ, propre.isEmpty() && obligatoire ? "obligatoire" : "trop_court");
			return null;
		}
		if (propre.length() > max) {
			erreurs.ajouter(champ, "trop_long");
			return null;
		}
		return propre;
	}

	// ------------------------------------------------------------------ stockage (colonnes JSONB)

	/** À écrire avec `CAST(:x AS jsonb)`. */
	public static String enJson(List<?> liste) {
		return JSON.writeValueAsString(liste == null ? List.of() : liste);
	}

	public static List<Question> questions(String json) {
		return json == null ? List.of() : JSON.readValue(json, new TypeReference<List<Question>>() {
		});
	}

	public static List<Reponse> reponses(String json) {
		return json == null ? List.of() : JSON.readValue(json, new TypeReference<List<Reponse>>() {
		});
	}

}
