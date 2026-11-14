package dev.jamii.api.hackathons;

import java.time.Instant;
import java.util.UUID;

/**
 * Événement public : le chef d'une équipe vient d'inviter un participant, qui la rejoint. Le module du réseau peut en
 * faire une notification (« Kofi t'a ajouté à l'équipe Les Griots ») sans lire les tables des hackathons.
 *
 * @param hackathonId le hackathon
 * @param equipeId l'équipe rejointe
 * @param inviteId la personne invitée (son nom se lit par l'annuaire des comptes)
 * @param chefId le chef qui invite
 * @param envoyeeLe l'instant de l'invitation
 */
public record InvitationEquipe(UUID hackathonId, UUID equipeId, UUID inviteId, UUID chefId, Instant envoyeeLe) {
}
