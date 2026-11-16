package dev.jamii.api.cercles;

import java.time.Instant;
import java.util.UUID;

/** Publié quand quelqu'un ouvre un cercle : le réseau prévient ses abonnés (#157). */
public record CercleCree(UUID cercleId, UUID createurId, Instant creeLe) {
}
