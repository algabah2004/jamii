package dev.jamii.api.projets;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jamii.api.systalink.ChiffresDesModules;

/**
 * La porte d'entrée des autres modules vers les projets (docs/modele-donnees.md §6) : ils ne lisent jamais les
 * tables `projet` et `membre_equipe`. Implémente aussi le chiffre « projets » du tableau de bord Systalink.
 */
@Service
public class Projets implements ChiffresDesModules.Projets {

	private final JdbcClient base;

	Projets(JdbcClient base) {
		this.base = base;
	}

	/**
	 * Tout utilisateur connecté voit les projets (contracts/projets.yaml). Il est MEMBRE s'il est dans l'équipe
	 * actuelle ; un membre parti redevient simple lecteur.
	 */
	@Transactional(readOnly = true)
	public AccesAuProjet acces(UUID projetId, UUID utilisateurId) {
		return base.sql("""
				SELECT EXISTS (
					SELECT 1 FROM membre_equipe
					WHERE projet_id = p.id AND utilisateur_id = :utilisateur AND parti_le IS NULL
				)
				FROM projet p WHERE p.id = :projet
				""")
			.param("projet", projetId)
			.param("utilisateur", utilisateurId)
			.query(Boolean.class)
			.optional()
			.map(membre -> membre ? AccesAuProjet.MEMBRE : AccesAuProjet.LECTEUR)
			.orElse(AccesAuProjet.INTROUVABLE);
	}

	/**
	 * Crée le projet d'un tour de cercle : `porteur` en est le porteur, tous les `membres` (porteur compris) sont dans
	 * l'équipe, et il démarre `en_cours` puisque l'équipe est déjà formée. Appelé par le module cercles.
	 */
	@Transactional
	public UUID creerPourCercle(UUID porteur, List<UUID> membres, String titre, String description, Instant maintenant) {
		UUID id = UUID.randomUUID();
		Timestamp t = Timestamp.from(maintenant);
		base.sql("""
				INSERT INTO projet (id, titre, description, origine, porteur_id, statut, derniere_activite_le, cree_le, mis_a_jour_le)
				VALUES (:id, :titre, :description, 'cercle', :porteur, 'en_cours', :t, :t, :t)
				""")
			.param("id", id).param("titre", titre).param("description", description).param("porteur", porteur)
			.param("t", t).update();
		for (UUID membre : membres) {
			base.sql("INSERT INTO membre_equipe (projet_id, utilisateur_id, rejoint_le) VALUES (:projet, :membre, :t)")
				.param("projet", id).param("membre", membre).param("t", t).update();
		}
		return id;
	}

	/** Le tour est livré : le projet passe à `livre` (sauf s'il a été abandonné). Renvoie faux si rien n'a changé. */
	@Transactional
	public boolean marquerLivre(UUID projetId, Instant maintenant) {
		Timestamp t = Timestamp.from(maintenant);
		return base.sql("""
				UPDATE projet SET statut = 'livre', derniere_activite_le = :t, mis_a_jour_le = :t, version = version + 1
				WHERE id = :id AND statut NOT IN ('livre', 'abandonne')
				""")
			.param("id", projetId).param("t", t).update() > 0;
	}

	/** Projets créés à partir de `depuis` (tous si `depuis` est null), abandonnés compris : ils ont été créés. */
	@Override
	@Transactional(readOnly = true)
	public long compter(Instant depuis) {
		if (depuis == null) {
			return base.sql("SELECT count(*) FROM projet").query(Long.class).single();
		}
		return base.sql("SELECT count(*) FROM projet WHERE cree_le >= :depuis")
			.param("depuis", Timestamp.from(depuis))
			.query(Long.class)
			.single();
	}

}
