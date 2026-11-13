package dev.jamii.api.profils;

import java.time.Clock;
import java.time.Duration;
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

	/** IA_FOURNISSEUR, comme pour le plan des demandes : simule (par défaut) ou service. */
	@Bean
	CartesIa cartesIa(@Value("${jamii.ia.fournisseur:simule}") String fournisseur, @Value("${jamii.ia.url:}") String url,
			@Value("${jamii.ia.jeton:}") String jeton, @Value("${jamii.ia.delai:3s}") Duration delai) {
		return switch (fournisseur) {
			case "simule" -> new CartesIaSimulees();
			// Moins que GENERATION_PERDUE : la génération se termine toujours avant d'être tenue pour perdue.
			case "service" -> new CartesIaHttp(url, jeton, delai, Duration.ofSeconds(75), Duration.ofSeconds(2));
			default -> throw new IllegalStateException("IA_FOURNISSEUR inconnu : " + fournisseur + " (simule ou service)");
		};
	}

	@Bean
	GenerationProfils generationProfils(ProfilRepository profils, Annuaire comptes, SourceGithub github, CartesIa cartes,
			PlatformTransactionManager transactions) {
		// Un fil virtuel par génération : il attend surtout GitHub, et ne coûte presque rien pendant ce temps.
		return new GenerationProfils(profils, comptes, github, cartes, new TransactionTemplate(transactions),
				Executors.newVirtualThreadPerTaskExecutor(), Clock.systemUTC());
	}

}
