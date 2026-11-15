package dev.jamii.api.fichiers;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Les octets dans la table `fichier_contenu` (V20261115_1730). */
@Component
class StockagePostgres implements StockageFichiers {

	private final JdbcClient base;

	StockagePostgres(JdbcClient base) {
		this.base = base;
	}

	@Override
	public void ecrire(UUID id, byte[] octets) {
		base.sql("INSERT INTO fichier_contenu (fichier_id, octets) VALUES (:id, :o)").param("id", id).param("o", octets).update();
	}

	@Override
	public Optional<byte[]> lire(UUID id) {
		return base.sql("SELECT octets FROM fichier_contenu WHERE fichier_id = :id").param("id", id).query(byte[].class).optional();
	}

}
