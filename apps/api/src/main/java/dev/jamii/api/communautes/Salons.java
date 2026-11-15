package dev.jamii.api.communautes;

import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Les salons d'une communauté, leurs messages (V20261114_1812__salons.sql) et leurs appels (V20261115_0741). Un appel
 * n'existe que quand quelqu'un le lance ; il est « en cours » une heure au plus, ou jusqu'à ce qu'on le termine.
 */
@Component
class Salons {

	/** Contre le spam : au plus 20 messages par minute et par personne (contrat). */
	static final int MESSAGES_PAR_MINUTE = 20;

	/** Un appel se termine tout seul une heure après son lancement : on ne sait pas quand les gens partent. */
	static final Duration DUREE_APPEL = Duration.ofHours(1);

	/** Au plus 10 appels lancés par heure et par personne (contrat). */
	static final int APPELS_PAR_HEURE = 10;

	record Salon(UUID id, String slug, String nom, String sujet) {
	}

	/** `appelId` : le message annonce cet appel ; `appelEnCours` : il l'est encore. */
	record Message(UUID id, long numero, UUID auteurId, String texte, Instant envoyeLe, UUID appelId, boolean appelEnCours,
			List<UUID> pieces) {
	}

	record Appel(UUID id, UUID salonId, UUID lancePar, String salle, Instant lanceLe) {
	}

	private static final SecureRandom HASARD = new SecureRandom();

	private final JdbcClient base;

	Salons(JdbcClient base) {
		this.base = base;
	}

	List<Salon> lister(UUID communaute) {
		return base.sql("SELECT id, slug, nom, sujet FROM salon WHERE communaute_id = :c ORDER BY ordre, slug")
			.param("c", communaute)
			.query((rs, n) -> new Salon(rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("nom"),
					rs.getString("sujet")))
			.list();
	}

	Optional<Salon> lire(UUID communaute, String slug) {
		return lister(communaute).stream().filter(s -> s.slug().equals(slug)).findFirst();
	}

	/** Sans `apres` : les `limite` derniers. Avec : ceux arrivés depuis. Toujours du plus ancien au plus récent. */
	List<Message> messages(UUID salon, Long apres, int limite, Instant maintenant) {
		String choix = apres == null
				? "SELECT * FROM (SELECT * FROM message_salon WHERE salon_id = :s ORDER BY numero DESC LIMIT :l) m"
				: "SELECT * FROM message_salon m WHERE salon_id = :s AND numero > :apres";
		String sql = "SELECT m.*, (a.id IS NOT NULL AND a.termine_le IS NULL AND a.lance_le > :limiteAppel) AS appel_en_cours"
				+ " FROM (" + choix + (apres == null ? "" : " ORDER BY numero LIMIT :l") + ") m"
				+ " LEFT JOIN appel_salon a ON a.id = m.appel_id ORDER BY m.numero";
		var requete = base.sql(sql).param("s", salon).param("l", limite)
			.param("limiteAppel", Timestamp.from(maintenant.minus(DUREE_APPEL)));
		if (apres != null) {
			requete = requete.param("apres", apres);
		}
		return requete.query(Salons::message).list();
	}

	Message ecrire(UUID salon, UUID auteur, String texte, Instant maintenant) {
		return ecrire(salon, auteur, texte, List.of(), maintenant);
	}

	/** Un message avec ses pièces jointes (déjà vérifiées par le module fichiers). */
	Message ecrire(UUID salon, UUID auteur, String texte, List<UUID> pieces, Instant maintenant) {
		return ecrire(salon, auteur, texte, maintenant, null, pieces);
	}

	private Message ecrire(UUID salon, UUID auteur, String texte, Instant maintenant, UUID appel, List<UUID> pieces) {
		UUID id = UUID.randomUUID();
		long numero = base.sql("""
				INSERT INTO message_salon (id, salon_id, auteur_id, texte, envoye_le, appel_id, pieces)
				VALUES (:id, :s, :a, :t, :le, :appel, CAST(:pieces AS UUID[]))
				RETURNING numero
				""")
			.param("id", id).param("s", salon).param("a", auteur).param("t", texte).param("le", Timestamp.from(maintenant))
			.param("appel", appel).param("pieces", dev.jamii.api.fichiers.Fichiers.tableau(pieces))
			.query(Long.class).single();
		return new Message(id, numero, auteur, texte, maintenant, appel, appel != null, pieces);
	}

