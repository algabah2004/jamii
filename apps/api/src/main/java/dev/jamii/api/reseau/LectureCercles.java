package dev.jamii.api.reseau;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Ce que le réseau lit des cercles, en lecture seule : les cercles ouverts et leurs membres.
 * Le module cercles n'expose pas encore de service public de lecture ; on lit donc ses deux tables
 * (`cercle`, `cercle_membre`, migration V10) sans jamais y écrire. Rejoindre passe toujours par l'API des cercles.
 */
@Component
class LectureCercles {

	/** Un cercle et l'identifiant de ses membres, dans l'ordre d'arrivée. */
	record CercleLu(UUID id, String nom, String ville, int taille, String statut, UUID createurId, Instant creeLe,
			List<UUID> membres) {

		boolean accueille() {
			return "ouvert".equals(statut) && membres.size() < taille;
		}

	}

	/** On ne parcourt que les plus récents : un cercle ouvert depuis longtemps a moins de chances de se remplir. */
	static final int MAX_CERCLES = 100;

	private final JdbcClient base;

	LectureCercles(JdbcClient base) {
		this.base = base;
	}

	/** Les cercles qui accueillent encore des membres et dont je ne fais pas partie, les plus récents d'abord. */
	List<CercleLu> ouvertsSans(UUID moi) {
		List<UUID> ids = base.sql("""
				SELECT c.id FROM cercle c
				WHERE c.statut = 'ouvert'
				  AND NOT EXISTS (SELECT 1 FROM cercle_membre m WHERE m.cercle_id = c.id AND m.utilisateur_id = :moi)
				  AND (SELECT count(*) FROM cercle_membre m WHERE m.cercle_id = c.id) < c.taille
				ORDER BY c.cree_le DESC, c.id DESC LIMIT :limite
				""").param("moi", moi).param("limite", MAX_CERCLES).query(UUID.class).list();
		return lire(ids).values().stream().toList();
	}

	Optional<CercleLu> lire(UUID id) {
		return Optional.ofNullable(lire(List.of(id)).get(id));
	}

	/** Les cercles demandés, dans l'ordre donné ; ceux qui n'existent plus sont absents. */
	Map<UUID, CercleLu> lire(Collection<UUID> ids) {
		Map<UUID, CercleLu> parId = new LinkedHashMap<>();
		if (ids.isEmpty()) {
			return parId;
		}
		Map<UUID, List<UUID>> membres = new LinkedHashMap<>();
		base.sql("SELECT cercle_id, utilisateur_id FROM cercle_membre WHERE cercle_id IN (:ids) ORDER BY rejoint_le, utilisateur_id")
			.param("ids", ids)
			.query((l, i) -> membres.computeIfAbsent(l.getObject("cercle_id", UUID.class), c -> new ArrayList<>())
				.add(l.getObject("utilisateur_id", UUID.class)))
			.list();
		Map<UUID, CercleLu> lus = new LinkedHashMap<>();
		base.sql("SELECT id, nom, ville, taille, statut, createur_id, cree_le FROM cercle WHERE id IN (:ids)")
			.param("ids", ids)
			.query((l, i) -> {
				UUID id = l.getObject("id", UUID.class);
				return new CercleLu(id, l.getString("nom"), l.getString("ville"), l.getInt("taille"), l.getString("statut"),
						l.getObject("createur_id", UUID.class), l.getTimestamp("cree_le").toInstant(),
						List.copyOf(membres.getOrDefault(id, List.of())));
			})
			.list()
			.forEach(c -> lus.put(c.id(), c));
		ids.forEach(id -> {
			if (lus.containsKey(id)) {
				parId.put(id, lus.get(id));
			}
		});
		return parId;
	}

}
