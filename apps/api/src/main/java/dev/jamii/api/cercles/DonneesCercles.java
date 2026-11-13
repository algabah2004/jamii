package dev.jamii.api.cercles;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import dev.jamii.api.auth.DonneesPersonnelles;

/**
 * Les cercles d'un compte dans l'export, et leur transmission quand il est supprimé : le cercle passe au membre
 * arrivé le plus tôt. Un cercle où il était seul est supprimé. Son appartenance (idée, rang, dépôt) part en cascade.
 */
@Component
class DonneesCercles implements DonneesPersonnelles {

	record MonCercle(UUID cercleId, String nom, String ville, String statut, boolean createur, Instant rejointLe,
			String ideeTitre, String ideeDescription, Integer rang, UUID projetId, Instant livreLe, String depotUrl) {
	}

	private final JdbcClient base;

	DonneesCercles(JdbcClient base) {
		this.base = base;
	}

	@Override
	public String section() {
		return "cercles";
	}

	@Override
	public List<MonCercle> exporter(UUID utilisateurId) {
		return base.sql("""
				SELECT c.id, c.nom, c.ville, c.statut, c.createur_id = :u AS createur, m.rejoint_le, m.idee_titre,
				       m.idee_description, m.rang, m.projet_id, m.livre_le, m.depot_url
				FROM cercle_membre m JOIN cercle c ON c.id = m.cercle_id
				WHERE m.utilisateur_id = :u
				ORDER BY m.rejoint_le, c.id
				""")
			.param("u", utilisateurId)
			.query((rs, n) -> {
				Timestamp livre = rs.getTimestamp("livre_le");
				int lu = rs.getInt("rang");
				Integer rang = rs.wasNull() ? null : lu;
				return new MonCercle(rs.getObject("id", UUID.class), rs.getString("nom"), rs.getString("ville"),
						rs.getString("statut"), rs.getBoolean("createur"), rs.getTimestamp("rejoint_le").toInstant(),
						rs.getString("idee_titre"), rs.getString("idee_description"), rang,
						rs.getObject("projet_id", UUID.class), livre == null ? null : livre.toInstant(), rs.getString("depot_url"));
			})
			.list();
	}

	@Override
	public void avantSuppression(UUID utilisateurId, Instant maintenant) {
		// Même ordre de verrou que GestionCercles : la ligne du cercle d'abord.
		List<UUID> crees = base.sql("SELECT id FROM cercle WHERE createur_id = :u ORDER BY id FOR UPDATE")
			.param("u", utilisateurId)
			.query(UUID.class)
			.list();
		for (UUID cercle : crees) {
			Optional<UUID> successeur = base.sql("""
					SELECT utilisateur_id FROM cercle_membre
					WHERE cercle_id = :c AND utilisateur_id <> :u
					ORDER BY rejoint_le, utilisateur_id
					LIMIT 1
					""")
				.param("c", cercle).param("u", utilisateurId)
				.query(UUID.class)
				.optional();
			if (successeur.isPresent()) {
				base.sql("UPDATE cercle SET createur_id = :s, version = version + 1 WHERE id = :c")
					.param("s", successeur.get()).param("c", cercle).update();
			}
			else {
				base.sql("DELETE FROM cercle WHERE id = :c").param("c", cercle).update();
			}
		}
	}

}
