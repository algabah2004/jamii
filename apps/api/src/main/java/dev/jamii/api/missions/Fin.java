package dev.jamii.api.missions;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Finir une mission autrement que par la dernière acceptation (contracts/missions.yaml, tag « fin ») : l'annuler, ou
 * ouvrir un désaccord qu'un modérateur tranche.
 *
 * <p>Annuler **seul** n'est permis que dans deux cas, pour protéger l'autre partie : le développeur, si le client n'a
 * pas donné les informations de départ 7 jours après l'attribution ; le client, si un jalon a plus de 7 jours de retard.
 * Sinon, il faut l'accord de l'autre partie.
 */
@Service
class Fin {

	/** Le délai après lequel une partie peut annuler seule. */
	static final Duration DELAI_ANNULATION_SEUL = Duration.ofDays(7);

	record Desaccord(String ouvertPar, String raison, Instant ouvertLe, String issue, String explication, Instant trancheLe) {
	}

	private final JdbcClient base;

	private final Missions missions;

	private final Clock horloge;

	private final ApplicationEventPublisher evenements;

	Fin(JdbcClient base, Missions missions, ObjectProvider<Clock> horloge, ApplicationEventPublisher evenements) {
		this.base = base;
		this.missions = missions;
		this.horloge = horloge.getIfAvailable(Clock::systemUTC);
		this.evenements = evenements;
	}

	// ---------------------------------------------------------------- annuler

	/**
	 * Demander ou confirmer l'annulation. Dans l'ordre : l'autre partie l'a déjà demandée (commun accord) ; je peux
	 * annuler seul (les deux cas prévus) ; un jalon livré attend une réponse (409 `annulation_non_permise` : il faut un
	 * désaccord) ; sinon ma demande est enregistrée et attend l'autre partie.
	 */
	@Transactional
	void annuler(UUID missionId, UUID moi, int version, String raison) {
		Missions.Mission m = missions.charger(missionId, true);
		String partie = partie(m, moi);
		verifierVersion(m, version);
		if (!"attribuee".equals(m.statut()) && !"en_cours".equals(m.statut())) {
			throw ErreurMissions.conflit("etat_invalide", "Cette mission ne peut plus être annulée");
		}
		String demandeePar = base.sql("SELECT annulation_demandee_par FROM mission WHERE id = :m").param("m", missionId)
			.query((rs, i) -> Optional.ofNullable(rs.getString(1))).single().orElse(null);
		if (demandeePar != null && !demandeePar.equals(partie) || peutAnnulerSeul(m, partie)) {
			base.sql("UPDATE mission SET statut = 'annulee', annulation_raison = COALESCE(annulation_raison, :r), version = version + 1 WHERE id = :m")
				.param("r", raison).param("m", missionId).update();
			return;
		}
		if (unJalonLivre(missionId)) {
			throw ErreurMissions.conflit("annulation_non_permise", "Un jalon livré attend une réponse : ouvrez plutôt un désaccord");
		}
		base.sql("UPDATE mission SET annulation_demandee_par = :p, annulation_raison = :r, version = version + 1 WHERE id = :m")
			.param("p", partie).param("r", raison).param("m", missionId).update();
	}

	private boolean peutAnnulerSeul(Missions.Mission m, String partie) {
		Instant limite = horloge.instant().minus(DELAI_ANNULATION_SEUL);
		if ("developpeur".equals(partie)) {
			// Le client n'a pas donné les informations de départ, 7 jours après avoir accepté le devis.
			return "attribuee".equals(m.statut()) && base.sql("""
					SELECT EXISTS (SELECT 1 FROM devis WHERE mission_id = :m AND accepte_le IS NOT NULL AND accepte_le <= :limite)
					""").param("m", m.id()).param("limite", Timestamp.from(limite)).query(Boolean.class).single();
		}
		// Un jalon pas encore livré a dépassé son échéance de plus de 7 jours.
		return "en_cours".equals(m.statut()) && base.sql("""
				SELECT EXISTS (
				  SELECT 1 FROM jalon_mission j JOIN devis d ON d.id = j.devis_id JOIN mission mi ON mi.id = d.mission_id
				  WHERE d.mission_id = :m AND d.accepte_le IS NOT NULL AND j.statut = 'en_cours'
				    AND mi.demarre_le + make_interval(days => j.delai_jours) <= :limite)
				""").param("m", m.id()).param("limite", Timestamp.from(limite)).query(Boolean.class).single();
	}

