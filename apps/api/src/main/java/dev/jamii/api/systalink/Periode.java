package dev.jamii.api.systalink;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonValue;

/** Fenêtre des compteurs « nouveaux » (schéma Periode du contrat). */
enum Periode {

	SEMAINE(Duration.ofDays(7)), MOIS(Duration.ofDays(30)), TOUT(null);

	private final Duration duree;

	Periode(Duration duree) {
		this.duree = duree;
	}

	/** Début de la fenêtre, ou null pour « depuis le début ». */
	Instant debut(Instant maintenant) {
		return duree == null ? null : maintenant.minus(duree);
	}

	@JsonValue
	String code() {
		return name().toLowerCase(Locale.ROOT);
	}

	/** Absente : `mois` (défaut du contrat). Inconnue : vide, et le contrôleur répond 400. */
	static Optional<Periode> lire(String valeur) {
		if (valeur == null || valeur.isBlank()) {
			return Optional.of(MOIS);
		}
		for (Periode p : values()) {
			if (p.code().equals(valeur)) {
				return Optional.of(p);
			}
		}
		return Optional.empty();
	}

}
