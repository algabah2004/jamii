package dev.jamii.api.reseau;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Les appels en tête-à-tête d'une discussion privée (#157), en WebRTC. L'API ne fait que la mise en relation : elle
 * transmet l'offre de l'appelant et la réponse de l'appelé (des descriptions SDP complètes, sans échange de candidats
 * au fil de l'eau), puis garde l'historique. Le son, l'image, le micro coupé et les réactions passent directement
 * entre les deux navigateurs.
 *
 * <p>Pas de WebSocket : l'appelé demande toutes les 2 secondes si on l'appelle, comme les salons relisent leurs
 * messages. Un appel qui sonne plus de 45 secondes est « manqué ». Les SDP, qui contiennent les adresses réseau,
 * sont effacées dès que l'appel est fini.
 */
@Service
class Appels {

	/** Au-delà, un appel qui sonne est manqué. */
	static final Duration SONNERIE = Duration.ofSeconds(45);

	/** Un appel « en cours » dont personne n'a signalé la fin (onglet fermé) est considéré comme terminé après ce délai. */
	static final Duration DUREE_MAX = Duration.ofHours(4);

	/** Une description SDP réelle fait quelques kilo-octets ; au-delà, ce n'est pas un navigateur. */
	static final int SDP_MAX = 30_000;

	record Appel(UUID id, UUID appelantId, UUID appeleId, UUID messageId, boolean video, String statut, String offre,
			String reponse, Instant creeLe, Instant reponduLe, Instant termineLe) {

		boolean participant(UUID qui) {
			return appelantId.equals(qui) || appeleId.equals(qui);
		}

		UUID autre(UUID moi) {
			return appelantId.equals(moi) ? appeleId : appelantId;
		}

		boolean enCours() {
			return "sonne".equals(statut) || "en_cours".equals(statut);
		}

	}

	private static final String COLONNES = "id, appelant_id, appele_id, message_id, video, statut, offre, reponse, cree_le, repondu_le, termine_le";

	private final JdbcClient base;

	private final Messagerie messagerie;

	private final Clock horloge;

	@org.springframework.beans.factory.annotation.Autowired
	Appels(JdbcClient base, Messagerie messagerie) {
		this(base, messagerie, Clock.systemUTC());
	}

	Appels(JdbcClient base, Messagerie messagerie, Clock horloge) {
		this.base = base;
		this.messagerie = messagerie;
		this.horloge = horloge;
	}

	/**
	 * Appeler quelqu'un : l'appel sonne chez l'autre, et la conversation garde un message qui l'annonce. Mêmes règles
	 * qu'un message (pas à soi-même, limite anti-spam). Un appel qui sonnait déjà entre ces deux personnes est annulé.
	 */
	@Transactional
	Appel lancer(UUID moi, UUID autre, String offre, boolean video) {
		valider("offre", offre);
		Instant maintenant = horloge.instant();
		var message = messagerie.annoncerAppel(moi, autre, video, maintenant);
		base.sql("""
				UPDATE appel_prive SET statut = 'annule', offre = NULL, reponse = NULL, termine_le = :le
				WHERE statut = 'sonne' AND ((appelant_id = :moi AND appele_id = :autre) OR (appelant_id = :autre AND appele_id = :moi))
				""").param("le", Timestamp.from(maintenant)).param("moi", moi).param("autre", autre).update();
		Appel appel = new Appel(UUID.randomUUID(), moi, autre, message.id(), video, "sonne", offre, null, maintenant, null, null);
		base.sql("INSERT INTO appel_prive (" + COLONNES + ") VALUES (:id, :de, :a, :message, :video, 'sonne', :offre, NULL, :le, NULL, NULL)")
			.param("id", appel.id())
			.param("de", moi)
			.param("a", autre)
			.param("message", message.id())
			.param("video", video)
			.param("offre", offre)
			.param("le", Timestamp.from(maintenant))
			.update();
		return appel;
	}

	/** L'appel qui sonne pour moi en ce moment, le plus récent. */
	@Transactional
	Optional<Appel> entrant(UUID moi) {
		expirer();
		return base.sql("SELECT " + COLONNES + " FROM appel_prive WHERE appele_id = :moi AND statut = 'sonne' ORDER BY cree_le DESC LIMIT 1")
			.param("moi", moi).query(Appels::appel).optional();
	}

	/** Un appel, pour l'une des deux personnes seulement : pour toute autre, il n'existe pas. */
	@Transactional
	Appel lire(UUID moi, UUID id) {
		expirer();
		return trouver(moi, id);
	}

