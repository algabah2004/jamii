package dev.jamii.api.missions;

import java.time.Instant;
import java.util.UUID;

/**
 * Événement public : le développeur retenu vient d'envoyer son devis (ou d'en envoyer un nouveau). Le client peut être
 * prévenu qu'il a un devis à relire.
 *
 * @param missionId la mission
 * @param devisId le devis à relire
 * @param clientId le client à prévenir
 * @param developpeurId l'auteur du devis
 * @param envoyeLe l'instant de l'envoi
 */
public record DevisEnvoye(UUID missionId, UUID devisId, UUID clientId, UUID developpeurId, Instant envoyeLe) {
}
