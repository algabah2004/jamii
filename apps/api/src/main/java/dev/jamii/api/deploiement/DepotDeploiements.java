package dev.jamii.api.deploiement;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Le seul endroit qui lit et écrit la table `deploiement`. */
@Repository
class DepotDeploiements {

	/** Au-delà, sans réponse, une adresse passe en `echec` et sort de la vitrine. */
	static final String FRAICHEUR = "24 hours";

	private final JdbcClient base;

	DepotDeploiements(JdbcClient base) {
		this.base = base;
	}

	void inserer(UUID id, UUID projetId, String url, Hebergeur hebergeur, Instant maintenant) {
		base.sql("""
				INSERT INTO deploiement (id, projet_id, url, hebergeur, statut, cree_le)
				VALUES (:id, :projetId, :url, :hebergeur, 'en_cours', :maintenant)""")
			.param("id", id)
			.param("projetId", projetId)
			.param("url", url)
			.param("hebergeur", hebergeur.code())
			.param("maintenant", utc(maintenant))
			.update();
	}

	boolean existe(UUID projetId, String url) {
		return base.sql("SELECT count(*) FROM deploiement WHERE projet_id = :projetId AND url = :url")
			.param("projetId", projetId)
			.param("url", url)
			.query(Long.class)
			.single() > 0;
	}

	Optional<Deploiement> lire(UUID projetId, UUID id) {
		return base.sql("SELECT * FROM deploiement WHERE id = :id AND projet_id = :projetId")
			.param("id", id)
			.param("projetId", projetId)
			.query(DepotDeploiements::versDeploiement)
			.optional();
	}

	/** Une page, les plus récents d'abord. `apres` : (cree_le, id) du dernier élément de la page précédente. */
	List<Deploiement> page(UUID projetId, Instant apresLe, UUID apresId, int limite) {
		return base.sql("""
				SELECT * FROM deploiement
				WHERE projet_id = :projetId
				  AND (CAST(:apresLe AS timestamptz) IS NULL OR (cree_le, id) < (CAST(:apresLe AS timestamptz), CAST(:apresId AS uuid)))
				ORDER BY cree_le DESC, id DESC
				LIMIT :limite""")
			.param("projetId", projetId)
			.param("apresLe", apresLe == null ? null : utc(apresLe))
			.param("apresId", apresId)
			.param("limite", limite)
			.query(DepotDeploiements::versDeploiement)
			.list();
	}

	boolean supprimer(UUID projetId, UUID id) {
		return base.sql("DELETE FROM deploiement WHERE id = :id AND projet_id = :projetId")
			.param("id", id)
			.param("projetId", projetId)
			.update() == 1;
	}

	/** Les adresses à vérifier, les moins récemment essayées d'abord. */
	List<Deploiement> aVerifier(int limite) {
		return base.sql("SELECT * FROM deploiement ORDER BY dernier_essai_le NULLS FIRST LIMIT :limite")
			.param("limite", limite)
			.query(DepotDeploiements::versDeploiement)
			.list();
	}

	/**
	 * Enregistre un essai et met le statut à jour. Renvoie vrai si c'est le tout premier passage `en_ligne` du projet
	 * (pour publier l'événement une seule fois).
	 */
	boolean enregistrerEssai(Deploiement d, Verificateur.Resultat resultat, Instant maintenant) {
		if (resultat == Verificateur.Resultat.REPOND) {
			boolean premier = base.sql("""
					SELECT count(*) FROM deploiement WHERE projet_id = :projetId AND mis_en_ligne_le IS NOT NULL""")
				.param("projetId", d.projetId())
				.query(Long.class)
				.single() == 0;
			base.sql("""
					UPDATE deploiement SET statut = 'en_ligne', verifie_le = :maintenant,
					       mis_en_ligne_le = COALESCE(mis_en_ligne_le, :maintenant),
					       dernier_essai_le = :maintenant, dernier_resultat = 'repond'
					WHERE id = :id""")
				.param("id", d.id())
				.param("maintenant", utc(maintenant))
				.update();
			return premier;
		}
		base.sql("""
				UPDATE deploiement SET dernier_essai_le = :maintenant, dernier_resultat = :resultat,
				       statut = CASE WHEN COALESCE(verifie_le, cree_le) < CAST(:maintenant AS timestamptz) - CAST(:fraicheur AS interval)
				                     THEN 'echec' ELSE statut END
				WHERE id = :id""")
			.param("id", d.id())
			.param("maintenant", utc(maintenant))
			.param("resultat", resultat.code())
			.param("fraicheur", FRAICHEUR)
			.update();
		return false;
	}

	/** Pour chaque projet en ligne, son déploiement en ligne le plus anciennement mis en ligne. */
	List<DeploiementsEnLigne.ProjetEnLigne> enLigne(Instant maintenant) {
		return base.sql("""
				SELECT DISTINCT ON (projet_id) projet_id, id, url, hebergeur, mis_en_ligne_le, verifie_le
				FROM deploiement
				WHERE statut = 'en_ligne' AND verifie_le >= CAST(:maintenant AS timestamptz) - CAST(:fraicheur AS interval)
				ORDER BY projet_id, mis_en_ligne_le, id""")
			.param("maintenant", utc(maintenant))
			.param("fraicheur", FRAICHEUR)
			.query((rs, i) -> new DeploiementsEnLigne.ProjetEnLigne(rs.getObject("projet_id", UUID.class),
					rs.getObject("id", UUID.class), rs.getString("url"), Hebergeur.depuisCode(rs.getString("hebergeur")),
					instant(rs, "mis_en_ligne_le"), instant(rs, "verifie_le")))
			.list();
	}

	/** Projets distincts en ligne (vérifiés depuis moins de 24 h), éventuellement pour un seul hébergeur. */
	long compterProjetsEnLigne(Hebergeur seulement, Instant maintenant) {
		return base.sql("""
				SELECT count(DISTINCT projet_id) FROM deploiement
				WHERE statut = 'en_ligne' AND verifie_le >= CAST(:maintenant AS timestamptz) - CAST(:fraicheur AS interval)
				  AND (CAST(:hebergeur AS varchar) IS NULL OR hebergeur = :hebergeur)""")
			.param("maintenant", utc(maintenant))
			.param("fraicheur", FRAICHEUR)
			.param("hebergeur", seulement == null ? null : seulement.code())
			.query(Long.class)
			.single();
	}

	private static Deploiement versDeploiement(ResultSet rs, int ligne) throws SQLException {
		Instant essaiLe = instant(rs, "dernier_essai_le");
		return new Deploiement(rs.getObject("id", UUID.class), rs.getObject("projet_id", UUID.class),
				rs.getString("url"), Hebergeur.depuisCode(rs.getString("hebergeur")),
				Deploiement.Statut.depuisCode(rs.getString("statut")), instant(rs, "verifie_le"),
				instant(rs, "mis_en_ligne_le"),
				essaiLe == null ? null : new Deploiement.Essai(essaiLe, rs.getString("dernier_resultat")),
				instant(rs, "cree_le"));
	}

	private static Instant instant(ResultSet rs, String colonne) throws SQLException {
		OffsetDateTime valeur = rs.getObject(colonne, OffsetDateTime.class);
		return valeur == null ? null : valeur.toInstant();
	}

	private static OffsetDateTime utc(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}

}
