package dev.jamii.api.reseau;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import dev.jamii.api.auth.DonneesPersonnelles;

/**
 * « En ligne » et « Vu il y a … » (#157). Chaque requête d'une personne connectée note son activité, au plus une fois par
 * minute (en mémoire, puis dans `presence`) : les onglets ouverts interrogent déjà l'API toutes les quelques secondes.
 *
 * Vie privée : chacun peut cacher sa présence, et c'est réciproque : qui se cache ne voit plus celle des autres. Au-delà
 * d'une semaine, on ne donne plus de date. Les heures sont arrondies à la minute.
 */
@Service
class Presence implements DonneesPersonnelles {

	/** Actif depuis moins de 2 minutes : « En ligne ». */
	static final Duration EN_LIGNE = Duration.ofMinutes(2);

	/** On n'écrit pas plus d'une fois par minute et par personne. */
	static final Duration ECRITURE = Duration.ofMinutes(1);

	/** Au-delà, plus de date : on ne raconte pas les absences longues. */
	static final Duration RECENT = Duration.ofDays(7);

	private static final int MEMOIRE_MAX = 100_000;

	record Etat(UUID personneId, boolean enLigne, Instant vuLe) {
	}

	record Lecture(boolean masquee, List<Etat> elements) {
	}

	private final JdbcClient base;

	private final Map<UUID, Instant> derniereEcriture = new ConcurrentHashMap<>();

	Presence(JdbcClient base) {
		this.base = base;
	}

	/** Une requête de cette personne vient d'arriver. */
	void signaler(UUID id, Instant maintenant) {
		Instant avant = derniereEcriture.get(id);
		if (avant != null && Duration.between(avant, maintenant).compareTo(ECRITURE) < 0) {
			return;
		}
		if (derniereEcriture.size() > MEMOIRE_MAX) {
			derniereEcriture.clear();
		}
		derniereEcriture.put(id, maintenant);
		try {
			base.sql("""
					INSERT INTO presence (utilisateur_id, vu_le) VALUES (:id, :le)
					ON CONFLICT (utilisateur_id) DO UPDATE SET vu_le = EXCLUDED.vu_le
					""").param("id", id).param("le", Timestamp.from(maintenant)).update();
		}
		catch (DataAccessException e) {
			// Compte supprimé pendant que la session vivait : rien à noter. La présence n'est qu'un indice.
		}
	}

	boolean visible(UUID id) {
		return base.sql("SELECT visible FROM presence WHERE utilisateur_id = :id").param("id", id).query(Boolean.class)
			.optional().orElse(true);
	}

	void changerVisibilite(UUID id, boolean visible) {
		base.sql("""
				INSERT INTO presence (utilisateur_id, visible) VALUES (:id, :visible)
				ON CONFLICT (utilisateur_id) DO UPDATE SET visible = EXCLUDED.visible
				""").param("id", id).param("visible", visible).update();
	}

	@Override
	public String section() {
		return "presence";
	}

	/** Pour l'export du compte : mon choix et ma dernière activité notée. Effacées avec le compte (cascade). */
	@Override
	public Object exporter(UUID id) {
		Map<String, Object> donnees = new HashMap<>();
		donnees.put("visible", true);
		donnees.put("vuLe", null);
		base.sql("SELECT visible, vu_le FROM presence WHERE utilisateur_id = :id").param("id", id).query((rs, n) -> {
			donnees.put("visible", rs.getBoolean("visible"));
			Timestamp vu = rs.getTimestamp("vu_le");
			donnees.put("vuLe", vu == null ? null : vu.toInstant().toString());
			return 0;
		}).list();
		return donnees;
	}

	/** La présence de ces personnes, vue par moi. */
	Lecture lire(UUID moi, Collection<UUID> personnes, Instant maintenant) {
		if (!visible(moi)) {
			return new Lecture(true, personnes.stream().map(p -> new Etat(p, false, null)).toList());
		}
		Map<UUID, Instant> vus = new HashMap<>();
		if (!personnes.isEmpty()) {
			base.sql("SELECT utilisateur_id, vu_le FROM presence WHERE utilisateur_id IN (:ids) AND visible AND vu_le IS NOT NULL")
				.param("ids", personnes)
				.query((rs, n) -> vus.put(rs.getObject("utilisateur_id", UUID.class), rs.getTimestamp("vu_le").toInstant()))
				.list();
		}
		return new Lecture(false, personnes.stream().map(p -> {
			Instant vu = p.equals(moi) ? null : vus.get(p);
			if (vu == null) return new Etat(p, false, null);
			if (vu.isAfter(maintenant.minus(EN_LIGNE))) return new Etat(p, true, null);
			return new Etat(p, false, vu.isAfter(maintenant.minus(RECENT)) ? vu.truncatedTo(ChronoUnit.MINUTES) : null);
		}).toList());
	}

}
