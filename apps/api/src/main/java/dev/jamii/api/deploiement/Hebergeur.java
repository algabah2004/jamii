package dev.jamii.api.deploiement;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;

/** Où le projet est hébergé (schéma Hebergeur de contracts/vitrine.yaml). `DATACLOUD` : « Hébergé sur Systalink ». */
public enum Hebergeur {

	DATACLOUD, AUTRE;

	/** Valeur du contrat et de la base : `datacloud` ou `autre`. */
	@JsonValue
	public String code() {
		return name().toLowerCase(Locale.ROOT);
	}

	static Hebergeur depuisCode(String code) {
		return valueOf(code.toUpperCase(Locale.ROOT));
	}

}
