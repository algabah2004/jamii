package dev.jamii.api.projets;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * La liste des projets (contracts/projets.yaml, `listerProjets`) : tout utilisateur connecté voit tous les projets,
 * les plus récents d'abord. Deux requêtes pour une page entière (les projets, puis leurs rôles ouverts), jamais une
 * par projet.
 */
@Service
class ListeProjets {

	record Ligne(UUID id, String titre, String origine, String statut, UUID porteurId, int nombreMembres,
			List<String> competencesRecherchees, Instant creeLe) {
	}

	record Page(List<Ligne> elements, String curseurSuivant) {
	}

	private final JdbcClient base;

	ListeProjets(JdbcClient base) {
		this.base = base;
	}

	/**
	 * @param statut un statut de projet, ou null pour tous
	 * @param competence une compétence déjà normalisée : seuls les projets qui la recrutent (rôle ouvert), ou null
	 * @param seulementLesMiens vrai : seulement les projets dont `moi` est membre de l'équipe actuelle
	 */
	@Transactional(readOnly = true)
	Page lister(UUID moi, String statut, String competence, boolean seulementLesMiens, int limite, String curseur) {
		Instant avant = null;
		UUID avantId = null;
		if (curseur != null) {
			try {
				String[] morceaux = new String(Base64.getUrlDecoder().decode(curseur)).split("\\|");
				avant = Instant.parse(morceaux[0]);
				avantId = UUID.fromString(morceaux[1]);
			}
			catch (RuntimeException e) {
				throw ErreurProjets.validation("curseur", "format_invalide");
			}
		}
		StringBuilder filtres = new StringBuilder("TRUE");
		if (statut != null) {
			filtres.append(" AND p.statut = :statut");
		}
		if (competence != null) {
			filtres.append(" AND EXISTS (SELECT 1 FROM role_recherche r WHERE r.projet_id = p.id AND r.statut = 'ouvert' AND :competence = ANY (r.competences))");
		}
		if (seulementLesMiens) {
			filtres.append(" AND EXISTS (SELECT 1 FROM membre_equipe m WHERE m.projet_id = p.id AND m.utilisateur_id = :moi AND m.parti_le IS NULL)");
		}
		if (avant != null) {
			filtres.append(" AND (p.cree_le, p.id) < (:avant, :avantId)");
		}
		var requete = base.sql("""
				SELECT p.id, p.titre, p.origine, p.statut, p.porteur_id, p.cree_le,
				       (SELECT count(*) FROM membre_equipe m WHERE m.projet_id = p.id AND m.parti_le IS NULL) AS nombre_membres
				FROM projet p
				WHERE %s
				ORDER BY p.cree_le DESC, p.id DESC
				LIMIT :limite
				""".formatted(filtres))
			.param("limite", limite + 1);
		if (statut != null) {
			requete = requete.param("statut", statut);
		}
		if (competence != null) {
			requete = requete.param("competence", competence);
		}
		if (seulementLesMiens) {
			requete = requete.param("moi", moi);
		}
		if (avant != null) {
			requete = requete.param("avant", Timestamp.from(avant)).param("avantId", avantId);
		}
		List<Ligne> lignes = requete.query((rs, n) -> new Ligne(rs.getObject("id", UUID.class), rs.getString("titre"),
				rs.getString("origine"), rs.getString("statut"), rs.getObject("porteur_id", UUID.class),
				rs.getInt("nombre_membres"), List.of(), rs.getTimestamp("cree_le").toInstant())).list();
		boolean suite = lignes.size() > limite;
		List<Ligne> page = new ArrayList<>(suite ? lignes.subList(0, limite) : lignes);
		page = avecCompetences(page);
		String suivant = null;
		if (suite) {
			Ligne derniere = page.getLast();
			suivant = Base64.getUrlEncoder().withoutPadding().encodeToString((derniere.creeLe() + "|" + derniere.id()).getBytes());
		}
		return new Page(page, suivant);
	}

	/** Les compétences des rôles encore ouverts de chaque projet de la page, sans doublon, en une seule requête. */
	private List<Ligne> avecCompetences(List<Ligne> page) {
		if (page.isEmpty()) {
			return page;
		}
		Map<UUID, TreeSet<String>> parProjet = new HashMap<>();
		base.sql("""
				SELECT r.projet_id, c.competence
				FROM role_recherche r CROSS JOIN LATERAL unnest(r.competences) AS c(competence)
				WHERE r.statut = 'ouvert' AND r.projet_id IN (:ids)
				""")
			.param("ids", page.stream().map(Ligne::id).toList())
			.query((rs, n) -> parProjet.computeIfAbsent(rs.getObject("projet_id", UUID.class), k -> new TreeSet<>())
				.add(rs.getString("competence")))
			.list();
		return page.stream()
			.map(l -> new Ligne(l.id(), l.titre(), l.origine(), l.statut(), l.porteurId(), l.nombreMembres(),
					List.copyOf(parProjet.getOrDefault(l.id(), new TreeSet<>())), l.creeLe()))
			.toList();
	}

}
