package dev.jamii.api.fichiers;

import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import dev.jamii.api.auth.DonneesPersonnelles;

/** Métadonnées dans l'export et effacement des fichiers privés à la suppression d'un des comptes. */
@Component
class DonneesFichiers implements DonneesPersonnelles {

	private final JdbcClient base;
	private final Fichiers fichiers;

	DonneesFichiers(JdbcClient base, Fichiers fichiers) {
		this.base = base;
		this.fichiers = fichiers;
	}

	@Override
	public String section() {
		return "fichiers";
	}

	@Override
	public Object exporter(UUID utilisateurId) {
		return fichiers.decrire(base.sql("SELECT id FROM fichier WHERE proprietaire_id = :id ORDER BY cree_le, id")
			.param("id", utilisateurId).query(UUID.class).list());
	}

	@Override
	public void avantSuppression(UUID utilisateurId, Instant maintenant) {
		base.sql("DELETE FROM fichier WHERE (conversation_avec IS NOT NULL AND (proprietaire_id = :id OR conversation_avec = :id)) OR (cercle_id IS NOT NULL AND proprietaire_id = :id)")
			.param("id", utilisateurId).update();
	}

}
