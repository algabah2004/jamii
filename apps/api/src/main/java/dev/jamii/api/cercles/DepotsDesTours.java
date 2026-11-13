package dev.jamii.api.cercles;

import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * Crée le dépôt Git d'un tour qui vient de s'ouvrir, <b>après</b> la transaction du tirage ou de la livraison :
 * un fournisseur lent ou en panne ne défait jamais un tirage. Sans adaptateur {@link DepotCode}, ou en cas d'échec,
 * le tour reste sans dépôt (`depot: null`) et le cercle continue.
 */
@Service
class DepotsDesTours {

	private static final Logger journal = LoggerFactory.getLogger(DepotsDesTours.class);

	private final ObjectProvider<DepotCode> adaptateur;

	private final GestionCercles gestion;

	DepotsDesTours(ObjectProvider<DepotCode> adaptateur, GestionCercles gestion) {
		this.adaptateur = adaptateur;
		this.gestion = gestion;
	}

	void creer(UUID cercleId, int numero) {
		DepotCode depots = adaptateur.getIfAvailable();
		if (depots == null || numero < 1) {
			return;
		}
		try {
			gestion.demandeDepot(cercleId, numero).flatMap(depots::creerDepot).ifPresent(depot -> {
				if (valide(depot)) {
					gestion.enregistrerDepot(cercleId, numero, depot);
				}
				else {
					journal.warn("Dépôt du tour {} ignoré : fournisseur ou adresse invalide", numero);
				}
			});
		}
		catch (RuntimeException e) {
			// Ni adresse ni jeton dans les journaux : seulement le type de l'erreur.
			journal.warn("Dépôt du tour {} impossible : {}", numero, e.getClass().getSimpleName());
		}
	}

	private static boolean valide(DepotCode.DepotDuTour depot) {
		return ("gitea".equals(depot.fournisseur()) || "github".equals(depot.fournisseur()))
				&& depot.url() != null && depot.url().startsWith("https://")
				&& depot.cloneUrl() != null && depot.cloneUrl().startsWith("https://");
	}

}
