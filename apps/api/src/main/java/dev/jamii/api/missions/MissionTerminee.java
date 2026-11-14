package dev.jamii.api.missions;

import java.time.Instant;
import java.util.UUID;

/**
 * Événement public : une mission est terminée, par l'acceptation de son dernier jalon (par le client ou
 * automatiquement) ou par la décision d'un modérateur. Le profil du développeur peut en faire une preuve vérifiable
 * (`/missions/{missionId}`), sans que le module des profils lise les tables des missions.
 *
 * @param missionId la mission
 * @param clientId le client
 * @param developpeurId le développeur retenu
 * @param titre le titre de la mission, tel qu'il était à la fin
 * @param termineeLe l'instant où la mission s'est terminée
 */
public record MissionTerminee(UUID missionId, UUID clientId, UUID developpeurId, String titre, Instant termineeLe) {
}
