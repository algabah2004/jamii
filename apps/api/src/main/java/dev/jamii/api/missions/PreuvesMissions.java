package dev.jamii.api.missions;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import dev.jamii.api.profils.SourcePreuves;

/**
 * Ce que les missions prouvent (profils.SourcePreuves) : les avis positifs (4 ou 5 sur 5) laissés par le client d'une
 * mission terminée au développeur qui l'a réalisée. Ils comptent comme `avis_coequipier` : quelqu'un qui a travaillé
 * avec la personne dit, par écrit, que ça s'est bien passé.
 */
@Component
class PreuvesMissions implements SourcePreuves {

	static final int NOTE_POSITIVE = 4;

	private static final String AVIS = """
			FROM avis_mission a JOIN mission m ON m.id = a.mission_id
			WHERE a.auteur = 'client' AND m.developpeur_id = :u AND m.statut = 'terminee' AND a.note >= :note
			""";

	private final JdbcClient base;

	PreuvesMissions(JdbcClient base) {
		this.base = base;
	}

	@Override
	public List<Point> points(UUID personne) {
		int n = base.sql("SELECT count(*) " + AVIS).param("u", personne).param("note", NOTE_POSITIVE).query(Integer.class).single();
		return List.of(new Point(AVIS_COEQUIPIER, n, null));
	}

	@Override
	public List<Preuve> preuves(UUID personne, String type, int limite) {
		if (!AVIS_COEQUIPIER.equals(type)) {
			return List.of();
		}
		return base.sql("SELECT m.id, m.titre, a.texte, a.auteur_id, a.donne_le " + AVIS + " ORDER BY a.donne_le DESC LIMIT :n")
			.param("u", personne).param("note", NOTE_POSITIVE).param("n", limite)
			.query((rs, i) -> new Preuve(rs.getObject("id", UUID.class), type, "missions", rs.getObject("id", UUID.class),
					rs.getString("titre"), rs.getString("texte"), rs.getObject("auteur_id", UUID.class), rs.getTimestamp("donne_le").toInstant()))
			.list();
	}

}
