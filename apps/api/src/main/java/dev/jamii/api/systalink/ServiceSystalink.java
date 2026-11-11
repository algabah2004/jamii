package dev.jamii.api.systalink;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;

/** Les services Systalink que Jamii propose (énumération ServiceSystalink de contracts/systalink.yaml). */
public enum ServiceSystalink {

	DATACLOUD, ORION, YOKK, SYSTATALK, SYSTAFORMS, DOMAINES;

	/** Valeur du contrat et de la base : `datacloud`, `orion`… */
	@JsonValue
	public String code() {
		return name().toLowerCase(Locale.ROOT);
	}

	static ServiceSystalink depuisCode(String code) {
		return valueOf(code.toUpperCase(Locale.ROOT));
	}

}
