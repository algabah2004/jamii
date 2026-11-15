package dev.jamii.api.cercles;

import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Le salon d'un cercle (V20261115_1500, contracts/cercles.yaml v0.2) : ses messages et ses appels. Les droits (membres
 * seulement) sont vérifiés par le contrôleur ; ici, on lit et on écrit.
 */
@Component
class SalonCercle {

	/** Contre le spam : au plus 20 messages par minute et par personne, tous cercles confondus. */
	static final int MESSAGES_PAR_MINUTE = 20;

	/** Un appel se termine tout seul une heure après son lancement. */
	static final Duration DUREE_APPEL = Duration.ofHours(1);

	/** Au plus 10 appels lancés par heure et par personne. */
	static final int APPELS_PAR_HEURE = 10;

	/** Les coups de main ouverts montrés en haut du salon, les plus anciens d'abord. */
	static final int COUPS_DE_MAIN_MAX = 10;

	record Message(UUID id, long numero, String type, UUID auteurId, String texte, String fait, String prevu,
			String bloque, String evenement, Integer tour, UUID personneId, Instant resoluLe, UUID resoluPar,
			UUID appelId, boolean appelEnCours, Instant envoyeLe, List<UUID> pieces) {
	}

	record Appel(UUID id, UUID lancePar, String salle, Instant lanceLe) {
	}

	private static final SecureRandom HASARD = new SecureRandom();

	private static final String COLONNES = """
			m.id, m.numero, m.type, m.auteur_id, m.texte, m.point_fait, m.point_prevu, m.point_bloque, m.evenement,
			m.evenement_tour, m.evenement_personne, m.resolu_le, m.resolu_par, m.appel_id, m.envoye_le, m.pieces,
			(a.id IS NOT NULL AND a.termine_le IS NULL AND a.lance_le > :limiteAppel) AS appel_en_cours
			""";

	private final JdbcClient base;

	SalonCercle(JdbcClient base) {
		this.base = base;
	}

	// ---------------------------------------------------------------- lecture

	/** Sans `apres` : les `limite` derniers. Avec : ceux arrivés depuis. Toujours du plus ancien au plus récent. */
	List<Message> messages(UUID cercle, Long apres, int limite, Instant maintenant) {
		String choix = apres == null
				? "SELECT * FROM (SELECT * FROM message_cercle WHERE cercle_id = :c ORDER BY numero DESC LIMIT :l) x"
				: "SELECT * FROM message_cercle WHERE cercle_id = :c AND numero > :apres ORDER BY numero LIMIT :l";
		var requete = base.sql("SELECT " + COLONNES + " FROM (" + choix + ") m LEFT JOIN appel_cercle a ON a.id = m.appel_id ORDER BY m.numero")
			.param("c", cercle).param("l", limite).param("limiteAppel", Timestamp.from(maintenant.minus(DUREE_APPEL)));
		if (apres != null) {
			requete = requete.param("apres", apres);
		}
		return requete.query(SalonCercle::message).list();
	}

	Optional<Message> message(UUID cercle, UUID id, Instant maintenant) {
		return base.sql("SELECT " + COLONNES + " FROM message_cercle m LEFT JOIN appel_cercle a ON a.id = m.appel_id WHERE m.cercle_id = :c AND m.id = :id")
			.param("c", cercle).param("id", id).param("limiteAppel", Timestamp.from(maintenant.minus(DUREE_APPEL)))
			.query(SalonCercle::message).optional();
	}

	/** Les coups de main pas encore résolus, du plus ancien au plus récent : on aide d'abord qui attend depuis longtemps. */
	List<Message> coupsDeMainOuverts(UUID cercle, Instant maintenant) {
		return base.sql("SELECT " + COLONNES + """
				 FROM message_cercle m LEFT JOIN appel_cercle a ON a.id = m.appel_id
				WHERE m.cercle_id = :c AND m.type = 'coup_de_main' AND m.resolu_le IS NULL ORDER BY m.numero LIMIT :l
				""").param("c", cercle).param("l", COUPS_DE_MAIN_MAX)
			.param("limiteAppel", Timestamp.from(maintenant.minus(DUREE_APPEL)))
			.query(SalonCercle::message).list();
	}

	/** Les dernières décisions du cercle, de la plus récente à la plus ancienne. */
	List<Message> decisions(UUID cercle, Instant maintenant) {
		return base.sql("SELECT " + COLONNES + """
				 FROM message_cercle m LEFT JOIN appel_cercle a ON a.id = m.appel_id
				WHERE m.cercle_id = :c AND m.type = 'decision' ORDER BY m.numero DESC LIMIT 30
				""").param("c", cercle).param("limiteAppel", Timestamp.from(maintenant.minus(DUREE_APPEL)))
			.query(SalonCercle::message).list();
	}

