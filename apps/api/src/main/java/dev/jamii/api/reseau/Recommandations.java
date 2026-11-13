package dev.jamii.api.reseau;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import dev.jamii.api.profils.ProfilsPublies;

/**
 * Le calcul des recommandations (contracts/reseau.yaml), sans IA et sans base : des fonctions pures, testées seules.
 * Ce qui compte, dans l'ordre : apporter une compétence qui manque au cercle, être dans la même ville, et que le
 * cercle soit presque complet. Chaque recommandation dit pourquoi (`raisons`) : jamais un score opaque.
 */
final class Recommandations {

	static final String COMPETENCE_MANQUANTE = "competence_manquante";

	static final String MEME_VILLE = "meme_ville";

	static final String DISPONIBLE = "disponible";

	static final String PRESQUE_COMPLET = "presque_complet";

	/** Au plus 6 compétences manquantes affichées pour un cercle. */
	static final int MAX_MANQUANTES = 6;

	record PourMoi(LectureCercles.CercleLu cercle, List<String> competences, List<String> raisons, int score) {
	}

	record PourLeCercle(ProfilsPublies.Resume profil, List<String> competences, List<String> raisons, int score) {
	}

	private Recommandations() {
	}

	/**
	 * Les cercles ouverts à qui mes compétences manquent, ceux de ma ville d'abord.
	 * @param competencesDesMembres les compétences des membres de chaque cercle (profils publiés seulement)
	 */
	static List<PourMoi> cerclesPour(ProfilsPublies.Resume moi, List<LectureCercles.CercleLu> cercles,
			Map<UUID, List<String>> competencesDesMembres, int limite) {
		List<PourMoi> resultat = new ArrayList<>();
		for (var cercle : cercles) {
			if (!cercle.accueille()) {
				continue;
			}
			Set<String> couvertes = couvertes(cercle.membres(), competencesDesMembres);
			List<String> apportees = moi.competences().stream().filter(c -> !couvertes.contains(c)).toList();
			boolean memeVille = memeVille(moi.ville(), cercle.ville());
			if (apportees.isEmpty() && !memeVille) {
				continue;
			}
			List<String> raisons = new ArrayList<>();
			if (!apportees.isEmpty()) {
				raisons.add(COMPETENCE_MANQUANTE);
			}
			if (memeVille) {
				raisons.add(MEME_VILLE);
			}
			boolean presque = cercle.taille() - cercle.membres().size() == 1;
			if (presque) {
				raisons.add(PRESQUE_COMPLET);
			}
			int score = 2 * Math.min(apportees.size(), 3) + (memeVille ? 3 : 0) + (presque ? 1 : 0);
			resultat.add(new PourMoi(cercle, apportees, raisons, score));
		}
		// À score égal, le cercle le plus récent : la liste d'entrée est déjà dans cet ordre, et le tri est stable.
		resultat.sort(Comparator.comparingInt(PourMoi::score).reversed());
		return resultat.subList(0, Math.min(limite, resultat.size()));
	}

	/** Les compétences qui manquent au cercle : celles des développeurs disponibles que ses membres n'ont pas, les plus fréquentes d'abord. */
	static List<String> manquantes(LectureCercles.CercleLu cercle, Map<UUID, List<String>> competencesDesMembres,
			List<ProfilsPublies.Resume> candidats) {
		Set<String> couvertes = couvertes(cercle.membres(), competencesDesMembres);
		Map<String, Integer> frequence = new LinkedHashMap<>();
		candidats.forEach(p -> p.competences()
			.stream()
			.filter(c -> !couvertes.contains(c))
			.forEach(c -> frequence.merge(c, 1, Integer::sum)));
		return frequence.entrySet()
			.stream()
			.sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
			.limit(MAX_MANQUANTES)
			.map(Map.Entry::getKey)
			.toList();
	}

	/** Les profils disponibles qui apportent ce qui manque au cercle, ceux de sa ville d'abord. */
	static List<PourLeCercle> profilsPour(LectureCercles.CercleLu cercle, Map<UUID, List<String>> competencesDesMembres,
			List<ProfilsPublies.Resume> candidats, int limite) {
		Set<String> couvertes = couvertes(cercle.membres(), competencesDesMembres);
		Set<UUID> membres = new HashSet<>(cercle.membres());
		List<PourLeCercle> resultat = new ArrayList<>();
		for (var profil : candidats) {
			if (membres.contains(profil.utilisateurId()) || !DISPONIBLE.equals(profil.disponibilite())) {
				continue;
			}
			List<String> apportees = profil.competences().stream().filter(c -> !couvertes.contains(c)).toList();
			boolean memeVille = memeVille(profil.ville(), cercle.ville());
			if (apportees.isEmpty() && !memeVille) {
				continue;
			}
			List<String> raisons = new ArrayList<>();
			if (!apportees.isEmpty()) {
				raisons.add(COMPETENCE_MANQUANTE);
			}
			if (memeVille) {
				raisons.add(MEME_VILLE);
			}
			raisons.add(DISPONIBLE);
			int score = 2 * Math.min(apportees.size(), 3) + (memeVille ? 3 : 0);
			resultat.add(new PourLeCercle(profil, apportees, raisons, score));
		}
		resultat.sort(Comparator.comparingInt(PourLeCercle::score).reversed());
		return resultat.subList(0, Math.min(limite, resultat.size()));
	}

	private static Set<String> couvertes(Collection<UUID> membres, Map<UUID, List<String>> competencesDesMembres) {
		Set<String> couvertes = new HashSet<>();
		membres.forEach(m -> couvertes.addAll(competencesDesMembres.getOrDefault(m, List.of())));
		return couvertes;
	}

	/** Même ville, sans tenir compte des majuscules ni des espaces autour (« dakar » = « Dakar »). */
	static boolean memeVille(String a, String b) {
		return a != null && b != null && !a.isBlank() && a.strip().equalsIgnoreCase(b.strip());
	}

}
