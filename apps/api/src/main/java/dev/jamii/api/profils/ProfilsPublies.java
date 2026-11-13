package dev.jamii.api.profils;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Le service public des profils pour les autres modules (le réseau, contracts/reseau.yaml) : seulement les profils
 * **publiés**, jamais l'e-mail, jamais un profil privé. Le nom et l'avatar viennent de l'annuaire (module auth).
 */
@Service
public class ProfilsPublies {

	/** Ce qu'un profil publié montre pour une recommandation. */
	public record Resume(UUID utilisateurId, String pays, String ville, String disponibilite, Integer heuresParSemaine,
			List<String> competences) {
	}

	/** Au-delà, on ne parcourt pas : les recommandations n'ont besoin que des meilleurs candidats. */
	static final int MAX_CANDIDATS = 200;

	private final NamedParameterJdbcTemplate jdbc;

	private final RechercheProfils recherche;

	ProfilsPublies(NamedParameterJdbcTemplate jdbc, RechercheProfils recherche) {
		this.jdbc = jdbc;
		this.recherche = recherche;
	}

	/** Mon profil, s'il est publié. */
	public Optional<Resume> publie(UUID utilisateurId) {
		return lire("p.utilisateur_id = :id", new MapSqlParameterSource("id", utilisateurId), 1).stream().findFirst();
	}

	/** Les profils publiés et disponibles, sauf ceux donnés (les membres du cercle), les plus récents d'abord. */
	public List<Resume> disponibles(Collection<UUID> sauf) {
		var params = new MapSqlParameterSource("sauf", sauf.isEmpty() ? List.of(new UUID(0, 0)) : sauf);
		return lire("p.disponibilite = 'disponible' AND p.utilisateur_id NOT IN (:sauf)", params, MAX_CANDIDATS);
	}

	/** Les compétences des profils publiés parmi ces personnes (un profil privé n'apparaît pas). */
	public Map<UUID, List<String>> competences(Collection<UUID> ids) {
		List<UUID> publies = ids.isEmpty() ? List.of()
				: jdbc.queryForList("SELECT utilisateur_id FROM profil WHERE publie AND utilisateur_id IN (:ids)",
						new MapSqlParameterSource("ids", ids), UUID.class);
		return recherche.competences(publies);
	}

	private List<Resume> lire(String condition, MapSqlParameterSource params, int limite) {
		record Ligne(UUID id, String pays, String ville, String disponibilite, Integer heures) {
		}
		List<Ligne> lignes = jdbc.query("""
				SELECT p.utilisateur_id, p.pays, p.ville, p.disponibilite, p.heures_par_semaine FROM profil p
				WHERE p.publie AND %s ORDER BY p.mis_a_jour_le DESC, p.utilisateur_id LIMIT :limite
				""".formatted(condition), params.addValue("limite", limite),
				(l, i) -> new Ligne(l.getObject("utilisateur_id", UUID.class), l.getString("pays"), l.getString("ville"),
						l.getString("disponibilite"), (Integer) l.getObject("heures_par_semaine")));
		Map<UUID, List<String>> competences = recherche.competences(lignes.stream().map(Ligne::id).toList());
		return lignes.stream()
			.map(l -> new Resume(l.id(), l.pays(), l.ville(), l.disponibilite(), l.heures(),
					competences.getOrDefault(l.id(), List.of())))
			.toList();
	}

}
