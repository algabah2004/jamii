package dev.jamii.api.communautes;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.jamii.api.auth.DonneesPersonnelles;

/**
 * Les communautés d'un compte dans l'export (#114) : ses adhésions, ses publications, ses réponses et ses messages
 * dans les salons. Rien de plus à faire à la suppression (V9__communautes.sql) : les adhésions partent en
 * cascade, les publications et les réponses restent sans auteur pour que les fils des autres restent lisibles, les
 * messages des salons partent avec le compte.
 */
@Component
class DonneesCommunautes implements DonneesPersonnelles {

	record Adhesion(String communaute, String nom, String role, Instant rejointLe) {
	}

	record Publication(UUID id, String communaute, String type, String titre, String contenu, UUID projetId,
			UUID reponseAccepteeId, Instant creeLe) {
	}

	/** `acceptee` : c'est la réponse retenue par l'auteur de la publication. */
	record Reponse(UUID id, UUID publicationId, String contenu, boolean acceptee, Instant creeLe) {
	}

	/** Ses messages dans les salons (#155). */
	record MessageSalon(UUID id, String communaute, String salon, String texte, Instant envoyeLe) {
	}

	record Section(List<Adhesion> adhesions, List<Publication> publications, List<Reponse> reponses,
			List<MessageSalon> messagesSalons) {
	}

	private final JdbcClient base;

	DonneesCommunautes(JdbcClient base) {
		this.base = base;
	}

	@Override
	public String section() {
		return "communautes";
	}

	@Override
	@Transactional(readOnly = true)
	public Section exporter(UUID utilisateurId) {
		List<Adhesion> adhesions = base.sql("""
				SELECT c.slug, c.nom, a.role, a.rejoint_le
				FROM adhesion a JOIN communaute c ON c.id = a.communaute_id
				WHERE a.utilisateur_id = :u
				ORDER BY a.rejoint_le, c.slug
				""")
			.param("u", utilisateurId)
			.query((rs, n) -> new Adhesion(rs.getString("slug"), rs.getString("nom"), rs.getString("role"),
					instant(rs, "rejoint_le")))
			.list();
		List<Publication> publications = base.sql("""
				SELECT p.id, c.slug, p.type, p.titre, p.contenu, p.projet_id, p.reponse_acceptee_id, p.cree_le
				FROM publication p JOIN communaute c ON c.id = p.communaute_id
				WHERE p.auteur_id = :u
				ORDER BY p.cree_le, p.id
				""")
			.param("u", utilisateurId)
			.query((rs, n) -> new Publication(rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("type"),
					rs.getString("titre"), rs.getString("contenu"), rs.getObject("projet_id", UUID.class),
					rs.getObject("reponse_acceptee_id", UUID.class), instant(rs, "cree_le")))
			.list();
		List<Reponse> reponses = base.sql("""
				SELECT r.id, r.publication_id, r.contenu, p.reponse_acceptee_id IS NOT DISTINCT FROM r.id AS acceptee, r.cree_le
				FROM reponse r JOIN publication p ON p.id = r.publication_id
				WHERE r.auteur_id = :u
				ORDER BY r.cree_le, r.id
				""")
			.param("u", utilisateurId)
			.query((rs, n) -> new Reponse(rs.getObject("id", UUID.class), rs.getObject("publication_id", UUID.class),
					rs.getString("contenu"), rs.getBoolean("acceptee"), instant(rs, "cree_le")))
			.list();
		List<MessageSalon> messages = base.sql("""
				SELECT m.id, c.slug AS communaute, s.slug AS salon, m.texte, m.envoye_le
				FROM message_salon m JOIN salon s ON s.id = m.salon_id JOIN communaute c ON c.id = s.communaute_id
				WHERE m.auteur_id = :u
				ORDER BY m.numero
				""")
			.param("u", utilisateurId)
			.query((rs, n) -> new MessageSalon(rs.getObject("id", UUID.class), rs.getString("communaute"), rs.getString("salon"),
					rs.getString("texte"), instant(rs, "envoye_le")))
			.list();
		return new Section(adhesions, publications, reponses, messages);
	}

	private static Instant instant(ResultSet rs, String colonne) throws SQLException {
		Timestamp t = rs.getTimestamp(colonne);
		return t == null ? null : t.toInstant();
	}

}
