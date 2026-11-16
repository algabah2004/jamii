package dev.jamii.api.cercles;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * La salle WebRTC de l'appel du cercle (#157) : au plus 6 membres, chacun relié directement à chacun. L'API ne fait
 * que la mise en relation, en HTTP, sans WebSocket :
 *
 * <ul>
 * <li>chaque présent signale toutes les 1,5 s qu'il est là, et reçoit en retour la liste des présents et les offres ou
 * réponses qui lui sont adressées (lues une fois, puis effacées) ;</li>
 * <li>pour chaque paire, c'est le dernier arrivé qui envoie l'offre : les deux navigateurs le savent sans se
 * concerter ;</li>
 * <li>un présent qui ne se signale plus depuis 12 s est parti (onglet fermé, réseau coupé) ; quand plus personne n'est
 * là, l'appel est terminé.</li>
 * </ul>
 */
@Service
class SalleAppelCercle {

	/** Sans signe de vie pendant ce délai, la personne a quitté l'appel. */
	static final Duration ABSENCE = Duration.ofSeconds(12);

	/** Une offre ou une réponse non lue au-delà ne sert plus à rien. */
	static final Duration VIE_SIGNAL = Duration.ofMinutes(1);

	/** Un appel lancé sans que personne ne s'y signale (lanceur parti aussitôt) est clos après ce délai. */
	static final Duration ATTENTE_PREMIER = Duration.ofSeconds(30);

	/** Une description SDP réelle fait quelques kilo-octets. */
	static final int SDP_MAX = 30_000;

	/** Au plus autant de signaux en attente d'une personne à une autre : pas d'inondation de la boîte. */
	static final int SIGNAUX_EN_ATTENTE_MAX = 6;

	record Present(UUID id, Instant arriveLe) {
	}

	record Signal(long id, UUID de, String type, String sdp) {
	}

	record Etat(List<Present> presents, List<Signal> signaux) {
	}

	private final JdbcClient base;

	private final SalonCercle salon;

	SalleAppelCercle(JdbcClient base, SalonCercle salon) {
		this.base = base;
		this.salon = salon;
	}

	/** Je suis là : la liste des présents (moi compris), et ce qui m'attend dans la boîte, effacé à la lecture. */
	@Transactional
	Etat present(UUID appel, UUID moi, Instant maintenant) {
		expirer(appel, maintenant);
		base.sql("""
				INSERT INTO appel_cercle_presence (appel_id, utilisateur_id, arrive_le, vu_le) VALUES (:a, :moi, :t, :t)
				ON CONFLICT (appel_id, utilisateur_id) DO UPDATE SET vu_le = :t
				""").param("a", appel).param("moi", moi).param("t", Timestamp.from(maintenant)).update();
		List<Present> presents = base.sql("SELECT utilisateur_id, arrive_le FROM appel_cercle_presence WHERE appel_id = :a ORDER BY arrive_le, utilisateur_id")
			.param("a", appel)
			.query((rs, n) -> new Present(rs.getObject("utilisateur_id", UUID.class), rs.getTimestamp("arrive_le").toInstant()))
			.list();
		List<Signal> signaux = base.sql("DELETE FROM appel_cercle_signal WHERE appel_id = :a AND a = :moi RETURNING id, de, type, sdp")
			.param("a", appel).param("moi", moi)
			.query((rs, n) -> new Signal(rs.getLong("id"), rs.getObject("de", UUID.class), rs.getString("type"), rs.getString("sdp")))
			.list()
			.stream()
			.sorted(Comparator.comparingLong(Signal::id))
			.toList();
		return new Etat(presents, signaux);
	}

