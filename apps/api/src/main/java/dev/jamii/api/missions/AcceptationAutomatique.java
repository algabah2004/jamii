package dev.jamii.api.missions;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Toutes les 15 minutes, accepte les jalons livrés sans réponse du client depuis 72 heures. Le client sait qu'il a ce
 * délai : la date d'acceptation automatique est affichée dès la livraison (`acceptationAutomatiqueLe`).
 */
@Component
class AcceptationAutomatique {

	private final Livraisons livraisons;

	AcceptationAutomatique(Livraisons livraisons) {
		this.livraisons = livraisons;
	}

	@Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT2M")
	void accepter() {
		livraisons.accepterAutomatiquement();
	}

}
