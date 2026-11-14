package dev.jamii.api.cercles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jamii.api.systalink.ChiffresDesModules;

/**
 * Les chiffres des cercles pour l'espace Systalink (#117) : le tableau de bord ne lit jamais les tables `cercle` et
 * `cercle_membre`, il passe par ici. Les identifiants des membres ne servent qu'à les compter par pays.
 */
@Service
class ChiffresCercles implements ChiffresDesModules.Cercles {

	private final JdbcClient base;

	ChiffresCercles(JdbcClient base) {
		this.base = base;
	}

	@Override
	@Transactional(readOnly = true)
	public long actifs() {
		return base.sql("SELECT count(*) FROM cercle WHERE statut IN ('ouvert', 'en_cours')").query(Long.class).single();
	}

	@Override
	@Transactional(readOnly = true)
	public long termines() {
		return base.sql("SELECT count(*) FROM cercle WHERE statut = 'termine'").query(Long.class).single();
	}

	/** Un tour livré, c'est un membre dont le tour a une date de livraison. */
	@Override
	@Transactional(readOnly = true)
	public long toursLivres(Instant depuis) {
		return base.sql("SELECT count(*) FROM cercle_membre WHERE livre_le IS NOT NULL AND livre_le >= :depuis")
			// « Depuis le début » : une date plus ancienne que toute livraison possible.
			.param("depuis", Timestamp.from(depuis == null ? Instant.EPOCH : depuis))
			.query(Long.class)
			.single();
	}

	@Override
	@Transactional(readOnly = true)
	public Set<UUID> membres() {
		return new HashSet<>(base.sql("SELECT DISTINCT utilisateur_id FROM cercle_membre").query(UUID.class).list());
	}

}
