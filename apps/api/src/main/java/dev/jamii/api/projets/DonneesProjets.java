package dev.jamii.api.projets;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import dev.jamii.api.auth.DonneesPersonnelles;

/**
 * Les projets d'un compte dans l'export, et leur transmission quand il est supprimé : le porteur passe au membre
 * actif arrivé le plus tôt. Un projet sans autre membre actif n'a plus personne pour le porter : il est supprimé.
 */
@Component
class DonneesProjets implements DonneesPersonnelles {

	record Equipe(UUID projetId, String titre, String statut, boolean porteur, String role, Instant rejointLe,
			Instant partiLe) {
	}

	record MaCandidature(UUID id, UUID projetId, String role, String message, String statut, Instant creeLe,
			Instant decideeLe) {
	}

	record Section(List<Equipe> equipes, List<MaCandidature> candidatures) {
	}

	private final JdbcClient base;

	DonneesProjets(JdbcClient base) {
		this.base = base;
	}

	@Override
	public String section() {
		return "projets";
	}

	@Override
	public Section exporter(UUID utilisateurId) {
		List<Equipe> equipes = base.sql("""
				SELECT p.id, p.titre, p.statut, p.porteur_id = :u AS porteur, r.titre AS role, m.rejoint_le, m.parti_le
				FROM membre_equipe m
				JOIN projet p ON p.id = m.projet_id
				LEFT JOIN role_recherche r ON r.id = m.role_recherche_id
				WHERE m.utilisateur_id = :u
				ORDER BY m.rejoint_le, p.id
				""")
			.param("u", utilisateurId)
			.query((rs, n) -> new Equipe(rs.getObject("id", UUID.class), rs.getString("titre"), rs.getString("statut"),
					rs.getBoolean("porteur"), rs.getString("role"), instant(rs, "rejoint_le"), instant(rs, "parti_le")))
			.list();
		List<MaCandidature> candidatures = base.sql("""
				SELECT c.id, r.projet_id, r.titre AS role, c.message, c.statut, c.cree_le, c.decidee_le
				FROM candidature c JOIN role_recherche r ON r.id = c.role_recherche_id
				WHERE c.candidat_id = :u
				ORDER BY c.cree_le, c.id
				""")
			.param("u", utilisateurId)
			.query((rs, n) -> new MaCandidature(rs.getObject("id", UUID.class), rs.getObject("projet_id", UUID.class),
					rs.getString("role"), rs.getString("message"), rs.getString("statut"), instant(rs, "cree_le"),
					instant(rs, "decidee_le")))
			.list();
		return new Section(equipes, candidatures);
	}

	@Override
	public void avantSuppression(UUID utilisateurId, Instant maintenant) {
		// Verrouille ses projets : personne n'accepte une candidature pendant la transmission.
		List<UUID> portes = base.sql("SELECT id FROM projet WHERE porteur_id = :u ORDER BY id FOR UPDATE")
			.param("u", utilisateurId)
			.query(UUID.class)
			.list();
		for (UUID projet : portes) {
			Optional<UUID> successeur = base.sql("""
					SELECT utilisateur_id FROM membre_equipe
					WHERE projet_id = :p AND utilisateur_id <> :u AND parti_le IS NULL
					ORDER BY rejoint_le, utilisateur_id
					LIMIT 1
					""")
				.param("p", projet).param("u", utilisateurId)
				.query(UUID.class)
				.optional();
			if (successeur.isPresent()) {
				base.sql("""
						UPDATE projet SET porteur_id = :s, mis_a_jour_le = :t, version = version + 1 WHERE id = :p
						""")
					.param("s", successeur.get()).param("t", Timestamp.from(maintenant)).param("p", projet).update();
			}
			else {
				// Un projet qui le reprenait garde son histoire, sans lien vers celui qui disparaît.
				base.sql("UPDATE projet SET projet_repris_id = NULL WHERE projet_repris_id = :p").param("p", projet).update();
				base.sql("DELETE FROM projet WHERE id = :p").param("p", projet).update();
			}
		}
	}

	private static Instant instant(ResultSet rs, String colonne) throws SQLException {
		Timestamp t = rs.getTimestamp(colonne);
		return t == null ? null : t.toInstant();
	}

}
