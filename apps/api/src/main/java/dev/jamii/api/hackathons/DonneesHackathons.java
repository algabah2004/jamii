package dev.jamii.api.hackathons;

import dev.jamii.api.questionnaires.Questionnaire;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import dev.jamii.api.auth.DonneesPersonnelles;

/**
 * Les hackathons d'un compte dans l'export (GET /api/me/export) : ce qu'il organise, ses inscriptions, les rendus de
 * ses équipes et les notes qu'il a données comme juré.
 *
 * <p>À la suppression du compte :
 * <ul>
 * <li>ses notes et commentaires de juré sont **anonymisés**, pas effacés : un même identifiant tiré au hasard les
 * remplace, et le classement déjà calculé ne change pas ;</li>
 * <li>une équipe dont il est le chef passe au membre arrivé le plus tôt, pour que les autres gardent leur rendu ;
 * seule, elle part avec lui ;</li>
 * <li>le reste (inscriptions, juré, hackathons organisés) part en cascade avec le compte.</li>
 * </ul>
 */
@Component
class DonneesHackathons implements DonneesPersonnelles {

	record MesHackathons(List<Organise> organises, List<MonInscription> inscriptions, List<MonRendu> rendus, List<MaNote> notes) {
	}

	record Organise(UUID id, String titre, Instant creeLe) {
	}

	record MonInscription(UUID hackathonId, String statut, UUID equipeId, boolean chercheEquipe, List<String> competences,
			List<Questionnaire.Reponse> reponses, Instant creeLe) {
	}

	record MonRendu(UUID id, UUID hackathonId, UUID equipeId, String titre, String lienProduit, Instant modifieLe) {
	}

	record MaNote(UUID renduId, String critere, int valeur, String commentaire) {
	}

	private final JdbcClient base;

	DonneesHackathons(JdbcClient base) {
		this.base = base;
	}

	@Override
	public String section() {
		return "hackathons";
	}

	@Override
	public MesHackathons exporter(UUID utilisateurId) {
		List<Organise> organises = base.sql("SELECT id, titre, cree_le FROM hackathon WHERE organisateur_id = :u ORDER BY cree_le, id")
			.param("u", utilisateurId)
			.query((rs, i) -> new Organise(rs.getObject("id", UUID.class), rs.getString("titre"), rs.getTimestamp("cree_le").toInstant()))
			.list();
		List<MonInscription> inscriptions = base.sql("""
				SELECT hackathon_id, statut, equipe_id, cherche_equipe, competences, reponses, cree_le FROM inscription
				WHERE utilisateur_id = :u ORDER BY cree_le, id
				""").param("u", utilisateurId)
			.query((rs, i) -> new MonInscription(rs.getObject("hackathon_id", UUID.class), rs.getString("statut"),
					rs.getObject("equipe_id", UUID.class), rs.getBoolean("cherche_equipe"),
					List.of((String[]) rs.getArray("competences").getArray()), Questionnaire.reponses(rs.getString("reponses")),
					rs.getTimestamp("cree_le").toInstant()))
			.list();
		List<MonRendu> rendus = base.sql("""
				SELECT r.id, r.hackathon_id, r.equipe_id, r.titre, r.lien_produit, r.modifie_le FROM rendu r
				JOIN inscription i ON i.equipe_id = r.equipe_id WHERE i.utilisateur_id = :u ORDER BY r.modifie_le, r.id
				""").param("u", utilisateurId)
			.query((rs, i) -> new MonRendu(rs.getObject("id", UUID.class), rs.getObject("hackathon_id", UUID.class),
					rs.getObject("equipe_id", UUID.class), rs.getString("titre"), rs.getString("lien_produit"),
					rs.getTimestamp("modifie_le").toInstant()))
			.list();
		List<MaNote> notes = base.sql("""
				SELECT n.rendu_id, c.nom, n.valeur, a.commentaire FROM note n
				JOIN critere c ON c.id = n.critere_id
				LEFT JOIN appreciation a ON a.rendu_id = n.rendu_id AND a.utilisateur_id = n.utilisateur_id
				WHERE n.utilisateur_id = :u ORDER BY n.rendu_id, c.ordre
				""").param("u", utilisateurId)
			.query((rs, i) -> new MaNote(rs.getObject("rendu_id", UUID.class), rs.getString("nom"), rs.getInt("valeur"),
					rs.getString("commentaire")))
			.list();
		return new MesHackathons(organises, inscriptions, rendus, notes);
	}

	@Override
	public void avantSuppression(UUID utilisateurId, Instant maintenant) {
		UUID anonyme = UUID.randomUUID();
		base.sql("UPDATE note SET utilisateur_id = :a WHERE utilisateur_id = :u").param("a", anonyme).param("u", utilisateurId).update();
		base.sql("UPDATE appreciation SET utilisateur_id = :a WHERE utilisateur_id = :u").param("a", anonyme).param("u", utilisateurId).update();
		// Le chef d'une équipe qui a d'autres membres la confie au plus ancien d'entre eux.
		base.sql("""
				UPDATE equipe e SET chef_id = (
				  SELECT i.utilisateur_id FROM inscription i WHERE i.equipe_id = e.id AND i.utilisateur_id <> :u
				  ORDER BY i.cree_le, i.id LIMIT 1)
				WHERE e.chef_id = :u AND EXISTS (SELECT 1 FROM inscription i WHERE i.equipe_id = e.id AND i.utilisateur_id <> :u)
				""").param("u", utilisateurId).update();
	}

}
