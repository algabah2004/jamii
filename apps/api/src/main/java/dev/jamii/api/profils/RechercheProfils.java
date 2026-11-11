package dev.jamii.api.profils;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Recherche de coéquipiers parmi les profils publiés, en SQL sur les seules tables du module.
 * Tri : disponibles d'abord, puis les plus récemment mis à jour. Pagination par curseur (api-conventions.md §4) :
 * le curseur est la clé de tri du dernier élément, encodée en base64 (opaque pour le front).
 */
@Component
class RechercheProfils {

	record Criteres(List<String> competences, List<String> pays, String disponibilite, UUID sauf, int limite,
			Curseur apres) {
	}

	record Curseur(int rang, Instant misAJourLe, UUID id) {

		String encoder() {
			String brut = rang + "|" + misAJourLe + "|" + id;
			return Base64.getUrlEncoder().withoutPadding().encodeToString(brut.getBytes(StandardCharsets.UTF_8));
		}

		static Curseur decoder(String texte) {
			try {
				String[] morceaux = new String(Base64.getUrlDecoder().decode(texte), StandardCharsets.UTF_8).split("\\|");
				return new Curseur(Integer.parseInt(morceaux[0]), Instant.parse(morceaux[1]), UUID.fromString(morceaux[2]));
			}
			catch (RuntimeException e) {
				throw ErreurProfil.validation(List.of(new ErreurProfil.Champ("curseur", "format_invalide")));
			}
		}

	}

	record Ligne(UUID id, int rang, Instant misAJourLe, String pays, String ville, String disponibilite,
			Integer heuresParSemaine) {
	}

	private static final String RANG = "CASE WHEN p.disponibilite = 'disponible' THEN 0 ELSE 1 END";

	private final NamedParameterJdbcTemplate jdbc;

	RechercheProfils(NamedParameterJdbcTemplate jdbc) {
		this.jdbc = jdbc;
	}

	/** Une ligne de plus que la limite, pour savoir s'il existe une page suivante. */
	List<Ligne> chercher(Criteres c) {
		var sql = new StringBuilder("""
				SELECT p.utilisateur_id, %s AS rang, p.mis_a_jour_le, p.pays, p.ville, p.disponibilite, p.heures_par_semaine
				FROM profil p
				WHERE p.publie AND p.utilisateur_id <> :sauf
				""".formatted(RANG));
		var params = new MapSqlParameterSource("sauf", c.sauf()).addValue("limite", c.limite() + 1);
		if (!c.competences().isEmpty()) {
			// Le profil doit avoir TOUTES les compétences demandées.
			sql.append("""
					AND p.utilisateur_id IN (SELECT utilisateur_id FROM competence_profil WHERE competence IN (:competences)
					  GROUP BY utilisateur_id HAVING count(DISTINCT competence) = :nombre)
					""");
			params.addValue("competences", c.competences()).addValue("nombre", c.competences().size());
		}
		if (!c.pays().isEmpty()) {
			sql.append(" AND p.pays IN (:pays)");
			params.addValue("pays", c.pays());
		}
		if (c.disponibilite() != null) {
			sql.append(" AND p.disponibilite = :disponibilite");
			params.addValue("disponibilite", c.disponibilite());
		}
		if (c.apres() != null) {
			sql.append("""
					 AND (%1$s > :rang OR (%1$s = :rang AND (p.mis_a_jour_le < :date
					   OR (p.mis_a_jour_le = :date AND p.utilisateur_id > :id))))
					""".formatted(RANG));
			params.addValue("rang", c.apres().rang())
				.addValue("date", Timestamp.from(c.apres().misAJourLe()))
				.addValue("id", c.apres().id());
		}
		sql.append(" ORDER BY rang, p.mis_a_jour_le DESC, p.utilisateur_id LIMIT :limite");
		return jdbc.query(sql.toString(), params,
				(l, i) -> new Ligne(l.getObject("utilisateur_id", UUID.class), l.getInt("rang"),
						l.getTimestamp("mis_a_jour_le").toInstant(), l.getString("pays"), l.getString("ville"),
						l.getString("disponibilite"), (Integer) l.getObject("heures_par_semaine")));
	}

	/** Les 10 premières compétences de chaque profil de la page, celles de GitHub d'abord. */
	Map<UUID, List<String>> competences(List<UUID> ids) {
		Map<UUID, List<String>> parProfil = new LinkedHashMap<>();
		if (ids.isEmpty()) {
			return parProfil;
		}
		jdbc.query("""
				SELECT utilisateur_id, competence FROM (
				  SELECT utilisateur_id, competence, min(CASE WHEN source = 'github' THEN 0 ELSE 1 END) AS ordre
				  FROM competence_profil WHERE utilisateur_id IN (:ids) GROUP BY utilisateur_id, competence) c
				ORDER BY utilisateur_id, ordre, competence
				""", new MapSqlParameterSource("ids", ids), ligne -> {
			List<String> liste = parProfil.computeIfAbsent(ligne.getObject("utilisateur_id", UUID.class),
					id -> new ArrayList<>());
			if (liste.size() < 10) {
				liste.add(ligne.getString("competence"));
			}
		});
		return parProfil;
	}

}
