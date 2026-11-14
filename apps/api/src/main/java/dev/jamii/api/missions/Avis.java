package dev.jamii.api.missions;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Les avis d'une mission (contracts/missions.yaml, `donnerAvis`) : une fois la mission terminée, chaque partie laisse
 * un avis sur l'autre, une seule fois. Un avis ne porte pas le nom de son auteur, seulement son rôle.
 */
@Service
class Avis {

	record UnAvis(String auteur, int note, String texte, Instant donneLe) {
	}

	private final JdbcClient base;

	private final Missions missions;

	private final Clock horloge;

	Avis(JdbcClient base, Missions missions, ObjectProvider<Clock> horloge) {
		this.base = base;
		this.missions = missions;
		this.horloge = horloge.getIfAvailable(Clock::systemUTC);
	}

	/**
	 * Donner mon avis. Dans l'ordre : je vois la mission (404), j'en suis une partie (403), elle est terminée (409
	 * `etat_invalide`), je n'ai pas déjà donné le mien (409 `avis_deja_donne`, même avec deux envois simultanés).
	 */
	@Transactional
	UnAvis donner(UUID missionId, UUID moi, int note, String texte) {
		Missions.Mission m = missions.charger(missionId, false);
		String role = missions.role(m, moi).orElseThrow(ErreurMissions::introuvable).role();
		String auteur = switch (role) {
			case "client" -> "client";
			case "developpeur_retenu" -> "developpeur";
			default -> throw ErreurMissions.interdit();
		};
		if (!"terminee".equals(m.statut())) {
			throw ErreurMissions.conflit("etat_invalide", "Un avis se donne une fois la mission terminée");
		}
		Instant maintenant = horloge.instant();
		try {
			base.sql("INSERT INTO avis_mission (mission_id, auteur, auteur_id, note, texte, donne_le) VALUES (:m, :a, :u, :n, :t, :d)")
				.param("m", missionId).param("a", auteur).param("u", moi).param("n", note).param("t", texte, java.sql.Types.VARCHAR)
				.param("d", Timestamp.from(maintenant)).update();
		}
		catch (DuplicateKeyException e) {
			throw ErreurMissions.conflit("avis_deja_donne", "Tu as déjà donné ton avis sur cette mission");
		}
		return new UnAvis(auteur, note, texte, maintenant);
	}

	/** Les avis d'une mission, celui du client d'abord. */
	@Transactional(readOnly = true)
	List<UnAvis> lister(UUID missionId) {
		return base.sql("SELECT auteur, note, texte, donne_le FROM avis_mission WHERE mission_id = :m ORDER BY auteur")
			.param("m", missionId)
			.query((rs, i) -> new UnAvis(rs.getString("auteur"), rs.getInt("note"), rs.getString("texte"),
					rs.getTimestamp("donne_le").toInstant()))
			.list();
	}

	/** A-t-il déjà donné son avis ? Pour l'action `donner_avis`. */
	@Transactional(readOnly = true)
	boolean aDonne(UUID missionId, UUID moi) {
		return base.sql("SELECT EXISTS (SELECT 1 FROM avis_mission WHERE mission_id = :m AND auteur_id = :u)")
			.param("m", missionId).param("u", moi).query(Boolean.class).single();
	}

}
