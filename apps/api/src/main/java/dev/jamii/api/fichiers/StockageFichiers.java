package dev.jamii.api.fichiers;

import java.util.Optional;
import java.util.UUID;

/**
 * Où vivent les octets d'un fichier. Aujourd'hui dans PostgreSQL ({@link StockagePostgres}) : rien à installer, et la
 * base est déjà sauvegardée. Demain, le stockage objet de DataCloud : une autre implémentation, le reste ne change pas.
 */
interface StockageFichiers {

	void ecrire(UUID id, byte[] octets);

	Optional<byte[]> lire(UUID id);

}
