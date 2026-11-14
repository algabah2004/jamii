package dev.jamii.api.communautes;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Les salons d'une communauté et leurs messages (V20261114_1812__salons.sql). */
@Component
class Salons {

	/** Contre le spam : au plus 20 messages par minute et par personne (contrat). */
	static final int MESSAGES_PAR_MINUTE = 20;

	record Salon(UUID id, String slug, String nom, String sujet, String salleAppel) {
	}

	record Message(UUID id, long numero, UUID auteurId, String texte, Instant envoyeLe) {
	}

	private final JdbcClient base;

	Salons(JdbcClient base) {
		this.base = base;
	}

	List<Salon> lister(UUID communaute) {
		return base.sql("SELECT id, slug, nom, sujet, salle_appel FROM salon WHERE communaute_id = :c ORDER BY ordre, slug")
			.param("c", communaute)
			.query((rs, n) -> new Salon(rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("nom"),
					rs.getString("sujet"), rs.getString("salle_appel")))
			.list();
	}

	Optional<Salon> lire(UUID communaute, String slug) {
		return lister(communaute).stream().filter(s -> s.slug().equals(slug)).findFirst();
	}

	/** Sans `apres` : les `limite` derniers. Avec : ceux arrivés depuis. Toujours du plus ancien au plus récent. */
	List<Message> messages(UUID salon, Long apres, int limite) {
		String sql = apres == null
				? "SELECT * FROM (SELECT * FROM message_salon WHERE salon_id = :s ORDER BY numero DESC LIMIT :l) m ORDER BY numero"
				: "SELECT * FROM message_salon WHERE salon_id = :s AND numero > :apres ORDER BY numero LIMIT :l";
		var requete = base.sql(sql).param("s", salon).param("l", limite);
		if (apres != null) {
			requete = requete.param("apres", apres);
		}
		return requete.query((rs, n) -> new Message(rs.getObject("id", UUID.class), rs.getLong("numero"),
				rs.getObject("auteur_id", UUID.class), rs.getString("texte"), rs.getTimestamp("envoye_le").toInstant()))
			.list();
	}

	Message ecrire(UUID salon, UUID auteur, String texte, Instant maintenant) {
		UUID id = UUID.randomUUID();
		long numero = base.sql("""
				INSERT INTO message_salon (id, salon_id, auteur_id, texte, envoye_le) VALUES (:id, :s, :a, :t, :le)
				RETURNING numero
				""")
			.param("id", id).param("s", salon).param("a", auteur).param("t", texte).param("le", Timestamp.from(maintenant))
			.query(Long.class).single();
		return new Message(id, numero, auteur, texte, maintenant);
	}

	/** Secondes à attendre avant d'écrire encore, 0 si c'est possible tout de suite. */
	long attenteAvantMessage(UUID auteur, Instant maintenant) {
		Instant depuis = maintenant.minus(Duration.ofMinutes(1));
		List<Instant> recents = base.sql("""
				SELECT envoye_le FROM message_salon WHERE auteur_id = :a AND envoye_le > :depuis ORDER BY envoye_le
				""")
			.param("a", auteur).param("depuis", Timestamp.from(depuis))
			.query((rs, n) -> rs.getTimestamp("envoye_le").toInstant())
			.list();
		if (recents.size() < MESSAGES_PAR_MINUTE) {
			return 0;
		}
		Instant libre = recents.get(recents.size() - MESSAGES_PAR_MINUTE).plus(Duration.ofMinutes(1));
		return Math.max(1, Duration.between(maintenant, libre).toSeconds() + 1);
	}

}
