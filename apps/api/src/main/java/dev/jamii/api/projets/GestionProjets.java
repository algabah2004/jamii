package dev.jamii.api.projets;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Créer et lire un projet (contracts/projets.yaml). Les tables `projet` et `membre_equipe` n'ont que ce module. */
@Service
class GestionProjets {

	record Fiche(UUID id, String titre, String description, String origine, UUID demandeId, UUID porteurId,
			UUID projetReprisId, String statut, Instant derniereActiviteLe, Instant creeLe, int version) {
	}

	record Membre(UUID utilisateurId, Instant rejointLe) {
	}

	private final JdbcClient base;

	GestionProjets(JdbcClient base) {
		this.base = base;
	}

	/** Le projet naît au statut `recrutement`, et son porteur est le premier membre de l'équipe, dans la même transaction. */
	@Transactional
	UUID creerDepuisUneIdee(UUID porteur, String titre, String description, Instant maintenant) {
		UUID id = UUID.randomUUID();
		Timestamp t = Timestamp.from(maintenant);
		base.sql("""
				INSERT INTO projet (id, titre, description, origine, porteur_id, derniere_activite_le, cree_le, mis_a_jour_le)
				VALUES (:id, :titre, :description, 'idee', :porteur, :t, :t, :t)
				""")
			.param("id", id).param("titre", titre).param("description", description).param("porteur", porteur)
			.param("t", t).update();
		base.sql("INSERT INTO membre_equipe (projet_id, utilisateur_id, rejoint_le) VALUES (:projet, :porteur, :t)")
			.param("projet", id).param("porteur", porteur).param("t", t).update();
		return id;
	}

	/**
	 * Le projet né d'une demande de PME : même chose qu'une idée, avec `demande_id`. L'index unique partiel
	 * `projet_demande_active` refuse un second projet actif sur la même demande (DuplicateKeyException).
	 */
	@Transactional
	UUID creerDepuisUneDemande(UUID porteur, String titre, String description, UUID demandeId, Instant maintenant) {
		UUID id = UUID.randomUUID();
		Timestamp t = Timestamp.from(maintenant);
		base.sql("""
				INSERT INTO projet (id, titre, description, origine, demande_id, porteur_id, derniere_activite_le, cree_le, mis_a_jour_le)
				VALUES (:id, :titre, :description, 'demande', :demande, :porteur, :t, :t, :t)
				""")
			.param("id", id).param("titre", titre).param("description", description).param("demande", demandeId)
			.param("porteur", porteur).param("t", t).update();
		base.sql("INSERT INTO membre_equipe (projet_id, utilisateur_id, rejoint_le) VALUES (:projet, :porteur, :t)")
			.param("projet", id).param("porteur", porteur).param("t", t).update();
		return id;
	}

	@Transactional(readOnly = true)
	Optional<Fiche> lire(UUID id) {
		return base.sql("""
				SELECT id, titre, description, origine, demande_id, porteur_id, projet_repris_id, statut,
				       derniere_activite_le, cree_le, version
				FROM projet WHERE id = :id
				""")
			.param("id", id)
			.query((rs, n) -> new Fiche(rs.getObject("id", UUID.class), rs.getString("titre"), rs.getString("description"),
					rs.getString("origine"), rs.getObject("demande_id", UUID.class), rs.getObject("porteur_id", UUID.class),
					rs.getObject("projet_repris_id", UUID.class), rs.getString("statut"),
					rs.getTimestamp("derniere_activite_le").toInstant(), rs.getTimestamp("cree_le").toInstant(),
					rs.getInt("version")))
			.optional();
	}

	/** L'équipe actuelle (les membres partis n'y figurent pas), dans l'ordre d'arrivée. */
	@Transactional(readOnly = true)
	List<Membre> equipe(UUID projetId) {
		return base.sql("""
				SELECT utilisateur_id, rejoint_le FROM membre_equipe
				WHERE projet_id = :projet AND parti_le IS NULL ORDER BY rejoint_le, utilisateur_id
				""")
			.param("projet", projetId)
			.query((rs, n) -> new Membre(rs.getObject("utilisateur_id", UUID.class), rs.getTimestamp("rejoint_le").toInstant()))
			.list();
	}

}
