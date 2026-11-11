package dev.jamii.api.auth;

import java.util.UUID;

/**
 * Ce que les autres modules ont le droit de savoir d'un compte. Jamais l'e-mail, les rôles ni l'identifiant GitHub.
 * Les autres modules gardent un identifiant (UUID) et passent par {@link Annuaire} pour afficher la personne.
 */
public record Compte(UUID id, String nom, String avatarUrl, String loginGithub) {
}
