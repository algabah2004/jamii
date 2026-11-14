package dev.jamii.api.missions;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import dev.jamii.api.auth.DonneesPersonnelles;

/**
 * Les missions d'un compte dans l'export (GET /api/me/export) : celles qu'il a publiées, ses propositions, les jalons
 * qu'il a livrés et les avis qu'il a donnés.
 *
 * <p>À la suppression du compte (contrat : « ses missions publiées sont fermées et ses avis anonymisés ») :
 * <ul>
 * <li>ses brouillons sont effacés ; ses missions publiées sont fermées ;</li>
 * <li>une mission attribuée, en cours ou en désaccord dont il est une partie est annulée : l'autre ne peut plus avancer ;</li>
 * <li>ses propositions en attente sont retirées ; les informations de départ qu'il a données sont effacées ;</li>
 * <li>ses avis restent, sans lien vers lui ; le travail terminé reste visible de l'autre partie, au nom de « Compte
 * supprimé ».</li>
 * </ul>
 */
@Component
class DonneesMissions implements DonneesPersonnelles {

	record MesMissions(List<Publiee> publiees, List<Envoyee> propositions, List<Livree> livraisons, List<Avis.UnAvis> avis) {
	}

	record Publiee(UUID id, String titre, String besoin, String statut, Instant creeLe) {
	}

	record Envoyee(UUID missionId, String message, long centimes, String devise, int delaiJours, String statut, Instant envoyeeLe) {
	}

	record Livree(UUID missionId, String jalon, String message, List<String> liens, Instant livreeLe) {
	}

	private final JdbcClient base;

	DonneesMissions(JdbcClient base) {
		this.base = base;
	}

	@Override
	public String section() {
		return "missions";
	}

	@Override
	public MesMissions exporter(UUID utilisateurId) {
		List<Publiee> publiees = base.sql("SELECT id, titre, besoin, statut, cree_le FROM mission WHERE client_id = :u ORDER BY cree_le, id")
			.param("u", utilisateurId)
			.query((rs, i) -> new Publiee(rs.getObject("id", UUID.class), rs.getString("titre"), rs.getString("besoin"),
					rs.getString("statut"), rs.getTimestamp("cree_le").toInstant()))
			.list();
		List<Envoyee> propositions = base.sql("""
				SELECT mission_id, message, prix_centimes, devise, delai_jours, statut, cree_le FROM proposition
				WHERE developpeur_id = :u ORDER BY cree_le, id
				""").param("u", utilisateurId)
			.query((rs, i) -> new Envoyee(rs.getObject("mission_id", UUID.class), rs.getString("message"), rs.getLong("prix_centimes"),
					rs.getString("devise"), rs.getInt("delai_jours"), rs.getString("statut"), rs.getTimestamp("cree_le").toInstant()))
			.list();
		List<Livree> livraisons = base.sql("""
				SELECT d.mission_id, j.titre, l.message, l.liens, l.livree_le FROM livraison l
				JOIN jalon_mission j ON j.id = l.jalon_id JOIN devis d ON d.id = j.devis_id
				JOIN proposition p ON p.id = d.proposition_id
				WHERE p.developpeur_id = :u ORDER BY l.livree_le, l.id
				""").param("u", utilisateurId)
			.query((rs, i) -> new Livree(rs.getObject("mission_id", UUID.class), rs.getString("titre"), rs.getString("message"),
					List.of((String[]) rs.getArray("liens").getArray()), rs.getTimestamp("livree_le").toInstant()))
			.list();
		List<Avis.UnAvis> avis = base.sql("SELECT auteur, note, texte, donne_le FROM avis_mission WHERE auteur_id = :u ORDER BY donne_le")
			.param("u", utilisateurId)
			.query((rs, i) -> new Avis.UnAvis(rs.getString("auteur"), rs.getInt("note"), rs.getString("texte"),
					rs.getTimestamp("donne_le").toInstant()))
			.list();
		return new MesMissions(publiees, propositions, livraisons, avis);
	}

	@Override
	public void avantSuppression(UUID utilisateurId, Instant maintenant) {
		base.sql("DELETE FROM mission WHERE client_id = :u AND statut = 'brouillon'").param("u", utilisateurId).update();
		base.sql("UPDATE mission SET statut = 'fermee', version = version + 1 WHERE client_id = :u AND statut = 'publiee'")
			.param("u", utilisateurId).update();
		base.sql("""
				UPDATE mission SET statut = 'annulee', annulation_raison = 'Compte supprimé', version = version + 1
				WHERE (client_id = :u OR developpeur_id = :u) AND statut IN ('attribuee', 'en_cours', 'desaccord')
				""").param("u", utilisateurId).update();
		base.sql("UPDATE mission SET informations_depart = NULL WHERE client_id = :u").param("u", utilisateurId).update();
		base.sql("UPDATE proposition SET statut = 'retiree' WHERE developpeur_id = :u AND statut IN ('envoyee', 'retenue')")
			.param("u", utilisateurId).update();
		// avis_mission.auteur_id passe à NULL avec la suppression du compte (ON DELETE SET NULL).
	}

}
