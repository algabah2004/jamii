package dev.jamii.api.demandes;

import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** La porte d'entrée des autres modules vers les demandes : ils ne lisent jamais la table `demande`. */
@Service
public class Demandes {

	private final JdbcClient base;

	Demandes(JdbcClient base) {
		this.base = base;
	}

	/** Cette demande existe-t-elle, et cette personne en est-elle l'auteure ? (Une demande n'est visible que de son auteur.) */
	@Transactional(readOnly = true)
	public boolean estAuteur(UUID demandeId, UUID utilisateurId) {
		return base.sql("SELECT EXISTS (SELECT 1 FROM demande WHERE id = :id AND auteur_id = :auteur)")
			.param("id", demandeId).param("auteur", utilisateurId).query(Boolean.class).single();
	}

}
