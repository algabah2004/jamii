package dev.jamii.api.auth;

import com.fasterxml.jackson.annotation.JsonValue;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Rôles globaux (contrat : Role dans contracts/socle.yaml). Les rôles dans un projet appartiennent au module projets. */
public enum Role {

	MEMBRE, MODERATEUR, ADMIN;

	/** Valeur du contrat et de la base : « membre », « moderateur », « admin ». */
	@JsonValue
	public String valeur() {
		return name().toLowerCase(java.util.Locale.ROOT);
	}

	/** Autorité Spring Security, pour hasRole("ADMIN") dans les règles d'accès. */
	String autorite() {
		return "ROLE_" + name();
	}

	@Converter(autoApply = true)
	static class EnBase implements AttributeConverter<Role, String> {

		@Override
		public String convertToDatabaseColumn(Role role) {
			return role.valeur();
		}

		@Override
		public Role convertToEntityAttribute(String valeur) {
			return Role.valueOf(valeur.toUpperCase(java.util.Locale.ROOT));
		}

	}

}
