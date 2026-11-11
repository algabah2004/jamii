package dev.jamii.api.systalink;

import java.util.Locale;

/** Où le service a été proposé : sur un projet, une demande de PME ou dans une communauté. */
public enum ContexteMoment {

	PROJET, DEMANDE, COMMUNAUTE;

	String code() {
		return name().toLowerCase(Locale.ROOT);
	}

}
