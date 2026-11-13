package dev.jamii.api.projets;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Les jalons et les tâches d'un projet (contracts/projets.yaml, tag « equipe »). Réservés à l'équipe actuelle :
 * un projet inconnu répond 404, et un projet dont on n'est pas membre répond 403 (son existence, elle,
 * est publique dans la liste des projets).
 */
@Service
class TachesEtJalons {

	record Jalon(UUID id, String titre, LocalDate echeance, String statut, int ordre) {
	}

	record Tache(UUID id, String titre, UUID jalonId, UUID assigneeId, String statut, LocalDate echeance, Instant livreeLe,
			boolean proposeeParIa, int version, Instant creeLe) {
	}

	record Page(List<Tache> elements, String curseurSuivant) {
	}

	/**
	 * Une fusion JSON déjà validée. Pour les champs qui acceptent `null` (jalon, assignee, échéance), le booléen dit
	 * si le champ était présent : absent, il ne change pas ; présent à null, il est vidé.
	 */
	record Modification(String titre, boolean changerJalon, UUID jalonId, boolean changerAssignee, UUID assigneeId,
			boolean changerEcheance, LocalDate echeance, String statut, int version) {
	}

	private static final String COLONNES_TACHE = "id, titre, jalon_id, assignee_id, statut, echeance, livree_le, proposee_par_ia, version, cree_le";

	private final JdbcClient base;

	TachesEtJalons(JdbcClient base) {
		this.base = base;
	}

	/** Tous les jalons, dans l'ordre choisi par l'équipe (50 au plus, comme le contrat). */
	@Transactional(readOnly = true)
	List<Jalon> jalons(UUID projetId, UUID moi) {
		exigerMembre(projetId, moi);
		return base.sql("""
				SELECT id, titre, echeance, statut, ordre FROM jalon
				WHERE projet_id = :p ORDER BY ordre, cree_le, id LIMIT 50
				""")
			.param("p", projetId)
			.query((rs, n) -> new Jalon(rs.getObject("id", UUID.class), rs.getString("titre"), date(rs, "echeance"),
					rs.getString("statut"), rs.getInt("ordre")))
			.list();
	}

	/**
	 * Une page de tâches, dans l'ordre de création.
	 * @param statut un statut de tâche, ou null pour tous
	 * @param jalonId seulement les tâches de ce jalon, ou null
	 * @param seulementLesMiennes vrai : seulement les tâches assignées à `moi`
	 */
	@Transactional(readOnly = true)
	Page taches(UUID projetId, UUID moi, String statut, UUID jalonId, boolean seulementLesMiennes, int limite, String curseur) {
		exigerMembre(projetId, moi);
		Instant apres = null;
		UUID apresId = null;
		if (curseur != null) {
			try {
				String[] morceaux = new String(Base64.getUrlDecoder().decode(curseur)).split("\\|");
				apres = Instant.parse(morceaux[0]);
				apresId = UUID.fromString(morceaux[1]);
			}
			catch (RuntimeException e) {
				throw ErreurProjets.validation("curseur", "format_invalide");
			}
		}
		StringBuilder filtres = new StringBuilder("projet_id = :p");
		Map<String, Object> parametres = new HashMap<>();
		parametres.put("p", projetId);
		parametres.put("limite", limite + 1);
		if (statut != null) {
			filtres.append(" AND statut = :statut");
			parametres.put("statut", statut);
		}
		if (jalonId != null) {
			filtres.append(" AND jalon_id = :jalon");
			parametres.put("jalon", jalonId);
		}
		if (seulementLesMiennes) {
			filtres.append(" AND assignee_id = :moi");
			parametres.put("moi", moi);
		}
		if (apres != null) {
			filtres.append(" AND (cree_le, id) > (:apres, :apresId)");
			parametres.put("apres", Timestamp.from(apres));
			parametres.put("apresId", apresId);
		}
		List<Tache> lignes = base.sql("SELECT " + COLONNES_TACHE + " FROM tache WHERE %s ORDER BY cree_le, id LIMIT :limite"
				.formatted(filtres))
			.params(parametres)
			.query(TachesEtJalons::tache)
			.list();
		boolean suite = lignes.size() > limite;
		List<Tache> page = new ArrayList<>(suite ? lignes.subList(0, limite) : lignes);
		String suivant = null;
		if (suite) {
			Tache derniere = page.getLast();
			suivant = Base64.getUrlEncoder().withoutPadding().encodeToString((derniere.creeLe() + "|" + derniere.id()).getBytes());
		}
		return new Page(page, suivant);
	}

