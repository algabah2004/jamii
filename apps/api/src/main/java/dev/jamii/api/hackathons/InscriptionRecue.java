package dev.jamii.api.hackathons;

import java.time.Instant;
import java.util.UUID;

/**
 * Événement public : une personne vient de poser sa candidature à un hackathon « sur candidature » (inscription
 * `en_attente`). L'organisateur peut être prévenu sans que le module du réseau lise les tables des hackathons.
 *
 * @param hackathonId le hackathon
 * @param inscriptionId l'inscription créée
 * @param organisateurId l'organisateur à prévenir
 * @param utilisateurId le candidat (son nom se lit par l'annuaire des comptes)
 * @param recueLe l'instant de la candidature
 */
public record InscriptionRecue(UUID hackathonId, UUID inscriptionId, UUID organisateurId, UUID utilisateurId, Instant recueLe) {
}
