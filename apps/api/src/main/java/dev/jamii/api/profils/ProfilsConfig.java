package dev.jamii.api.profils;

import java.time.Clock;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import dev.jamii.api.auth.Annuaire;

/**
 * Choix de l'adaptateur GitHub par la configuration, jamais par le code (AGENTS.md §3) :
 * GITHUB_SOURCE=github (par défaut) ou simule (tests, démo sans réseau).
 */
@Configuration
class ProfilsConfig {

	@Bean
	SourceGithub sourceGithub(@Value("${jamii.github.source:github}") String source,
			@Value("${jamii.github.url-api:https://api.github.com}") String urlApi,
			@Value("${jamii.github.jeton:}") String jeton) {
		return switch (source) {
			case "github" -> new SourceGithubHttp(urlApi, jeton);
			case "simule" -> new SourceGithubSimulee();
			default -> throw new IllegalStateException("GITHUB_SOURCE inconnu : " + source + " (github ou simule)");
		};
	}

	@Bean
	GenerationProfils generationProfils(ProfilRepository profils, Annuaire comptes, SourceGithub github,
			PlatformTransactionManager transactions) {
		// Un fil virtuel par génération : il attend surtout GitHub, et ne coûte presque rien pendant ce temps.
		return new GenerationProfils(profils, comptes, github, new TransactionTemplate(transactions),
				Executors.newVirtualThreadPerTaskExecutor(), Clock.systemUTC());
	}

}
