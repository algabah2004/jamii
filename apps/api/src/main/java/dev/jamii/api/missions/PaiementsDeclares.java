package dev.jamii.api.missions;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Le paiement déclaré (v1, `PAIEMENTS=declares`, par défaut) : rien n'est encaissé, l'écran le dit. La condition sur l'état
 * précédent départage deux déclarations simultanées et impose l'ordre « payé » puis « reçu ».
 */
@Component
@ConditionalOnProperty(name = "jamii.paiements.fournisseur", havingValue = "declares", matchIfMissing = true)
class PaiementsDeclares implements Paiements {

	private final JdbcClient base;

	PaiementsDeclares(JdbcClient base) {
		this.base = base;
	}

	@Override
	public String statut(UUID jalonId) {
		return base.sql("SELECT paiement FROM jalon_mission WHERE id = :j").param("j", jalonId).query(String.class).single();
	}

	@Override
	public void declarer(UUID jalonId, Sens sens) {
		String avant = sens == Sens.PAYE ? "non_paye" : "declare_paye";
		String apres = sens == Sens.PAYE ? "declare_paye" : "declare_recu";
		int lignes = base.sql("UPDATE jalon_mission SET paiement = :apres WHERE id = :j AND paiement = :avant")
			.param("apres", apres).param("j", jalonId).param("avant", avant).update();
		if (lignes == 0) {
			throw ErreurMissions.conflit("etat_invalide", sens == Sens.PAYE ? "Ce paiement est déjà déclaré"
					: "Le client doit d'abord déclarer avoir payé");
		}
	}

}
