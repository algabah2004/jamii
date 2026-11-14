package dev.jamii.api.demandes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import dev.jamii.api.auth.DonneesPersonnelles;

/**
 * Les demandes d'un compte dans l'export. À la suppression, elles partent en cascade avec le compte ; un projet déjà
 * lancé depuis l'une d'elles appartient à son équipe et reste.
 */
@Component
class DonneesDemandes implements DonneesPersonnelles {

	record MaDemande(UUID id, String titre, String description, Integer dureeSemaines, Instant creeLe,
			List<String> precisions, Publication publication) {
	}

	/** Le résumé que la communauté voit, si la demande est publiée. */
	record Publication(Instant publieeLe, String resume) {
	}

	private final JdbcClient base;

	DonneesDemandes(JdbcClient base) {
		this.base = base;
	}

	@Override
	public String section() {
		return "demandes";
	}

	@Override
	public List<MaDemande> exporter(UUID utilisateurId) {
		return base.sql("""
				SELECT id, titre, description, duree_semaines, cree_le, publiee_le, publiee_resume,
				  ARRAY(SELECT texte FROM demande_precision p WHERE p.demande_id = demande.id ORDER BY rang) AS precisions
				FROM demande WHERE auteur_id = :u ORDER BY cree_le, id
				""")
			.param("u", utilisateurId)
			.query((rs, n) -> {
				int lue = rs.getInt("duree_semaines");
				Integer duree = rs.wasNull() ? null : lue;
				return new MaDemande(rs.getObject("id", UUID.class), rs.getString("titre"), rs.getString("description"),
						duree, rs.getTimestamp("cree_le").toInstant(),
						List.of((String[]) rs.getArray("precisions").getArray()),
						rs.getTimestamp("publiee_le") == null ? null
								: new Publication(rs.getTimestamp("publiee_le").toInstant(), rs.getString("publiee_resume")));
			})
			.list();
	}

}
