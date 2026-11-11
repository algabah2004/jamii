package dev.jamii.api.systalink;

import java.net.URI;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Page Systalink ouverte pour chaque service. Par défaut, l'inscription Systalink (seule adresse vérifiée,
 * docs/systalink-services.md). On la précise par service avec JAMII_SYSTALINK_LIENS_DATACLOUD=https://…
 */
@ConfigurationProperties("jamii.systalink")
record LiensSystalink(Map<ServiceSystalink, URI> liens) {

	static final URI PAR_DEFAUT = URI.create("https://platform.systalink.com/sign-up");

	LiensSystalink {
		liens = liens == null ? Map.of() : Map.copyOf(liens);
		liens.values().forEach(lien -> {
			if (!"https".equals(lien.getScheme())) {
				throw new IllegalArgumentException("Un lien Systalink doit être en https : " + lien);
			}
		});
	}

	URI pour(ServiceSystalink service) {
		return liens.getOrDefault(service, PAR_DEFAUT);
	}

}
