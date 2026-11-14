package dev.jamii.api.missions;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Le suivi d'une mission attribuée (contracts/missions.yaml, tag « suivi ») : le client donne les informations de
 * départ, le développeur livre chaque jalon, le client l'accepte ou demande une retouche. **Sans réponse 72 heures après
 * la livraison, le jalon est accepté** ; le dernier jalon accepté termine la mission.
 *
 * <p>Chaque action verrouille la mission et vérifie la `version` lue (409 `modification_concurrente`).
 */
@Service
class Livraisons {

	/** Sans réponse du client, un jalon livré est accepté au bout de ce délai. */
	static final Duration ACCEPTATION_AUTOMATIQUE = Duration.ofHours(72);

	record Livraison(String message, List<String> liens, Instant livreeLe, String retouche) {
	}

	/** Le suivi d'un jalon, pour la vue. */
	record SuiviJalon(Instant livreLe, Instant accepteLe, boolean acceptationAutomatique, String paiement, List<Livraison> livraisons) {
	}

	/** Le départ de la mission, pour la vue. */
	record Depart(String informations, Instant demarreLe, int retouchesUtilisees) {
	}

	private final JdbcClient base;

	private final Missions missions;

	private final Paiements paiements;

	private final Clock horloge;

	Livraisons(JdbcClient base, Missions missions, Paiements paiements, ObjectProvider<Clock> horloge) {
		this.base = base;
		this.missions = missions;
		this.paiements = paiements;
		this.horloge = horloge.getIfAvailable(Clock::systemUTC);
	}

	// ---------------------------------------------------------------- démarrer

	/** Le client donne les informations de départ : `attribuee` → `en_cours`. Le délai de chaque jalon commence ici. */
	@Transactional
	void demarrer(UUID missionId, UUID moi, int version, String informations) {
		Missions.Mission m = missions.clientDe(missionId, moi);
		verifierVersion(m, version);
		if (!"attribuee".equals(m.statut())) {
			throw ErreurMissions.conflit("etat_invalide", "Seule une mission attribuée peut démarrer");
		}
		base.sql("UPDATE mission SET statut = 'en_cours', informations_depart = :i, demarre_le = :t, version = version + 1 WHERE id = :m")
			.param("i", informations).param("t", Timestamp.from(horloge.instant())).param("m", missionId).update();
		base.sql("UPDATE jalon_mission SET statut = 'en_cours' WHERE devis_id = (" + DEVIS_ACCEPTE + ")").param("m", missionId).update();
	}

	// ---------------------------------------------------------------- livrer, accepter, retoucher

	/** Le développeur retenu livre un jalon en cours : `en_cours` → `livre`. Avant le démarrage : 409 `etat_invalide`. */
	@Transactional
	void livrer(UUID missionId, UUID jalonId, UUID moi, int version, String message, List<String> liens) {
		Missions.Mission m = developpeurDe(missionId, moi);
		verifierVersion(m, version);
		String statut = statutJalon(missionId, jalonId);
		if (!"en_cours".equals(m.statut()) || !"en_cours".equals(statut)) {
			throw ErreurMissions.conflit("etat_invalide", "Ce jalon ne peut pas être livré maintenant");
		}
		Instant maintenant = horloge.instant();
		base.sql("INSERT INTO livraison (id, jalon_id, message, liens, livree_le) VALUES (:id, :j, :msg, :liens, :t)")
			.param("id", UUID.randomUUID()).param("j", jalonId).param("msg", message).param("liens", liens.toArray(String[]::new))
			.param("t", Timestamp.from(maintenant)).update();
		base.sql("UPDATE jalon_mission SET statut = 'livre', livre_le = :t WHERE id = :j").param("t", Timestamp.from(maintenant))
			.param("j", jalonId).update();
		incrementerVersion(missionId);
	}

	/** Le client accepte un jalon livré : `livre` → `accepte`. Le dernier jalon accepté termine la mission. */
	@Transactional
	void accepter(UUID missionId, UUID jalonId, UUID moi, int version) {
		Missions.Mission m = missions.clientDe(missionId, moi);
		verifierVersion(m, version);
		if (!"en_cours".equals(m.statut()) || !"livre".equals(statutJalon(missionId, jalonId))) {
			throw ErreurMissions.conflit("etat_invalide", "Seul un jalon livré peut être accepté");
		}
		base.sql("UPDATE jalon_mission SET statut = 'accepte', accepte_le = :t WHERE id = :j")
			.param("t", Timestamp.from(horloge.instant())).param("j", jalonId).update();
		incrementerVersion(missionId);
		terminerSiFini(missionId);
	}

