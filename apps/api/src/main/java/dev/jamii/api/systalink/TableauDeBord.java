package dev.jamii.api.systalink;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Schéma TableauDeBord du contrat. */
record TableauDeBord(Periode periode, Instant calculeLe, Inscrits inscrits, Projets projets, Cercles cercles,
		List<Moment> moments) {

	/** En dessous de ce nombre de personnes, un pays passe dans « autres » : personne ne doit pouvoir être reconnu. */
	static final long SEUIL_ANONYMAT = 5;

	record Inscrits(long total, long nouveaux, List<Pays> parPays, long autres) {

		static Inscrits anonymiser(long total, long nouveaux, Map<String, Long> parPays) {
			List<Pays> visibles = visibles(parPays);
			return new Inscrits(total, nouveaux, visibles, reste(total, visibles));
		}

	}

	record Cercles(long actifs, long termines, long toursLivres, long nouveauxToursLivres, Membres membres) {

		/** Tant que le module cercles n'est pas branché : 0 partout, jamais un chiffre inventé. */
		static final Cercles AUCUN = new Cercles(0, 0, 0, 0, Membres.anonymiser(0, Map.of()));

	}

	record Membres(long total, List<Pays> parPays, long autres) {

		static Membres anonymiser(long total, Map<String, Long> parPays) {
			List<Pays> visibles = visibles(parPays);
			return new Membres(total, visibles, reste(total, visibles));
		}

	}

	/**
	 * Les pays d'au moins {@link #SEUIL_ANONYMAT} personnes, du plus grand au plus petit (puis par code, pour un ordre
	 * stable). Tout le reste, y compris les personnes sans pays, va dans `autres`.
	 */
	private static List<Pays> visibles(Map<String, Long> parPays) {
		return parPays.entrySet()
			.stream()
			.filter(e -> e.getValue() >= SEUIL_ANONYMAT)
			.map(e -> new Pays(e.getKey(), e.getValue()))
			.sorted(Comparator.comparingLong(Pays::nombre).reversed().thenComparing(Pays::pays))
			.toList();
	}

	/** Jamais négatif, même si deux modules se contredisent. */
	private static long reste(long total, List<Pays> visibles) {
		return Math.max(0, total - visibles.stream().mapToLong(Pays::nombre).sum());
	}

	record Pays(String pays, long nombre) {
	}

	record Projets(long crees, long nouveaux, long enLigne, long surDatacloud) {
	}

	record Moment(ServiceSystalink service, long proposes, long acceptes) {
	}

}
