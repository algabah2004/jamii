package dev.jamii.api.hackathons;

import java.time.Instant;
import java.util.UUID;

/**
 * Événement public : l'organisateur vient d'ajouter un juré. Le module du réseau peut le prévenir (« Awa t'invite au
 * jury du Dakar Cloud Hack ») sans lire les tables des hackathons.
 *
 * @param hackathonId le hackathon
 * @param jureId la personne ajoutée au jury
 * @param organisateurId l'organisateur qui l'a ajoutée
 * @param inviteLe l'instant de l'invitation
 */
public record JureInvite(UUID hackathonId, UUID jureId, UUID organisateurId, Instant inviteLe) {
}
