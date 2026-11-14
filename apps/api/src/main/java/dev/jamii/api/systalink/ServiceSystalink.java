package dev.jamii.api.systalink;

import java.util.Locale;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonValue;

/** Les services Systalink que Jamii propose (énumération ServiceSystalink de contracts/systalink.yaml). */
public enum ServiceSystalink {

	DATACLOUD, ORION, YOKK, SYSTATALK, SYSTAFORMS, DOMAINES, SYSTAONE;

	/**
	 * Le service d'une entrée du catalogue de l'orientation (services/ia/catalogue-systalink.yaml) : les trois offres
	 * DataCloud comptent pour DataCloud. Vide pour une entrée inconnue, qui n'est alors pas mesurée.
	 */
	public static Optional<ServiceSystalink> depuisCatalogue(String id) {
		if (id == null) {
			return Optional.empty();
		}
		if (id.startsWith("datacloud")) {
			return Optional.of(DATACLOUD);
		}
		return switch (id) {
			case "orion" -> Optional.of(ORION);
			case "nom-de-domaine" -> Optional.of(DOMAINES);
			case "systaone" -> Optional.of(SYSTAONE);
			default -> Optional.empty();
		};
	}

	/** Valeur du contrat et de la base : `datacloud`, `orion`… */
	@JsonValue
	public String code() {
		return name().toLowerCase(Locale.ROOT);
	}

	static ServiceSystalink depuisCode(String code) {
		return valueOf(code.toUpperCase(Locale.ROOT));
	}

}
