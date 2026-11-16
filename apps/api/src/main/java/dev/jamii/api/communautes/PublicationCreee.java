package dev.jamii.api.communautes;

import java.time.Instant;
import java.util.UUID;

/** Publié quand un membre publie dans le fil d'une communauté : le réseau prévient ses abonnés (#157). */
public record PublicationCreee(UUID publicationId, UUID communauteId, UUID auteurId, Instant creeLe) {
}