	/** Qui a déjà fait son point aujourd'hui (jour UTC, l'heure de Dakar et d'Abidjan). */
	List<UUID> pointsDuJour(UUID cercle, Instant maintenant) {
		return base.sql("""
				SELECT DISTINCT auteur_id FROM message_cercle
				WHERE cercle_id = :c AND type = 'point' AND envoye_le >= :minuit AND auteur_id IS NOT NULL
				""").param("c", cercle).param("minuit", Timestamp.from(minuit(maintenant)))
			.query(UUID.class).list();
	}

	boolean pointDejaFait(UUID cercle, UUID auteur, Instant maintenant) {
		return pointsDuJour(cercle, maintenant).contains(auteur);
	}

	// ---------------------------------------------------------------- écriture

	Message ecrire(UUID cercle, UUID auteur, String texte, Instant maintenant) {
		return ecrire(cercle, auteur, texte, List.of(), maintenant);
	}

	/** Un message libre, avec ses pièces jointes (déjà vérifiées par le module fichiers). */
	Message ecrire(UUID cercle, UUID auteur, String texte, List<UUID> pieces, Instant maintenant) {
		return inserer(cercle, auteur, "texte", texte, null, null, null, null, null, null, null, pieces, maintenant);
	}

	Message point(UUID cercle, UUID auteur, String fait, String prevu, String bloque, Instant maintenant) {
		return inserer(cercle, auteur, "point", null, fait, prevu, bloque, null, null, null, null, List.of(), maintenant);
	}

	Message decision(UUID cercle, UUID auteur, String texte, Instant maintenant) {
		return decision(cercle, auteur, texte, List.of(), maintenant);
	}

	Message decision(UUID cercle, UUID auteur, String texte, List<UUID> pieces, Instant maintenant) {
		return inserer(cercle, auteur, "decision", texte, null, null, null, null, null, null, null, pieces, maintenant);
	}

	Message coupDeMain(UUID cercle, UUID auteur, String texte, Instant maintenant) {
		return coupDeMain(cercle, auteur, texte, List.of(), maintenant);
	}

	Message coupDeMain(UUID cercle, UUID auteur, String texte, List<UUID> pieces, Instant maintenant) {
		return inserer(cercle, auteur, "coup_de_main", texte, null, null, null, null, null, null, null, pieces, maintenant);
	}

	/** Écrit par Jamii quand le cercle avance. `personne` : de qui parle l'événement (peut être null). */
	void evenement(UUID cercle, String evenement, Integer tour, UUID personne, Instant maintenant) {
		inserer(cercle, null, "evenement", null, null, null, null, evenement, tour, personne, null, List.of(), maintenant);
	}

	/** Marque un coup de main résolu. Sans effet s'il l'est déjà (le premier qui l'a résolu reste). */
	void resoudre(UUID message, UUID par, Instant maintenant) {
		base.sql("UPDATE message_cercle SET resolu_le = :t, resolu_par = :u WHERE id = :id AND type = 'coup_de_main' AND resolu_le IS NULL")
			.param("t", Timestamp.from(maintenant)).param("u", par).param("id", message).update();
	}

	private Message inserer(UUID cercle, UUID auteur, String type, String texte, String fait, String prevu, String bloque,
			String evenement, Integer tour, UUID personne, UUID appel, List<UUID> pieces, Instant maintenant) {
		UUID id = UUID.randomUUID();
		long numero = base.sql("""
				INSERT INTO message_cercle (id, cercle_id, auteur_id, type, texte, point_fait, point_prevu, point_bloque,
				                            evenement, evenement_tour, evenement_personne, appel_id, envoye_le, pieces)
				VALUES (:id, :c, :a, :type, :texte, :fait, :prevu, :bloque, :ev, :tour, :p, :appel, :le, CAST(:pieces AS UUID[]))
				RETURNING numero
				""")
			.param("id", id).param("c", cercle).param("a", auteur).param("type", type).param("texte", texte)
			.param("fait", fait).param("prevu", prevu).param("bloque", bloque).param("ev", evenement).param("tour", tour)
			.param("p", personne).param("appel", appel).param("le", Timestamp.from(maintenant)).param("pieces", tableau(pieces))
			.query(Long.class).single();
		return new Message(id, numero, type, auteur, texte, fait, prevu, bloque, evenement, tour, personne, null, null, appel,
				appel != null, maintenant, pieces);
	}

