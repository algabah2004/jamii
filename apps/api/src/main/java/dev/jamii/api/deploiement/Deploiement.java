package dev.jamii.api.deploiement;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Schéma Deploiement de contracts/deploiement.yaml. Un champ inconnu est absent de la réponse, jamais `null`
 * (docs/api-conventions.md, §1).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
record Deploiement(UUID id, UUID projetId, String url, Hebergeur hebergeur, Statut statut, Instant verifieLe,
		Instant misEnLigneLe, Essai dernierEssai, Instant creeLe) {

	enum Statut {

		EN_COURS, EN_LIGNE, ECHEC;

		@JsonValue
		String code() {
			return name().toLowerCase(Locale.ROOT);
		}

		static Statut depuisCode(String code) {
			return valueOf(code.toUpperCase(Locale.ROOT));
		}

	}

	/** La dernière vérification : `resultat` vaut un code de {@link Verificateur.Resultat} (`repond`…). */
	record Essai(Instant le, String resultat) {
	}

}
