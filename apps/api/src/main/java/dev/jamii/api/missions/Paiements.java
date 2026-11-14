package dev.jamii.api.missions;

import java.util.UUID;

/**
 * Le paiement d'un jalon. En v1, Jamii n'encaisse rien : chacun déclare (le client « payé », puis le développeur
 * « reçu »). Un séquestre par paiement mobile pourra implémenter la même interface sans changer le contrat.
 */
interface Paiements {

	enum Sens {
		PAYE, RECU
	}

	/** `non_paye`, `declare_paye` ou `declare_recu`. */
	String statut(UUID jalonId);

	/** Déclare un paiement ; dans le désordre (« reçu » avant « payé », ou deux fois) : 409 `etat_invalide`. */
	void declarer(UUID jalonId, Sens sens);

}
