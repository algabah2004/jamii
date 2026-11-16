package dev.jamii.api.reseau;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jamii.api.auth.DonneesPersonnelles;

/**
 * Les abonnés (#157) : suivre quelqu'un sans son accord, comme sur LinkedIn. Les contacts restent réciproques.
 * Suivre donne des notifications : « X te suit », puis quand la personne suivie ouvre un cercle ou publie dans une
 * communauté. Contre le spam : 200 nouveaux abonnements par jour au plus, et « X te suit » une fois par personne.
 */
@Service
class Abonnements implements DonneesPersonnelles {

	static final int PAR_JOUR = 200;

	static final int LISTE_MAX = 50;

	/** Au-delà, on ne prévient pas tout le monde d'un coup : les premiers abonnés, puis les plus récents. */
	static final int DIFFUSION_MAX = 5_000;

	record Suivi(long abonnes, long abonnements, boolean jeSuis, boolean meSuit) {
	}

	record Lien(UUID personneId, Instant depuis) {
	}

	private final JdbcClient base;

	private final Notifications notifications;

	Abonnements(JdbcClient base, Notifications notifications) {
		this.base = base;
		this.notifications = notifications;
	}

	boolean existe(UUID personne) {
		return base.sql("SELECT EXISTS (SELECT 1 FROM utilisateur WHERE id = :id)").param("id", personne).query(Boolean.class).single();
	}

	Suivi suivi(UUID moi, UUID personne) {
		return base.sql("""
				SELECT (SELECT count(*) FROM abonnement WHERE suivi_id = :p) AS abonnes,
				       (SELECT count(*) FROM abonnement WHERE abonne_id = :p) AS abonnements,
				       EXISTS (SELECT 1 FROM abonnement WHERE abonne_id = :moi AND suivi_id = :p) AS je_suis,
				       EXISTS (SELECT 1 FROM abonnement WHERE abonne_id = :p AND suivi_id = :moi) AS me_suit
				""").param("p", personne).param("moi", moi)
			.query((rs, n) -> new Suivi(rs.getLong("abonnes"), rs.getLong("abonnements"), rs.getBoolean("je_suis"), rs.getBoolean("me_suit")))
			.single();
	}

	@Transactional
	void suivre(UUID moi, UUID personne, Instant maintenant) {
		if (moi.equals(personne)) {
			throw ErreurReseau.conflit("soi_meme", "On ne se suit pas soi-même");
		}
		if (!existe(personne)) {
			throw ErreurReseau.introuvable();
		}
		long recents = base.sql("SELECT count(*) FROM abonnement WHERE abonne_id = :moi AND depuis > :depuis")
			.param("moi", moi).param("depuis", Timestamp.from(maintenant.minus(Duration.ofDays(1)))).query(Long.class).single();
		if (recents >= PAR_JOUR) {
			throw ErreurReseau.tropDeRequetes(3600);
		}
		int ajoutes = base.sql("""
				INSERT INTO abonnement (abonne_id, suivi_id, depuis) VALUES (:moi, :p, :le) ON CONFLICT DO NOTHING
				""").param("moi", moi).param("p", personne).param("le", Timestamp.from(maintenant)).update();
		// Une seule fois par personne, même si elle se désabonne puis se réabonne : pas de notification en rafale.
		boolean dejaPrevenu = base.sql("SELECT EXISTS (SELECT 1 FROM notification WHERE utilisateur_id = :p AND type = 'nouvel_abonne' AND personne_id = :moi)")
			.param("p", personne).param("moi", moi).query(Boolean.class).single();
		if (ajoutes > 0 && !dejaPrevenu) {
			notifications.notifier(personne, "nouvel_abonne", moi, null, "/p/" + moi);
		}
	}

	void neplusSuivre(UUID moi, UUID personne) {
		base.sql("DELETE FROM abonnement WHERE abonne_id = :moi AND suivi_id = :p").param("moi", moi).param("p", personne).update();
	}

	/** Les abonnés de `personne` (ou ses abonnements), les plus récents d'abord. */
	List<Lien> lister(UUID personne, boolean abonnes, int limite) {
		String sql = abonnes
				? "SELECT abonne_id AS autre, depuis FROM abonnement WHERE suivi_id = :p ORDER BY depuis DESC, abonne_id LIMIT :l"
				: "SELECT suivi_id AS autre, depuis FROM abonnement WHERE abonne_id = :p ORDER BY depuis DESC, suivi_id LIMIT :l";
		return base.sql(sql).param("p", personne).param("l", limite)
			.query((rs, n) -> new Lien(rs.getObject("autre", UUID.class), rs.getTimestamp("depuis").toInstant()))
			.list();
	}

	/**
	 * Prévient les abonnés de `auteur` (une notification par abonné, rafraîchie si elle n'est pas encore lue). Une seule
	 * requête, quel que soit le nombre d'abonnés.
	 */
	@Transactional
	int prevenirAbonnes(UUID auteur, String type, UUID cercleId, String lien, Instant maintenant) {
		Timestamp le = Timestamp.from(maintenant);
		base.sql("""
				UPDATE notification n SET cree_le = :le, cercle_id = :cercle, lien = :lien
				FROM abonnement a
				WHERE a.suivi_id = :auteur AND n.utilisateur_id = a.abonne_id AND n.type = :type AND n.personne_id = :auteur
				  AND n.lu_le IS NULL
				""").param("le", le).param("cercle", cercleId).param("lien", lien).param("auteur", auteur).param("type", type)
			.update();
		return base.sql("""
				INSERT INTO notification (id, utilisateur_id, type, personne_id, cercle_id, lien, cree_le)
				SELECT gen_random_uuid(), a.abonne_id, :type, :auteur, :cercle, :lien, :le
				FROM (SELECT abonne_id FROM abonnement WHERE suivi_id = :auteur ORDER BY depuis LIMIT :max) a
				WHERE NOT EXISTS (SELECT 1 FROM notification n WHERE n.utilisateur_id = a.abonne_id AND n.type = :type
				                  AND n.personne_id = :auteur AND n.lu_le IS NULL)
				""").param("type", type).param("auteur", auteur).param("cercle", cercleId).param("lien", lien).param("le", le)
			.param("max", DIFFUSION_MAX).update();
	}

	@Override
	public String section() {
		return "abonnements";
	}

	/** Qui je suis et qui me suit : seulement des identifiants pour les autres. */
	@Override
	public Object exporter(UUID moi) {
		Map<String, Object> donnees = new HashMap<>();
		donnees.put("jeSuis", base.sql("SELECT suivi_id, depuis FROM abonnement WHERE abonne_id = :moi ORDER BY depuis")
			.param("moi", moi).query((rs, n) -> Map.of("personne", rs.getObject("suivi_id", UUID.class), "depuis", rs.getTimestamp("depuis").toInstant().toString())).list());
		donnees.put("meSuivent", base.sql("SELECT abonne_id, depuis FROM abonnement WHERE suivi_id = :moi ORDER BY depuis")
			.param("moi", moi).query((rs, n) -> Map.of("personne", rs.getObject("abonne_id", UUID.class), "depuis", rs.getTimestamp("depuis").toInstant().toString())).list());
		return donnees;
	}

}
