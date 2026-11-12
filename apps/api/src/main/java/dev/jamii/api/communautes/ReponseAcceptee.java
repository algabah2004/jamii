package dev.jamii.api.communautes;

import java.time.Instant;
import java.util.UUID;

/**
 * Publié quand l'auteur d'une question accepte la réponse d'un **humain** : le module profils peut ajouter la preuve
 * `reponse_acceptee` à `auteurId` (docs/modele-donnees.md §6). Jamais publié pour une réponse de l'IA, même validée
 * par un pair : elle ne compte sur le profil de personne (contrat).
 */
public record ReponseAcceptee(UUID reponseId, UUID publicationId, UUID auteurId, Instant accepteeLe) {
}