	/** L'appelé décroche : sa réponse SDP part chez l'appelant, l'appel est en cours. */
	@Transactional
	Appel repondre(UUID moi, UUID id, String reponse) {
		valider("reponse", reponse);
		expirer();
		Appel appel = trouver(moi, id);
		if (!appel.appeleId().equals(moi)) {
			throw ErreurReseau.conflit("pas_appele", "Seule la personne appelée peut décrocher");
		}
		if (!"sonne".equals(appel.statut())) {
			throw ErreurReseau.conflit("appel_fini", "Cet appel ne sonne plus");
		}
		base.sql("UPDATE appel_prive SET statut = 'en_cours', reponse = :r, repondu_le = :le WHERE id = :id AND statut = 'sonne'")
			.param("r", reponse).param("le", Timestamp.from(horloge.instant())).param("id", id).update();
		return trouver(moi, id);
	}

	/** L'appelé refuse. */
	@Transactional
	Appel refuser(UUID moi, UUID id) {
		Appel appel = trouver(moi, id);
		if (!appel.appeleId().equals(moi)) {
			throw ErreurReseau.conflit("pas_appele", "Seule la personne appelée peut refuser");
		}
		finir(id, "sonne", "refuse");
		return trouver(moi, id);
	}

	/**
	 * Raccrocher, pour l'un comme pour l'autre : un appel qui sonnait est annulé, un appel en cours est terminé. Sans
	 * effet sur un appel déjà fini (les deux personnes peuvent raccrocher en même temps).
	 */
	@Transactional
	Appel terminer(UUID moi, UUID id) {
		trouver(moi, id);
		finir(id, "sonne", "annule");
		finir(id, "en_cours", "termine");
		return trouver(moi, id);
	}

	/** Les appels annoncés par ces messages, pour les cartes de la conversation. */
	Map<UUID, Appel> parMessage(Collection<UUID> messages) {
		if (messages.isEmpty()) {
			return Map.of();
		}
		return base.sql("SELECT " + COLONNES + " FROM appel_prive WHERE message_id IN (:m)")
			.param("m", messages).query(Appels::appel).list().stream()
			.collect(Collectors.toMap(Appel::messageId, Function.identity(), (a, b) -> a));
	}

	/** Les appels qui ont trop sonné sont manqués ; ceux restés « en cours » trop longtemps sont terminés. */
	private void expirer() {
		Instant maintenant = horloge.instant();
		base.sql("UPDATE appel_prive SET statut = 'manque', offre = NULL, termine_le = :le WHERE statut = 'sonne' AND cree_le < :limite")
			.param("le", Timestamp.from(maintenant)).param("limite", Timestamp.from(maintenant.minus(SONNERIE))).update();
		base.sql("""
				UPDATE appel_prive SET statut = 'termine', offre = NULL, reponse = NULL, termine_le = :le
				WHERE statut = 'en_cours' AND repondu_le < :limite""")
			.param("le", Timestamp.from(maintenant)).param("limite", Timestamp.from(maintenant.minus(DUREE_MAX))).update();
	}

	private void finir(UUID id, String depuis, String vers) {
		base.sql("UPDATE appel_prive SET statut = :vers, offre = NULL, reponse = NULL, termine_le = :le WHERE id = :id AND statut = :depuis")
			.param("vers", vers).param("le", Timestamp.from(horloge.instant())).param("id", id).param("depuis", depuis).update();
	}

	private Appel trouver(UUID moi, UUID id) {
		return base.sql("SELECT " + COLONNES + " FROM appel_prive WHERE id = :id").param("id", id).query(Appels::appel)
			.optional().filter(a -> a.participant(moi)).orElseThrow(ErreurReseau::introuvable);
	}

	private static void valider(String champ, String sdp) {
		if (sdp == null || sdp.isBlank()) {
			throw ErreurReseau.validation(champ, "obligatoire");
		}
		if (sdp.length() > SDP_MAX || !sdp.startsWith("v=0")) {
			throw ErreurReseau.validation(champ, "format_invalide");
		}
	}

	private static Appel appel(ResultSet l, int ligne) throws SQLException {
		return new Appel(l.getObject("id", UUID.class), l.getObject("appelant_id", UUID.class),
				l.getObject("appele_id", UUID.class), l.getObject("message_id", UUID.class), l.getBoolean("video"),
				l.getString("statut"), l.getString("offre"), l.getString("reponse"), instant(l, "cree_le"),
				instant(l, "repondu_le"), instant(l, "termine_le"));
	}

	private static Instant instant(ResultSet l, String colonne) throws SQLException {
		Timestamp t = l.getTimestamp(colonne);
		return t == null ? null : t.toInstant();
	}

}