	/** Une offre ou une réponse pour un autre présent. Les deux doivent être dans l'appel. */
	@Transactional
	void envoyer(UUID appel, UUID moi, UUID a, String type, String sdp, Instant maintenant) {
		if (!"offre".equals(type) && !"reponse".equals(type)) {
			throw CerclesController.validation("type", "valeur_invalide");
		}
		if (sdp == null || sdp.isBlank()) {
			throw CerclesController.validation("sdp", "obligatoire");
		}
		if (sdp.length() > SDP_MAX || !sdp.startsWith("v=0")) {
			throw CerclesController.validation("sdp", "format_invalide");
		}
		if (moi.equals(a)) {
			throw CerclesController.validation("a", "valeur_invalide");
		}
		expirer(appel, maintenant);
		int presents = base.sql("SELECT count(*) FROM appel_cercle_presence WHERE appel_id = :appel AND utilisateur_id IN (:moi, :a)")
			.param("appel", appel).param("moi", moi).param("a", a).query(Integer.class).single();
		if (presents < 2) {
			throw new ErreurCercle(org.springframework.http.HttpStatus.CONFLICT, "pas_dans_l_appel", "Les deux personnes doivent être dans l'appel");
		}
		int enAttente = base.sql("SELECT count(*) FROM appel_cercle_signal WHERE appel_id = :appel AND de = :moi AND a = :a")
			.param("appel", appel).param("moi", moi).param("a", a).query(Integer.class).single();
		if (enAttente >= SIGNAUX_EN_ATTENTE_MAX) {
			throw new ErreurCercle(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "trop_de_requetes", "Trop de signaux en attente", null, 2);
		}
		base.sql("INSERT INTO appel_cercle_signal (appel_id, de, a, type, sdp, cree_le) VALUES (:appel, :moi, :a, :type, :sdp, :t)")
			.param("appel", appel).param("moi", moi).param("a", a).param("type", type).param("sdp", sdp)
			.param("t", Timestamp.from(maintenant)).update();
	}

	/** Je quitte l'appel. Le dernier à partir le termine. */
	@Transactional
	void quitter(UUID appel, UUID moi, Instant maintenant) {
		base.sql("DELETE FROM appel_cercle_presence WHERE appel_id = :a AND utilisateur_id = :moi").param("a", appel).param("moi", moi).update();
		base.sql("DELETE FROM appel_cercle_signal WHERE appel_id = :a AND (de = :moi OR a = :moi)").param("a", appel).param("moi", moi).update();
		terminerSiVide(appel, maintenant);
	}

	/** Combien de personnes sont dans l'appel, pour le bandeau du salon. Termine l'appel s'il est vide depuis un moment. */
	@Transactional
	int presents(UUID appel, Instant lanceLe, Instant maintenant) {
		expirer(appel, maintenant);
		int n = base.sql("SELECT count(*) FROM appel_cercle_presence WHERE appel_id = :a").param("a", appel).query(Integer.class).single();
		if (n == 0 && lanceLe.plus(ATTENTE_PREMIER).isBefore(maintenant)) {
			salon.terminer(appel, maintenant);
		}
		return n;
	}

	private void terminerSiVide(UUID appel, Instant maintenant) {
		int n = base.sql("SELECT count(*) FROM appel_cercle_presence WHERE appel_id = :a").param("a", appel).query(Integer.class).single();
		if (n == 0) {
			salon.terminer(appel, maintenant);
		}
	}

	private void expirer(UUID appel, Instant maintenant) {
		List<UUID> partis = base.sql("DELETE FROM appel_cercle_presence WHERE appel_id = :a AND vu_le < :limite RETURNING utilisateur_id")
			.param("a", appel).param("limite", Timestamp.from(maintenant.minus(ABSENCE)))
			.query(UUID.class).list();
		for (UUID parti : partis) {
			base.sql("DELETE FROM appel_cercle_signal WHERE appel_id = :a AND (de = :p OR a = :p)").param("a", appel).param("p", parti).update();
		}
		base.sql("DELETE FROM appel_cercle_signal WHERE appel_id = :a AND cree_le < :limite")
			.param("a", appel).param("limite", Timestamp.from(maintenant.minus(VIE_SIGNAL))).update();
		if (!partis.isEmpty()) {
			terminerSiVide(appel, maintenant);
		}
	}

}
