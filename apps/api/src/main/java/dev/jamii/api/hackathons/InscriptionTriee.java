package dev.jamii.api.hackathons;

import java.time.Instant;
import java.util.UUID;

/**
 * Événement public : l'organisateur vient d'accepter, de refuser ou de mettre en liste d'attente une inscription
 * (mode « candidature »). La personne peut être prévenue sans que le module du réseau lise les tables des hackathons.
 *
 * @param hackathonId le hackathon
 * @param inscriptionId l'inscription triée
 * @param utilisateurId la personne à prévenir
 * @param statut `acceptee`, `refusee` ou `attente`
 * @param trieeLe l'instant de la décision
 */
public record InscriptionTriee(UUID hackathonId, UUID inscriptionId, UUID utilisateurId, String statut, Instant trieeLe) {
}
