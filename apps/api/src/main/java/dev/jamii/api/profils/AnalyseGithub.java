package dev.jamii.api.profils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import dev.jamii.api.profils.SourceGithub.Depot;

/**
 * Transforme les dépôts publics en éléments du profil. Calcul pur, sans réseau ni base : facile à tester.
 * Les forks sont écartés (ce n'est pas le travail de la personne).
 */
final class AnalyseGithub {

	static final int LANGAGES_MAX = 8;

	static final int DEPOTS_MAX = 6;

	static final int COMPETENCES_MAX = 15;

	private AnalyseGithub() {
	}

	record Resultat(List<Profil.Langage> langages, List<Profil.DepotGithub> depots, Set<String> competences) {
	}

	static Resultat analyser(List<Depot> tous) {
		List<Depot> siens = tous.stream().filter(d -> !d.fork()).toList();
		List<Profil.Langage> langages = langages(siens);
		return new Resultat(langages, depots(siens), competences(siens, langages));
	}

	/**
	 * Part de chaque langage. Approximation assumée : le langage principal de chaque dépôt, pondéré par sa taille.
	 * Le détail exact demanderait une requête par dépôt, et la limite de GitHub serait vite atteinte.
	 */
	private static List<Profil.Langage> langages(List<Depot> depots) {
		Map<String, Long> poids = depots.stream()
			.filter(d -> d.langage() != null && !d.langage().isBlank())
			.collect(Collectors.groupingBy(Depot::langage, Collectors.summingLong(d -> Math.max(1, d.tailleKo()))));
		long total = poids.values().stream().mapToLong(Long::longValue).sum();
		if (total == 0) {
			return List.of();
		}
		return poids.entrySet().stream()
			.sorted(Map.Entry.<String, Long>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
			.limit(LANGAGES_MAX)
			.map(e -> new Profil.Langage(e.getKey(),
					BigDecimal.valueOf(e.getValue()).divide(BigDecimal.valueOf(total), 3, RoundingMode.HALF_UP)))
			.toList();
	}

	private static List<Profil.DepotGithub> depots(List<Depot> depots) {
		return depots.stream()
			.sorted(Comparator.comparingInt(Depot::etoiles).reversed()
				.thenComparing(Depot::pousseLe, Comparator.nullsLast(Comparator.reverseOrder())))
			.limit(DEPOTS_MAX)
			.map(d -> new Profil.DepotGithub(couper(d.nom(), 100), d.url(), couper(d.description(), 350),
					couper(d.langage(), 50), d.etoiles(), d.pousseLe()))
			.toList();
	}

	/** Les langages d'abord, puis les sujets (topics) les plus fréquents : flutter, firebase… */
	private static Set<String> competences(List<Depot> depots, List<Profil.Langage> langages) {
		Set<String> competences = new LinkedHashSet<>();
		langages.forEach(l -> Competences.normaliser(l.nom()).ifPresent(competences::add));
		Map<String, Long> sujets = depots.stream()
			.flatMap(d -> d.sujets().stream())
			.map(Competences::normaliser)
			.flatMap(java.util.Optional::stream)
			.collect(Collectors.groupingBy(Function.identity(), LinkedHashMap::new, Collectors.counting()));
		List<Map.Entry<String, Long>> tries = new ArrayList<>(sujets.entrySet());
		tries.sort(Map.Entry.<String, Long>comparingByValue().reversed());
		tries.forEach(e -> competences.add(e.getKey()));
		return competences.stream().limit(COMPETENCES_MAX).collect(Collectors.toCollection(LinkedHashSet::new));
	}

	private static String couper(String texte, int max) {
		return texte == null || texte.length() <= max ? texte : texte.substring(0, max);
	}

}