	/**
	 * Le client demande une retouche sur un jalon livré : `livre` → `en_cours`. Dans la limite des retouches du devis,
	 * pour toute la mission (409 `retouches_epuisees`). La raison est attachée à la dernière livraison.
	 */
	@Transactional
	void demanderRetouche(UUID missionId, UUID jalonId, UUID moi, int version, String raison) {
		Missions.Mission m = missions.clientDe(missionId, moi);
		verifierVersion(m, version);
		if (!"en_cours".equals(m.statut()) || !"livre".equals(statutJalon(missionId, jalonId))) {
			throw ErreurMissions.conflit("etat_invalide", "Une retouche se demande sur un jalon livré");
		}
		if (retouchesRestantes(missionId) <= 0) {
			throw ErreurMissions.conflit("retouches_epuisees", "Toutes les retouches prévues au devis ont été utilisées");
		}
		base.sql("""
				UPDATE livraison SET retouche = :r WHERE id = (
				  SELECT id FROM livraison WHERE jalon_id = :j ORDER BY livree_le DESC, id DESC LIMIT 1)
				""").param("r", raison).param("j", jalonId).update();
		base.sql("UPDATE jalon_mission SET statut = 'en_cours', livre_le = NULL WHERE id = :j").param("j", jalonId).update();
		base.sql("UPDATE mission SET retouches_utilisees = retouches_utilisees + 1, version = version + 1 WHERE id = :m")
			.param("m", missionId).update();
	}

	// ---------------------------------------------------------------- paiement

	/**
	 * Déclarer un paiement fait hors de Jamii : le client « payé », puis le développeur « reçu ». Possible dès que le
	 * jalon est livré ; dans le désordre : 409 `etat_invalide`.
	 */
	@Transactional
	void declarerPaiement(UUID missionId, UUID jalonId, UUID moi, int version, Paiements.Sens sens) {
		Missions.Mission m = sens == Paiements.Sens.PAYE ? missions.clientDe(missionId, moi) : developpeurDe(missionId, moi);
		verifierVersion(m, version);
		String statut = statutJalon(missionId, jalonId);
		boolean ouverte = "en_cours".equals(m.statut()) || "terminee".equals(m.statut());
		if (!ouverte || !("livre".equals(statut) || "accepte".equals(statut))) {
			throw ErreurMissions.conflit("etat_invalide", "Un paiement se déclare après la livraison");
		}
		paiements.declarer(jalonId, sens);
		incrementerVersion(missionId);
	}

	// ---------------------------------------------------------------- acceptation automatique

	/**
	 * Accepte chaque jalon livré depuis plus de 72 heures, à l'instant exact où le délai a expiré. La condition `statut =
	 * 'livre'` départage deux exécutions simultanées : un jalon n'est accepté qu'une fois. Renvoie combien l'ont été.
	 */
	@Transactional
	int accepterAutomatiquement() {
		Instant limite = horloge.instant().minus(ACCEPTATION_AUTOMATIQUE);
		List<UUID> missionsTouchees = base.sql("""
				UPDATE jalon_mission j SET statut = 'accepte', accepte_le = j.livre_le + INTERVAL '72 hours', acceptation_automatique = TRUE
				FROM devis d WHERE d.id = j.devis_id AND j.statut = 'livre' AND j.livre_le <= :limite
				RETURNING d.mission_id
				""").param("limite", Timestamp.from(limite)).query(UUID.class).list();
		for (UUID m : missionsTouchees.stream().distinct().toList()) {
			incrementerVersion(m);
			terminerSiFini(m);
		}
		return missionsTouchees.size();
	}

	// ---------------------------------------------------------------- lectures pour la vue

	@Transactional(readOnly = true)
	Depart depart(UUID missionId) {
		return base.sql("SELECT informations_depart, demarre_le, retouches_utilisees FROM mission WHERE id = :m").param("m", missionId)
			.query((rs, i) -> {
				Timestamp t = rs.getTimestamp("demarre_le");
				return new Depart(rs.getString("informations_depart"), t == null ? null : t.toInstant(), rs.getInt("retouches_utilisees"));
			})
			.single();
	}

