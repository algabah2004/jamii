package dev.jamii.api.systalink;

import java.net.URI;
import java.util.UUID;

/**
 * Schéma PropositionSystalink du contrat : un module la renvoie au front quand il propose un service Systalink.
 * Le front affiche le texte (traduit selon `service`) et appelle `accepter` au clic.
 */
public record PropositionSystalink(UUID momentId, ServiceSystalink service, URI lien) {
}
