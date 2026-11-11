package dev.jamii.api.deploiement;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import dev.jamii.api.systalink.ChiffresDesModules;

/**
 * API publique du module pour les autres modules (contracts/deploiement.yaml, « Pour les autres modules »).
 * <ul>
 * <li>la vitrine (#18) lit {@link #lister()} ;</li>
 * <li>le tableau de bord Systalink (#18) lit les deux compteurs de {@link ChiffresDesModules.Deploiements}.</li>
 * </ul>
 * « En ligne » veut toujours dire : l'adresse a répondu il y a moins de 24 heures.
 */
@Service
public class DeploiementsEnLigne implements ChiffresDesModules.Deploiements {

	private final DepotDeploiements depot;

	private final Clock horloge;

	DeploiementsEnLigne(DepotDeploiements depot, ObjectProvider<Clock> horloge) {
		this.depot = depot;
		this.horloge = horloge.getIfAvailable(Clock::systemUTC);
	}

	/** Un élément par projet en ligne : le déploiement mis en ligne le premier parmi ceux qui répondent. */
	public List<ProjetEnLigne> lister() {
		return depot.enLigne(horloge.instant());
	}

	@Override
	public long projetsEnLigne() {
		return depot.compterProjetsEnLigne(null, horloge.instant());
	}

	@Override
	public long projetsSurDatacloud() {
		return depot.compterProjetsEnLigne(Hebergeur.DATACLOUD, horloge.instant());
	}

	/** Ce que la vitrine affiche d'un projet en ligne. `url` est toujours en https. */
	public record ProjetEnLigne(UUID projetId, UUID deploiementId, String url, Hebergeur hebergeur,
			Instant misEnLigneLe, Instant verifieLe) {
	}

}