	/**
	 * Modifie une tâche, la prend ou la marque livrée. N'importe quel membre de l'équipe peut le faire. La ligne est
	 * verrouillée : deux modifications parties de la même version ne passent pas toutes les deux.
	 */
	@Transactional
	Tache modifier(UUID projetId, UUID tacheId, UUID moi, Modification m, Instant maintenant) {
		exigerMembre(projetId, moi);
		Tache actuelle = base.sql("SELECT " + COLONNES_TACHE + " FROM tache WHERE id = :t AND projet_id = :p FOR UPDATE")
			.param("t", tacheId).param("p", projetId)
			.query(TachesEtJalons::tache)
			.optional()
			.orElseThrow(ErreurProjets::introuvable);
		if (actuelle.version() != m.version()) {
			throw ErreurProjets.conflit("modification_concurrente", "La tâche a changé entre-temps, recharge-la");
		}
		UUID jalon = m.changerJalon() ? m.jalonId() : actuelle.jalonId();
		if (m.changerJalon() && jalon != null && !base.sql("SELECT EXISTS (SELECT 1 FROM jalon WHERE id = :j AND projet_id = :p)")
			.param("j", jalon).param("p", projetId).query(Boolean.class).single()) {
			throw ErreurProjets.validation("jalonId", "introuvable");
		}
		UUID assignee = m.changerAssignee() ? m.assigneeId() : actuelle.assigneeId();
		if (m.changerAssignee() && assignee != null && !estMembreActuel(projetId, assignee)) {
			throw ErreurProjets.conflit("pas_membre", "Cette personne ne fait pas partie de l'équipe");
		}
		String statut = m.statut() == null ? actuelle.statut() : m.statut();
		// Livrée une fois, la date ne bouge plus ; revenir en arrière l'efface.
		Instant livreeLe = !"livree".equals(statut) ? null : actuelle.livreeLe() != null ? actuelle.livreeLe() : maintenant;
		LocalDate echeance = m.changerEcheance() ? m.echeance() : actuelle.echeance();
		base.sql("""
				UPDATE tache SET titre = :titre, jalon_id = :jalon, assignee_id = :assignee, echeance = :echeance,
				                 statut = :statut, livree_le = :livree, version = version + 1
				WHERE id = :t
				""")
			.param("titre", m.titre() == null ? actuelle.titre() : m.titre())
			.param("jalon", jalon).param("assignee", assignee)
			.param("echeance", echeance == null ? null : Date.valueOf(echeance))
			.param("statut", statut).param("livree", livreeLe == null ? null : Timestamp.from(livreeLe))
			.param("t", tacheId)
			.update();
		Timestamp t = Timestamp.from(maintenant);
		base.sql("UPDATE projet SET derniere_activite_le = :t, mis_a_jour_le = :t WHERE id = :p").param("t", t).param("p", projetId).update();
		return base.sql("SELECT " + COLONNES_TACHE + " FROM tache WHERE id = :t").param("t", tacheId).query(TachesEtJalons::tache).single();
	}

	private boolean estMembreActuel(UUID projetId, UUID utilisateurId) {
		return base.sql("SELECT EXISTS (SELECT 1 FROM membre_equipe WHERE projet_id = :p AND utilisateur_id = :u AND parti_le IS NULL)")
			.param("p", projetId).param("u", utilisateurId).query(Boolean.class).single();
	}

	private static Tache tache(ResultSet rs, int n) throws SQLException {
		return new Tache(rs.getObject("id", UUID.class), rs.getString("titre"), rs.getObject("jalon_id", UUID.class),
				rs.getObject("assignee_id", UUID.class), rs.getString("statut"), date(rs, "echeance"), instant(rs, "livree_le"),
				rs.getBoolean("proposee_par_ia"), rs.getInt("version"), instant(rs, "cree_le"));
	}

	private void exigerMembre(UUID projetId, UUID moi) {
		Boolean membre = base.sql("""
				SELECT EXISTS (SELECT 1 FROM membre_equipe m WHERE m.projet_id = p.id AND m.utilisateur_id = :moi AND m.parti_le IS NULL)
				FROM projet p WHERE p.id = :p
				""")
			.param("p", projetId).param("moi", moi)
			.query(Boolean.class)
			.optional()
			.orElseThrow(ErreurProjets::introuvable);
		if (!membre) {
			throw ErreurProjets.interdit();
		}
	}

	private static LocalDate date(ResultSet rs, String colonne) throws SQLException {
		Date d = rs.getDate(colonne);
		return d == null ? null : d.toLocalDate();
	}

	private static Instant instant(ResultSet rs, String colonne) throws SQLException {
		Timestamp t = rs.getTimestamp(colonne);
		return t == null ? null : t.toInstant();
	}

}
