package dev.jamii.api.communautes;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ExecutorService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.annotation.PreDestroy;

/**
 * Demande à l'IA une réponse à une question, en arrière-plan : la question est publiée tout de suite, et la réponse
 * arrive quelques secondes plus tard (contrat : `propositionIa.statut`). Si l'IA tombe, la question reste ouverte aux
 * humains : rien n'est bloqué.
 */
class PropositionsIa {

	/** Au-delà, une proposition encore « en attente » est considérée comme perdue (redémarrage, IA bloquée). */
	static final Duration ATTENTE_MAX = Duration.ofMinutes(2);

	private static final Logger journal = LoggerFactory.getLogger(PropositionsIa.class);

	private final ReponsesIa ia;

	private final GestionReponses reponses;

	private final ExecutorService enArrierePlan;

	PropositionsIa(ReponsesIa ia, GestionReponses reponses, ExecutorService enArrierePlan) {
		this.ia = ia;
		this.reponses = reponses;
		this.enArrierePlan = enArrierePlan;
	}

	@PreDestroy
	void arreter() {
		enArrierePlan.shutdownNow();
	}

	/** Le statut initial d'une nouvelle question : `en_attente` si une IA est branchée, `indisponible` sinon. */
	String statutInitial() {
		return ia == null ? "indisponible" : "en_attente";
	}

	/** À appeler une fois la question enregistrée. Ne bloque jamais l'appelant. */
	void demander(UUID publicationId, ReponsesIa.Question question) {
		if (ia == null) {
			return;
		}
		enArrierePlan.submit(() -> {
			try {
				ia.proposer(question).map(String::strip).filter(t -> !t.isEmpty())
					.ifPresentOrElse(texte -> reponses.propositionPrete(publicationId, texte, Instant.now()),
							() -> reponses.propositionIndisponible(publicationId));
			}
			catch (RuntimeException e) {
				// Pas le texte de la question dans le journal : seulement son identifiant.
				journal.warn("L'IA n'a pas pu proposer de réponse à la publication {} : {}", publicationId, e.toString());
				reponses.propositionIndisponible(publicationId);
			}
		});
	}

	/** Une proposition bloquée trop longtemps s'affiche « indisponible » : le front arrête d'attendre. */
	static String statutAffiche(String statut, Instant creeLe, Instant maintenant) {
		if ("en_attente".equals(statut) && creeLe.plus(ATTENTE_MAX).isBefore(maintenant)) {
			return "indisponible";
		}
		return statut;
	}

}
