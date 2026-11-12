package dev.jamii.api.demandes;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.json.JsonMapper;

/** Choix du service IA par la configuration, jamais par le code (AGENTS.md §3). */
@Configuration
class DemandesConfig {

	@Bean
	PlansIa plansIa(@Value("${jamii.ia.fournisseur:simule}") String fournisseur, @Value("${jamii.ia.url:}") String url,
			@Value("${jamii.ia.jeton:}") String jeton, @Value("${jamii.ia.delai:3s}") Duration delai,
			@Value("${jamii.ia.simule-attente:4s}") Duration attente, ObjectProvider<Clock> horloge, JsonMapper json) {
		return switch (fournisseur) {
			case "simule" -> new PlansIaSimules(attente, horloge.getIfAvailable(Clock::systemUTC), json);
			case "service" -> new PlansIaHttp(url, jeton, delai);
			default -> throw new IllegalStateException("IA_FOURNISSEUR inconnu : " + fournisseur + " (simule ou service)");
		};
	}

}