	/** Secondes à attendre avant d'écrire encore, 0 si c'est possible tout de suite. */
	long attenteAvantMessage(UUID auteur, Instant maintenant) {
		return attente(base.sql("""
				SELECT envoye_le AS le FROM message_salon WHERE auteur_id = :a AND envoye_le > :depuis ORDER BY envoye_le
				"""), auteur, maintenant, Duration.ofMinutes(1), MESSAGES_PAR_MINUTE);
	}

	// ---------------------------------------------------------------- les appels

	/** L'appel en cours du salon : lancé il y a moins d'une heure et pas terminé. */
	Optional<Appel> appelEnCours(UUID salon, Instant maintenant) {
		return base.sql("""
				SELECT id, salon_id, lance_par, salle, lance_le FROM appel_salon
				WHERE salon_id = :s AND termine_le IS NULL AND lance_le > :limite
				""").param("s", salon).param("limite", Timestamp.from(maintenant.minus(DUREE_APPEL)))
			.query(Salons::appel).optional();
	}

	/**
	 * Lance un appel avec une salle neuve, et l'annonce dans le salon. Un appel resté ouvert au-delà d'une heure est
	 * d'abord clos (l'index n'en permet qu'un ouvert par salon).
	 */
	Appel lancer(UUID salon, String communaute, String salonSlug, UUID auteur, String annonce, Instant maintenant) {
		base.sql("""
				UPDATE appel_salon SET termine_le = lance_le + make_interval(hours => 1)
				WHERE salon_id = :s AND termine_le IS NULL AND lance_le <= :limite
				""").param("s", salon).param("limite", Timestamp.from(maintenant.minus(DUREE_APPEL))).update();
		byte[] octets = new byte[6];
		HASARD.nextBytes(octets);
		Appel appel = new Appel(UUID.randomUUID(), salon, auteur,
				"jamii-" + communaute + "-" + salonSlug + "-" + HexFormat.of().formatHex(octets), maintenant);
		base.sql("""
				INSERT INTO appel_salon (id, salon_id, lance_par, salle, lance_le) VALUES (:id, :s, :u, :salle, :le)
				""").param("id", appel.id()).param("s", salon).param("u", auteur).param("salle", appel.salle())
			.param("le", Timestamp.from(maintenant)).update();
		ecrire(salon, auteur, annonce, maintenant, appel.id(), List.of());
		return appel;
	}

	void terminer(UUID appel, Instant maintenant) {
		base.sql("UPDATE appel_salon SET termine_le = :t WHERE id = :id AND termine_le IS NULL")
			.param("t", Timestamp.from(maintenant)).param("id", appel).update();
	}

	/** Secondes à attendre avant de lancer un autre appel, 0 si c'est possible tout de suite. */
	long attenteAvantAppel(UUID auteur, Instant maintenant) {
		return attente(base.sql("""
				SELECT lance_le AS le FROM appel_salon WHERE lance_par = :a AND lance_le > :depuis ORDER BY lance_le
				"""), auteur, maintenant, Duration.ofHours(1), APPELS_PAR_HEURE);
	}

	private static long attente(JdbcClient.StatementSpec requete, UUID auteur, Instant maintenant, Duration fenetre, int max) {
		List<Instant> recents = requete.param("a", auteur).param("depuis", Timestamp.from(maintenant.minus(fenetre)))
			.query((rs, n) -> rs.getTimestamp("le").toInstant())
			.list();
		if (recents.size() < max) {
			return 0;
		}
		Instant libre = recents.get(recents.size() - max).plus(fenetre);
		return Math.max(1, Duration.between(maintenant, libre).toSeconds() + 1);
	}

	private static Message message(ResultSet rs, int n) throws SQLException {
		return new Message(rs.getObject("id", UUID.class), rs.getLong("numero"), rs.getObject("auteur_id", UUID.class),
				rs.getString("texte"), rs.getTimestamp("envoye_le").toInstant(), rs.getObject("appel_id", UUID.class),
				rs.getBoolean("appel_en_cours"), dev.jamii.api.fichiers.Fichiers.lireTableau(rs, "pieces"));
	}

	private static Appel appel(ResultSet rs, int n) throws SQLException {
		return new Appel(rs.getObject("id", UUID.class), rs.getObject("salon_id", UUID.class),
				rs.getObject("lance_par", UUID.class), rs.getString("salle"), rs.getTimestamp("lance_le").toInstant());
	}

}
