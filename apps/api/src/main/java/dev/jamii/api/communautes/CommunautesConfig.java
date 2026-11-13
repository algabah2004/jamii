package dev.jamii.api.communautes;

import java.time.Duration;
import java.util.concurrent.Executors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Choix de l'IA des communautés par la configuration, jamais par le code (AGENTS.md §3). */
@Configuration
class CommunautesConfig {

	@Bean
	PropositionsIa propositionsIa(@Value("${jamii.communautes.ia:simulee}") String ia,
			@Value("${jamii.communautes.ia-reflexion:3s}") Duration reflexion, GestionReponses reponses) {
		ReponsesIa service = switch (ia) {
			case "simulee" -> new ReponsesIaSimulees(reflexion);
			case "aucune" -> null;
			default -> throw new IllegalStateException("COMMUNAUTES_IA inconnu : " + ia + " (simulee ou aucune)");
		};
		// Un fil virtuel par question : il attend surtout l'IA, et ne coûte presque rien pendant ce temps.
		return new PropositionsIa(service, reponses, Executors.newVirtualThreadPerTaskExecutor());
	}

}
