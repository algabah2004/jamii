package dev.jamii.api.profils;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Une compétence est un texte libre normalisé (contrat : Competence de projets.yaml). Les variantes courantes sont
 * ramenées à une seule forme, sinon « react » et « react.js » seraient deux compétences différentes dans la recherche.
 */
final class Competences {

	static final int LONGUEUR_MAX = 50;

	private static final Pattern FORME = Pattern.compile("^[a-z0-9][a-z0-9.+#-]*$");

	private static final Map<String, String> VARIANTES = Map.ofEntries(
			Map.entry("react.js", "react"), Map.entry("reactjs", "react"),
			Map.entry("vue.js", "vue"), Map.entry("vuejs", "vue"),
			Map.entry("nodejs", "node.js"), Map.entry("node", "node.js"),
			Map.entry("postgres", "postgresql"), Map.entry("psql", "postgresql"),
			Map.entry("js", "javascript"), Map.entry("ts", "typescript"),
			Map.entry("golang", "go"), Map.entry("k8s", "kubernetes"),
			Map.entry("springboot", "spring-boot"), Map.entry("nextjs", "next.js"));

	private Competences() {
	}

	/** La forme normalisée, ou vide si le texte ne peut pas devenir une compétence valide. */
	static Optional<String> normaliser(String brut) {
		if (brut == null) {
			return Optional.empty();
		}
		String forme = brut.strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", "-");
		forme = VARIANTES.getOrDefault(forme, forme);
		if (forme.isEmpty() || forme.length() > LONGUEUR_MAX || !FORME.matcher(forme).matches()) {
			return Optional.empty();
		}
		return Optional.of(forme);
	}

}