	// ---------------------------------------------------------------- désaccord

	/** Ouvrir un désaccord : `en_cours` → `desaccord`. Livraisons, retouches et acceptations automatiques sont gelées. */
	@Transactional
	void ouvrirDesaccord(UUID missionId, UUID moi, int version, String raison) {
		Missions.Mission m = missions.charger(missionId, true);
		String partie = partie(m, moi);
		verifierVersion(m, version);
		if (!"en_cours".equals(m.statut())) {
			throw ErreurMissions.conflit("etat_invalide", "Un désaccord s'ouvre sur une mission en cours");
		}
		base.sql("INSERT INTO desaccord (mission_id, ouvert_par, raison, ouvert_le) VALUES (:m, :p, :r, :t)")
			.param("m", missionId).param("p", partie).param("r", raison).param("t", Timestamp.from(horloge.instant())).update();
		base.sql("UPDATE mission SET statut = 'desaccord', version = version + 1 WHERE id = :m").param("m", missionId).update();
	}

	/** Trancher (modérateur seulement, vérifié par le contrôleur) : `desaccord` → `terminee` ou `annulee`. */
	@Transactional
	void trancher(UUID missionId, UUID moderateur, int version, String issue, String explication) {
		Missions.Mission m = missions.charger(missionId, true);
		verifierVersion(m, version);
		if (!"desaccord".equals(m.statut())) {
			throw ErreurMissions.conflit("etat_invalide", "Cette mission n'a pas de désaccord en cours");
		}
		base.sql("UPDATE desaccord SET issue = :i, explication = :e, tranche_par = :p, tranche_le = :t WHERE mission_id = :m")
			.param("i", issue).param("e", explication).param("p", moderateur).param("t", Timestamp.from(horloge.instant()))
			.param("m", missionId).update();
		base.sql("UPDATE mission SET statut = :s, version = version + 1 WHERE id = :m").param("s", issue).param("m", missionId).update();
		if ("terminee".equals(issue)) {
			evenements.publishEvent(new MissionTerminee(missionId, m.clientId(), m.developpeurId(), m.titre(), horloge.instant()));
		}
	}

	// ---------------------------------------------------------------- lectures pour la vue

	@Transactional(readOnly = true)
	Optional<Desaccord> desaccord(UUID missionId) {
		return base.sql("SELECT ouvert_par, raison, ouvert_le, issue, explication, tranche_le FROM desaccord WHERE mission_id = :m")
			.param("m", missionId)
			.query((rs, i) -> {
				Timestamp tranche = rs.getTimestamp("tranche_le");
				return new Desaccord(rs.getString("ouvert_par"), rs.getString("raison"), rs.getTimestamp("ouvert_le").toInstant(),
						rs.getString("issue"), rs.getString("explication"), tranche == null ? null : tranche.toInstant());
			})
			.optional();
	}

	@Transactional(readOnly = true)
	String annulationDemandeePar(UUID missionId) {
		return base.sql("SELECT annulation_demandee_par FROM mission WHERE id = :m").param("m", missionId)
			.query((rs, i) -> Optional.ofNullable(rs.getString(1))).single().orElse(null);
	}

	// ---------------------------------------------------------------- règles

	/** `client` ou `developpeur` ; 404 si `moi` ne voit pas la mission, 403 s'il n'en est pas une partie. */
	private String partie(Missions.Mission m, UUID moi) {
		Missions.Lecture l = missions.role(m, moi).orElseThrow(ErreurMissions::introuvable);
		return switch (l.role()) {
			case "client" -> "client";
			case "developpeur_retenu" -> "developpeur";
			default -> throw ErreurMissions.interdit();
		};
	}

	private boolean unJalonLivre(UUID missionId) {
		return base.sql("""
				SELECT EXISTS (SELECT 1 FROM jalon_mission j JOIN devis d ON d.id = j.devis_id
				  WHERE d.mission_id = :m AND d.accepte_le IS NOT NULL AND j.statut = 'livre')
				""").param("m", missionId).query(Boolean.class).single();
	}

	private static void verifierVersion(Missions.Mission m, int version) {
		if (m.version() != version) {
			throw ErreurMissions.conflit("modification_concurrente", "La mission a changé entre-temps");
		}
	}

}
