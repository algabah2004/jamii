package dev.jamii.api.missions;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Événement public : le client a accepté un devis, la mission est attribuée. Le développeur retenu et chaque candidat
 * écarté peuvent être prévenus, sans que le module du réseau lise les tables des missions.
 *
 * @param missionId la mission
 * @param devisId le devis accepté, qui vaut contrat
 * @param clientId le client
 * @param developpeurId le développeur retenu
 * @param nonRetenus les candidats dont la proposition était encore en cours, désormais écartés
 * @param attribueeLe l'instant de l'acceptation
 */
public record MissionAttribuee(UUID missionId, UUID devisId, UUID clientId, UUID developpeurId, List<UUID> nonRetenus,
		Instant attribueeLe) {

	public MissionAttribuee {
		nonRetenus = List.copyOf(nonRetenus);
	}

}
