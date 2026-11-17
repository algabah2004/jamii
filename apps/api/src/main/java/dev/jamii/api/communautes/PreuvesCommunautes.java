package dev.jamii.api.communautes;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import dev.jamii.api.profils.SourcePreuves;

/**
 * Ce que les communautés prouvent (profils.SourcePreuves) : les réponses d'une personne acceptées par l'auteur de la
 * question. Accepter sa propre réponse ne compte pas, et une réponse proposée par l'IA n'appartient à personne.
 */
@Component
class PreuvesCommunautes implements SourcePreuves {

	private static final String ACCEPTEES = """
			FROM reponse r JOIN publication p ON p.reponse_acceptee_id = r.id
			WHERE r.auteur_id = :u AND NOT r.proposee_par_ia AND p.auteur_id IS DISTINCT FROM r.auteur_id
			""";

	private final JdbcClient base;

	PreuvesCommunautes(JdbcClient base) {
		this.base = base;
	}

	@Override
	public List<Point> points(UUID personne) {
		int n = base.sql("SELECT count(*) " + ACCEPTEES).param("u", personne).query(Integer.class).single();
		return List.of(new Point(REPONSE_ACCEPTEE, n, null));
	}

	@Override
	public List<Preuve> preuves(UUID personne, String type, int limite) {
		if (!REPONSE_ACCEPTEE.equals(type)) {
			return List.of();
		}
		return base.sql("SELECT r.id, p.id AS publication, p.titre, p.auteur_id, p.acceptee_le " + ACCEPTEES + " ORDER BY p.acceptee_le DESC LIMIT :n")
			.param("u", personne).param("n", limite)
			.query((rs, i) -> new Preuve(rs.getObject("id", UUID.class), type, "communautes", rs.getObject("publication", UUID.class),
					rs.getString("titre"), null, rs.getObject("auteur_id", UUID.class),
					rs.getTimestamp("acceptee_le") == null ? null : rs.getTimestamp("acceptee_le").toInstant()))
			.list();
	}

}
