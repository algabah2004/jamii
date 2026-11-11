package dev.jamii.api.projets;

import java.sql.Timestamp;
import java.time.Instant;
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
