package dev.jamii.api.cercles;

import java.time.Duration;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Choix du dépôt des tours par la configuration (`DEPOT_CODE`), jamais par le code (AGENTS.md §3) :
 * `aucun` (par défaut : les tours n'ont pas de dépôt) ou `github`. Gitea s'ajoutera ici.
 */
@Configuration
class DepotsConfig {

	DepotsConfig(@Value("${jamii.depot-code.fournisseur:aucun}") String fournisseur) {
		if (!Set.of("aucun", "github").contains(fournisseur)) {
			throw new IllegalStateException("DEPOT_CODE inconnu : " + fournisseur + " (aucun ou github)");
		}
	}

	@Bean
	@ConditionalOnProperty(name = "jamii.depot-code.fournisseur", havingValue = "github")
	DepotCode depotsGithub(@Value("${jamii.depot-code.github.api-url:https://api.github.com}") String apiUrl,
			@Value("${jamii.depot-code.github.organisation:}") String organisation,
			@Value("${jamii.depot-code.github.jeton:}") String jeton,
			@Value("${jamii.depot-code.github.prives:false}") boolean prives,
			@Value("${jamii.depot-code.delai:5s}") Duration delai) {
		return new DepotsGithub(apiUrl, organisation, jeton, prives, delai);
	}

}
