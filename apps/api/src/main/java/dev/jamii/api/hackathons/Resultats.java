package dev.jamii.api.hackathons;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Confirmer les gagnants et publier les résultats d'un hackathon (contracts/hackathons.yaml, `confirmerResultats` et
 * `lireResultats`). Le classement proposé vient des notes du jury ; l'organisateur a le dernier mot, mais doit dire
 * pourquoi quand il s'en écarte. Une fois publiés, les résultats sont publics et les notes verrouillées.
 */
@Service
class Resultats {

	/** Un gagnant confirmé ; `raison` est obligatoire s'il diffère du classement proposé. */
	record Gagnant(UUID prixId, UUID equipeId, String raison) {
	}

	/** Une ligne du classement publié : le retour du jury est la liste de ses commentaires, sans nom. */
	record Ligne(int rang, UUID equipeId, UUID renduId, List<String> retourDuJury) {
	}

	record Publies(Instant publieLe, List<Hackathons.Prix> prix, List<Gagnant> gagnants, List<Ligne> classement) {
	}

	private final JdbcClient base;

	private final Hackathons hackathons;

	private final Jury jury;

	Resultats(JdbcClient base, Hackathons hackathons, Jury jury) {
		this.base = base;
		this.hackathons = hackathons;
		this.jury = jury;
	}

	/**
	 * Publier les résultats (organisateur). Dans l'ordre : le hackathon est en évaluation (409 `etat_invalide`), la
	 * version est la bonne (409 `modification_concurrente`), chaque prix a exactement un gagnant qui a rendu un projet
	 * (400), et un gagnant qui n'est pas celui du classement proposé a une raison (400 `gagnants[i].raison`). Le n-ième
	 * prix revient, dans la proposition, à l'équipe classée n-ième.
	 */
	@Transactional
	Publies confirmer(UUID hackathonId, UUID moi, int version, List<Gagnant> gagnants) {
		Hackathons.Hackathon h = hackathons.organisateurDe(hackathonId, moi);
		if (!"evaluation".equals(h.statut(hackathons.maintenant()))) {
			throw ErreurHackathons.conflit("etat_invalide", "Les résultats se publient après la fin des rendus, une seule fois");
		}
		if (h.version() != version) {
			throw ErreurHackathons.conflit("modification_concurrente", "Le hackathon a changé entre-temps");
		}
		List<Jury.Classe> classement = jury.classement(hackathonId);
		List<UUID> classees = classement.stream().map(Jury.Classe::equipeId).toList();
		Map<UUID, Gagnant> parPrix = new HashMap<>();
		for (Gagnant g : gagnants) {
			if (parPrix.put(g.prixId(), g) != null) {
				throw ErreurHackathons.validation("gagnants", "prix_en_double");
			}
		}
		List<Map<String, String>> erreurs = new ArrayList<>();
		for (int i = 0; i < h.prix().size(); i++) {
			Gagnant g = parPrix.remove(h.prix().get(i).id());
			if (g == null) {
				erreurs.add(Map.of("champ", "gagnants", "code", "prix_sans_gagnant"));
				continue;
			}
			int rang = gagnants.indexOf(g);
			if (!classees.contains(g.equipeId())) {
				erreurs.add(Map.of("champ", "gagnants[" + rang + "].equipeId", "code", "equipe_sans_rendu"));
			}
			else if (i >= classees.size() || !classees.get(i).equals(g.equipeId())) {
				if (g.raison() == null || g.raison().isBlank()) {
					erreurs.add(Map.of("champ", "gagnants[" + rang + "].raison", "code", "obligatoire"));
				}
			}
		}
		if (!parPrix.isEmpty()) {
			erreurs.add(Map.of("champ", "gagnants", "code", "prix_inconnu"));
		}
		if (!erreurs.isEmpty()) {
			throw ErreurHackathons.validation(erreurs);
		}
		for (Gagnant g : gagnants) {
			base.sql("INSERT INTO gagnant (hackathon_id, prix_id, equipe_id, raison_ajustement) VALUES (:h, :p, :e, :r)")
				.param("h", hackathonId).param("p", g.prixId()).param("e", g.equipeId())
				.param("r", g.raison() == null || g.raison().isBlank() ? null : g.raison().strip(), java.sql.Types.VARCHAR).update();
		}
		base.sql("UPDATE hackathon SET statut_base = 'resultats', resultats_publies_le = :t, version = version + 1 WHERE id = :id")
			.param("t", Timestamp.from(hackathons.maintenant())).param("id", hackathonId).update();
		return lire(hackathonId, moi);
	}

	/** Les résultats publiés, pour tout le monde ; avant leur publication, ils n'existent pas (404). */
	@Transactional(readOnly = true)
	Publies lire(UUID hackathonId, UUID moi) {
		Hackathons.Hackathon h = hackathons.charger(hackathonId, false);
		hackathons.role(h, moi).orElseThrow(ErreurHackathons::introuvable);
		Instant publieLe = base.sql("SELECT resultats_publies_le FROM hackathon WHERE id = :h AND statut_base = 'resultats'")
			.param("h", hackathonId).query((rs, i) -> rs.getTimestamp(1).toInstant()).optional()
			.orElseThrow(ErreurHackathons::introuvable);
		List<Gagnant> gagnants = base.sql("""
				SELECT g.prix_id, g.equipe_id, g.raison_ajustement FROM gagnant g JOIN prix p ON p.id = g.prix_id
				WHERE g.hackathon_id = :h ORDER BY p.ordre
				""").param("h", hackathonId)
			.query((rs, i) -> new Gagnant(rs.getObject("prix_id", UUID.class), rs.getObject("equipe_id", UUID.class),
					rs.getString("raison_ajustement")))
			.list();
		Map<UUID, List<String>> retours = new HashMap<>();
		base.sql("""
				SELECT a.rendu_id, a.commentaire FROM appreciation a JOIN rendu r ON r.id = a.rendu_id
				WHERE r.hackathon_id = :h AND a.commentaire IS NOT NULL ORDER BY a.modifie_le
				""").param("h", hackathonId)
			.query((rs, i) -> retours.computeIfAbsent(rs.getObject("rendu_id", UUID.class), k -> new ArrayList<>()).add(rs.getString("commentaire")))
			.list();
		List<Ligne> classement = jury.classement(hackathonId).stream()
			.map(c -> new Ligne(c.rang(), c.equipeId(), c.renduId(), retours.getOrDefault(c.renduId(), List.of())))
			.toList();
		return new Publies(publieLe, h.prix(), gagnants, classement);
	}

}