	/** Secondes à attendre avant d'écrire encore, 0 si c'est possible tout de suite. */
	long attenteAvantMessage(UUID auteur, Instant maintenant) {
		return attente(base.sql("SELECT envoye_le AS le FROM message_cercle WHERE auteur_id = :a AND envoye_le > :depuis ORDER BY envoye_le"),
				auteur, maintenant, Duration.ofMinutes(1), MESSAGES_PAR_MINUTE);
	}

	// ---------------------------------------------------------------- les appels

	Optional<Appel> appelEnCours(UUID cercle, Instant maintenant) {
		return base.sql("""
				SELECT id, lance_par, salle, lance_le FROM appel_cercle
				WHERE cercle_id = :c AND termine_le IS NULL AND lance_le > :limite
				""").param("c", cercle).param("limite", Timestamp.from(maintenant.minus(DUREE_APPEL)))
			.query((rs, n) -> new Appel(rs.getObject("id", UUID.class), rs.getObject("lance_par", UUID.class),
					rs.getString("salle"), rs.getTimestamp("lance_le").toInstant()))
			.optional();
	}

	/** Lance un appel avec une salle neuve et l'annonce dans le salon. Un appel resté ouvert plus d'une heure est clos. */
	Appel lancer(UUID cercle, UUID auteur, Instant maintenant) {
		base.sql("""
				UPDATE appel_cercle SET termine_le = lance_le + make_interval(hours => 1)
				WHERE cercle_id = :c AND termine_le IS NULL AND lance_le <= :limite
				""").param("c", cercle).param("limite", Timestamp.from(maintenant.minus(DUREE_APPEL))).update();
		byte[] octets = new byte[8];
		HASARD.nextBytes(octets);
		Appel appel = new Appel(UUID.randomUUID(), auteur, "jamii-cercle-" + HexFormat.of().formatHex(octets), maintenant);
		base.sql("INSERT INTO appel_cercle (id, cercle_id, lance_par, salle, lance_le) VALUES (:id, :c, :u, :salle, :le)")
			.param("id", appel.id()).param("c", cercle).param("u", auteur).param("salle", appel.salle())
			.param("le", Timestamp.from(maintenant)).update();
		inserer(cercle, auteur, "appel", null, null, null, null, null, null, null, appel.id(), List.of(), maintenant);
		return appel;
	}

	void terminer(UUID appel, Instant maintenant) {
		base.sql("UPDATE appel_cercle SET termine_le = :t WHERE id = :id AND termine_le IS NULL")
			.param("t", Timestamp.from(maintenant)).param("id", appel).update();
	}

	long attenteAvantAppel(UUID auteur, Instant maintenant) {
		return attente(base.sql("SELECT lance_le AS le FROM appel_cercle WHERE lance_par = :a AND lance_le > :depuis ORDER BY lance_le"),
				auteur, maintenant, Duration.ofHours(1), APPELS_PAR_HEURE);
	}

	// ---------------------------------------------------------------- outils

	static Instant minuit(Instant maintenant) {
		return maintenant.atZone(ZoneOffset.UTC).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant();
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
		Timestamp resolu = rs.getTimestamp("resolu_le");
		int tour = rs.getInt("evenement_tour");
		Integer leTour = rs.wasNull() ? null : tour;
		return new Message(rs.getObject("id", UUID.class), rs.getLong("numero"), rs.getString("type"),
				rs.getObject("auteur_id", UUID.class), rs.getString("texte"), rs.getString("point_fait"),
				rs.getString("point_prevu"), rs.getString("point_bloque"), rs.getString("evenement"), leTour,
				rs.getObject("evenement_personne", UUID.class), resolu == null ? null : resolu.toInstant(),
				rs.getObject("resolu_par", UUID.class), rs.getObject("appel_id", UUID.class), rs.getBoolean("appel_en_cours"),
				rs.getTimestamp("envoye_le").toInstant(), pieces(rs));
	}

	/** Un tableau PostgreSQL de UUID, écrit en texte (`{a,b}`) : portable quel que soit le pilote. */
	static String tableau(List<UUID> ids) {
		return "{" + String.join(",", ids.stream().map(UUID::toString).toList()) + "}";
	}

	static List<UUID> pieces(ResultSet rs) throws SQLException {
		java.sql.Array a = rs.getArray("pieces");
		if (a == null) {
			return List.of();
		}
		Object[] valeurs = (Object[]) a.getArray();
		return java.util.Arrays.stream(valeurs).map(v -> v instanceof UUID u ? u : UUID.fromString(v.toString())).toList();
	}

}