	/** Le suivi de chaque jalon d'un devis, avec ses livraisons dans l'ordre. */
	@Transactional(readOnly = true)
	Map<UUID, SuiviJalon> suivi(UUID devisId) {
		Map<UUID, List<Livraison>> livraisons = new HashMap<>();
		base.sql("""
				SELECT l.jalon_id, l.message, l.liens, l.livree_le, l.retouche FROM livraison l
				JOIN jalon_mission j ON j.id = l.jalon_id WHERE j.devis_id = :d ORDER BY l.livree_le, l.id
				""").param("d", devisId)
			.query((rs, i) -> livraisons.computeIfAbsent(rs.getObject("jalon_id", UUID.class), k -> new ArrayList<>())
				.add(new Livraison(rs.getString("message"), List.of((String[]) rs.getArray("liens").getArray()),
						rs.getTimestamp("livree_le").toInstant(), rs.getString("retouche"))))
			.list();
		Map<UUID, SuiviJalon> suivi = new HashMap<>();
		base.sql("SELECT id, livre_le, accepte_le, acceptation_automatique, paiement FROM jalon_mission WHERE devis_id = :d")
			.param("d", devisId)
			.query((rs, i) -> {
				UUID id = rs.getObject("id", UUID.class);
				Timestamp livre = rs.getTimestamp("livre_le");
				Timestamp accepte = rs.getTimestamp("accepte_le");
				return suivi.put(id, new SuiviJalon(livre == null ? null : livre.toInstant(), accepte == null ? null : accepte.toInstant(),
						rs.getBoolean("acceptation_automatique"), rs.getString("paiement"), livraisons.getOrDefault(id, List.of())));
			})
			.list();
		return suivi;
	}

	// ---------------------------------------------------------------- règles

	/** Le devis accepté de la mission `:m`, en sous-requête. */
	private static final String DEVIS_ACCEPTE = "SELECT id FROM devis WHERE mission_id = :m AND accepte_le IS NOT NULL ORDER BY cree_le DESC LIMIT 1";

	private int retouchesRestantes(UUID missionId) {
		return base.sql("""
				SELECT d.retouches_comprises - m.retouches_utilisees FROM mission m JOIN devis d ON d.mission_id = m.id
				WHERE m.id = :m AND d.accepte_le IS NOT NULL ORDER BY d.cree_le DESC LIMIT 1
				""").param("m", missionId).query(Integer.class).single();
	}

	/** Le statut d'un jalon du devis accepté de cette mission ; 404 s'il n'en fait pas partie. */
	private String statutJalon(UUID missionId, UUID jalonId) {
		return base.sql("SELECT statut FROM jalon_mission WHERE id = :j AND devis_id = (" + DEVIS_ACCEPTE + ")")
			.param("j", jalonId).param("m", missionId).query(String.class).optional().orElseThrow(ErreurMissions::introuvable);
	}

	/** La mission verrouillée, pour son développeur retenu seulement : 404 si `moi` ne la voit pas, 403 sinon. */
	private Missions.Mission developpeurDe(UUID missionId, UUID moi) {
		Missions.Mission m = missions.charger(missionId, true);
		Missions.Lecture l = missions.role(m, moi).orElseThrow(ErreurMissions::introuvable);
		if (!"developpeur_retenu".equals(l.role())) {
			throw ErreurMissions.interdit();
		}
		return m;
	}

	private void terminerSiFini(UUID missionId) {
		base.sql("""
				UPDATE mission SET statut = 'terminee', version = version + 1 WHERE id = :m AND statut = 'en_cours'
				AND NOT EXISTS (SELECT 1 FROM jalon_mission WHERE devis_id = (""" + DEVIS_ACCEPTE + ") AND statut <> 'accepte')")
			.param("m", missionId).update();
	}

	private void incrementerVersion(UUID missionId) {
		base.sql("UPDATE mission SET version = version + 1 WHERE id = :m").param("m", missionId).update();
	}

	private static void verifierVersion(Missions.Mission m, int version) {
		if (m.version() != version) {
			throw ErreurMissions.conflit("modification_concurrente", "La mission a changé entre-temps");
		}
	}

}
