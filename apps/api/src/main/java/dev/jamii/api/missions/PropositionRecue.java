package dev.jamii.api.missions;

import java.time.Instant;
import java.util.UUID;

/**
 * Événement public : un développeur vient de proposer ses services pour une mission. Le module du réseau peut en faire
 * une notification pour le client (« Kofi a répondu à ta mission ») sans lire les tables des missions.
 *
 * @param missionId la mission
 * @param propositionId la proposition reçue
 * @param clientId le client à prévenir
 * @param developpeurId l'auteur de la proposition (son nom se lit par l'annuaire des comptes)
 * @param recueLe l'instant de l'envoi
 */
public record PropositionRecue(UUID missionId, UUID propositionId, UUID clientId, UUID developpeurId, Instant recueLe) {
}
