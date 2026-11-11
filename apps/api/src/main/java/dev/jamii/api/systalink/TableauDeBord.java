package dev.jamii.api.systalink;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Schéma TableauDeBord du contrat. */
record TableauDeBord(Periode periode, Instant calculeLe, Inscrits inscrits, Projets projets, List<Moment> moments) {

	/** En dessous de ce nombre d'inscrits, un pays passe dans « autres » : personne ne doit pouvoir être reconnu. */
	static final long SEUIL_ANONYMAT = 5;

	record Inscrits(long total, long nouveaux, List<Pays> parPays, long autres) {

		/**
		 * Garde les pays d'au moins {@link #SEUIL_ANONYMAT} inscrits, du plus grand au plus petit (puis par code,
		 * pour un ordre stable). Tout le reste, y compris les inscrits sans pays, va dans `autres`.
		 */
		static Inscrits anonymiser(long total, long nouveaux, Map<String, Long> parPays) {
			List<Pays> visibles = parPays.entrySet()
				.stream()
				.filter(e -> e.getValue() >= SEUIL_ANONYMAT)
				.map(e -> new Pays(e.getKey(), e.getValue()))
				.sorted(Comparator.comparingLong(Pays::nombre).reversed().thenComparing(Pays::pays))
				.toList();
			long montres = visibles.stream().mapToLong(Pays::nombre).sum();
			return new Inscrits(total, nouveaux, visibles, Math.max(0, total - montres));
		}

	}

	record Pays(String pays, long nombre) {
	}

	record Projets(long crees, long nouveaux, long enLigne, long surDatacloud) {
	}

	record Moment(ServiceSystalink service, long proposes, long acceptes) {
	}

}
