package dev.jamii.api.deploiement;

import java.time.Instant;
import java.util.UUID;

/**
 * Événement publié au premier passage `en_ligne` d'un projet : la vitrine et les profils peuvent réagir sans que le
 * module deploiement les connaisse (docs/modele-donnees.md, §6). Il n'est pas republié si le projet retombe puis
 * revient.
 */
public record ProjetMisEnLigne(UUID projetId, UUID deploiementId, Hebergeur hebergeur, Instant misEnLigneLe) {
}
