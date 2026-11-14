package dev.jamii.api.hackathons;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Le jury d'un hackathon (contracts/hackathons.yaml, tag « jury ») : l'organisateur invite des jurés ; chacun note les
 * rendus, critère par critère, **après la fin des rendus** et jusqu'à la publication des résultats. Un juré inscrit
 * dans une équipe ne note jamais le projet de son équipe (403 `conflit_interets`).
 *
 * <p>Le score pondéré d'un juré est Σ(valeur × poids) / Σ poids ; celui d'un rendu, la moyenne de ses jurés, à deux
 * décimales. Le classement est calculé à la lecture : aucune donnée en double à garder synchronisée.
 */
@Service
class Jury {

	record Jure(UUID utilisateurId, String groupe, int rendusNotes, int rendusATraiter) {
	}

	record Note(UUID critereId, int valeur) {
	}

	record NotesDuJure(UUID renduId, List<Note> notes, String commentaire, BigDecimal scorePondere) {
	}

	record Classe(UUID equipeId, UUID renduId, int rang, BigDecimal scorePondere, int nombreDeJures) {
	}

	record Notation(List<Jure> jures, List<Classe> classement) {
	}

	/** Une ligne de l'export : une note, avec les noms lisibles. */
	record LigneExport(String equipe, String rendu, UUID jure, String critere, int valeur, String commentaire) {
	}

	private final JdbcClient base;

	private final Hackathons hackathons;

	private final ApplicationEventPublisher evenements;

	Jury(JdbcClient base, Hackathons hackathons, ApplicationEventPublisher evenements) {
		this.base = base;
		this.hackathons = hackathons;
		this.evenements = evenements;
	}

	// ---------------------------------------------------------------- les jurés

	/**
	 * Ajouter un juré, ou changer son groupe (organisateur). Pas après les résultats ni pour un hackathon annulé (409
	 * `etat_invalide`) ; l'organisateur n'est pas juré de son propre hackathon, et le compte existe (400).
	 */
	@Transactional
	Jure inviter(UUID hackathonId, UUID moi, UUID utilisateur, String groupe) {
		Hackathons.Hackathon h = hackathons.organisateurDe(hackathonId, moi);
		String statut = h.statut(hackathons.maintenant());
		if ("brouillon".equals(statut) || "annule".equals(statut) || "resultats".equals(statut)) {
			throw ErreurHackathons.conflit("etat_invalide", "On n'ajoute plus de juré à ce hackathon");
		}
		if (utilisateur.equals(moi)) {
			throw ErreurHackathons.validation("utilisateurId", "organisateur");
		}
		// Un identifiant collé à la main peut ne correspondre à aucun compte : 400, plutôt qu'une clé étrangère violée.
		if (!base.sql("SELECT EXISTS (SELECT 1 FROM utilisateur WHERE id = :u)").param("u", utilisateur).query(Boolean.class).single()) {
			throw ErreurHackathons.validation("utilisateurId", "inconnu");
		}
		int nouveau = base.sql("""
				INSERT INTO jure (hackathon_id, utilisateur_id, groupe, cree_le) VALUES (:h, :u, :g, :t)
				ON CONFLICT (hackathon_id, utilisateur_id) DO UPDATE SET groupe = EXCLUDED.groupe
				RETURNING (xmax = 0)::int
				""")
			.param("h", hackathonId).param("u", utilisateur).param("g", groupe, java.sql.Types.VARCHAR)
			.param("t", Timestamp.from(hackathons.maintenant())).query(Integer.class).single();
		if (nouveau == 1) {
			evenements.publishEvent(new JureInvite(hackathonId, utilisateur, moi, hackathons.maintenant()));
		}
		return jures(hackathonId).stream().filter(j -> j.utilisateurId().equals(utilisateur)).findFirst().orElseThrow();
	}

	/** Les jurés, pour l'organisateur. */
	@Transactional(readOnly = true)
	List<Jure> lister(UUID hackathonId, UUID moi) {
		hackathons.exigerOrganisateur(hackathonId, moi);
		return jures(hackathonId);
	}

	/** Chaque juré, avec le nombre de rendus qu'il a notés sur ceux qu'il doit noter (tous, sauf celui de son équipe). */
	private List<Jure> jures(UUID hackathonId) {
		return base.sql("""
				SELECT j.utilisateur_id, j.groupe,
				  (SELECT count(DISTINCT n.rendu_id) FROM note n JOIN rendu r ON r.id = n.rendu_id
				     WHERE r.hackathon_id = j.hackathon_id AND n.utilisateur_id = j.utilisateur_id) AS notes,
				  (SELECT count(*) FROM rendu r WHERE r.hackathon_id = j.hackathon_id AND NOT EXISTS (
				     SELECT 1 FROM inscription i WHERE i.equipe_id = r.equipe_id AND i.utilisateur_id = j.utilisateur_id)) AS a_traiter
				FROM jure j WHERE j.hackathon_id = :h ORDER BY j.cree_le, j.utilisateur_id
				""").param("h", hackathonId)
			.query((rs, i) -> new Jure(rs.getObject("utilisateur_id", UUID.class), rs.getString("groupe"), rs.getInt("notes"),
					rs.getInt("a_traiter")))
			.list();
	}

	// ---------------------------------------------------------------- les rendus à noter

	/**
	 * Les rendus visibles de `moi` : un juré voit tous les rendus sauf celui de sa propre équipe ; l'organisateur les voit
	 * tous ; une fois les résultats publiés, tout le monde. Les autres : 403.
	 */
	@Transactional(readOnly = true)
	List<UUID> rendusVisibles(UUID hackathonId, UUID moi) {
		Hackathons.Hackathon h = hackathons.charger(hackathonId, false);
		Hackathons.Lecture l = hackathons.role(h, moi).orElseThrow(ErreurHackathons::introuvable);
		boolean tous = "organisateur".equals(l.role()) || "resultats".equals(h.statut(hackathons.maintenant()));
		if (!tous && !"jure".equals(l.role())) {
			throw ErreurHackathons.interdit();
		}
		return base.sql("SELECT id FROM rendu WHERE hackathon_id = :h" + (tous ? "" : " AND equipe_id IS DISTINCT FROM CAST(:e AS uuid)")
				+ " ORDER BY modifie_le, id")
			.param("h", hackathonId).param("e", l.equipeId(), java.sql.Types.OTHER).query(UUID.class).list();
	}

	// ---------------------------------------------------------------- noter

	/**
	 * Remplacer mes notes sur un rendu. Dans l'ordre : je suis juré (403), le rendu est de ce hackathon (404), il n'est
	 * pas celui de mon équipe (403 `conflit_interets`), les rendus sont clos et les résultats pas encore publiés (409
	 * `etat_invalide`), chaque critère a exactement une note (400 : un critère manquant refuse tout).
	 */
	@Transactional
	NotesDuJure noter(UUID hackathonId, UUID renduId, UUID moi, List<Note> notes, String commentaire) {
		Hackathons.Hackathon h = hackathons.charger(hackathonId, true);
		Hackathons.Lecture l = hackathons.role(h, moi).orElseThrow(ErreurHackathons::introuvable);
		if (!"jure".equals(l.role())) {
			throw ErreurHackathons.interdit();
		}
		UUID equipe = base.sql("SELECT equipe_id FROM rendu WHERE id = :r AND hackathon_id = :h").param("r", renduId).param("h", hackathonId)
			.query(UUID.class).optional().orElseThrow(ErreurHackathons::introuvable);
		if (equipe.equals(l.equipeId())) {
			throw ErreurHackathons.conflitInterets();
		}
		if (!"evaluation".equals(h.statut(hackathons.maintenant()))) {
			throw ErreurHackathons.conflit("etat_invalide", "On note entre la fin des rendus et la publication des résultats");
		}
		Set<UUID> criteres = h.criteres().stream().map(Hackathons.Critere::id).collect(Collectors.toSet());
		Set<UUID> vus = new HashSet<>();
		for (Note n : notes) {
			if (!criteres.contains(n.critereId()) || !vus.add(n.critereId())) {
				throw ErreurHackathons.validation("notes", "critere_inconnu_ou_en_double");
			}
		}
		if (!vus.equals(criteres)) {
			throw ErreurHackathons.validation("notes", "critere_manquant");
		}
		base.sql("DELETE FROM note WHERE rendu_id = :r AND utilisateur_id = :u").param("r", renduId).param("u", moi).update();
		for (Note n : notes) {
			base.sql("INSERT INTO note (rendu_id, utilisateur_id, critere_id, valeur) VALUES (:r, :u, :c, :v)")
				.param("r", renduId).param("u", moi).param("c", n.critereId()).param("v", n.valeur()).update();
		}
		base.sql("""
				INSERT INTO appreciation (rendu_id, utilisateur_id, commentaire, modifie_le) VALUES (:r, :u, :c, :t)
				ON CONFLICT (rendu_id, utilisateur_id) DO UPDATE SET commentaire = EXCLUDED.commentaire, modifie_le = EXCLUDED.modifie_le
				""")
			.param("r", renduId).param("u", moi).param("c", commentaire, java.sql.Types.VARCHAR)
			.param("t", Timestamp.from(hackathons.maintenant())).update();
		Map<UUID, Integer> poids = poids(h);
		return new NotesDuJure(renduId, notes, commentaire, score(notes, poids));
	}

	// ---------------------------------------------------------------- score et classement

	/** Le score d'un juré : Σ(valeur × poids) / Σ poids, à deux décimales. */
	static BigDecimal score(List<Note> notes, Map<UUID, Integer> poids) {
		long somme = 0;
		long total = 0;
		for (Note n : notes) {
			int p = poids.getOrDefault(n.critereId(), 0);
			somme += (long) n.valeur() * p;
			total += p;
		}
		return total == 0 ? BigDecimal.ZERO.setScale(2) : BigDecimal.valueOf(somme).divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
	}

	/** Le score pondéré d'un rendu : la moyenne des scores de ses jurés, à deux décimales (0 sans juré). */
	@Transactional(readOnly = true)
	BigDecimal scorePondere(UUID renduId) {
		UUID hackathonId = base.sql("SELECT hackathon_id FROM rendu WHERE id = :r").param("r", renduId).query(UUID.class).single();
		return scores(hackathons.charger(hackathonId, false)).getOrDefault(renduId, new Classe(null, renduId, 0, BigDecimal.ZERO.setScale(2), 0))
			.scorePondere();
	}

	/**
	 * Le classement proposé : du meilleur score au moins bon, à égalité le même rang (1, 1, 3). Un rendu sans note est
	 * classé en dernier, avec 0 juré.
	 */
	@Transactional(readOnly = true)
	List<Classe> classement(UUID hackathonId) {
		List<Classe> parScore = new ArrayList<>(scores(hackathons.charger(hackathonId, false)).values());
		parScore.sort(Comparator.comparing(Classe::scorePondere).reversed().thenComparing(c -> c.renduId().toString()));
		List<Classe> classement = new ArrayList<>();
		for (int i = 0; i < parScore.size(); i++) {
			Classe c = parScore.get(i);
			int rang = i > 0 && c.scorePondere().compareTo(parScore.get(i - 1).scorePondere()) == 0 ? classement.get(i - 1).rang() : i + 1;
			classement.add(new Classe(c.equipeId(), c.renduId(), rang, c.scorePondere(), c.nombreDeJures()));
		}
		return classement;
	}

	/** L'avancement de la notation et le classement proposé, pour l'organisateur. */
	@Transactional(readOnly = true)
	Notation suivre(UUID hackathonId, UUID moi) {
		hackathons.exigerOrganisateur(hackathonId, moi);
		return new Notation(jures(hackathonId), classement(hackathonId));
	}

	private Map<UUID, Classe> scores(Hackathons.Hackathon h) {
		Map<UUID, Integer> poids = poids(h);
		Map<UUID, UUID> equipes = new LinkedHashMap<>();
		base.sql("SELECT id, equipe_id FROM rendu WHERE hackathon_id = :h").param("h", h.id())
			.query((rs, i) -> equipes.put(rs.getObject("id", UUID.class), rs.getObject("equipe_id", UUID.class))).list();
		// Les notes de chaque juré, rendu par rendu.
		Map<UUID, Map<UUID, List<Note>>> notes = new HashMap<>();
		base.sql("SELECT n.rendu_id, n.utilisateur_id, n.critere_id, n.valeur FROM note n JOIN rendu r ON r.id = n.rendu_id WHERE r.hackathon_id = :h")
			.param("h", h.id())
			.query((rs, i) -> notes.computeIfAbsent(rs.getObject("rendu_id", UUID.class), k -> new HashMap<>())
				.computeIfAbsent(rs.getObject("utilisateur_id", UUID.class), k -> new ArrayList<>())
				.add(new Note(rs.getObject("critere_id", UUID.class), rs.getInt("valeur"))))
			.list();
		Map<UUID, Classe> scores = new LinkedHashMap<>();
		equipes.forEach((rendu, equipe) -> {
			List<BigDecimal> parJure = notes.getOrDefault(rendu, Map.of()).values().stream().map(n -> score(n, poids)).toList();
			BigDecimal moyenne = parJure.isEmpty() ? BigDecimal.ZERO.setScale(2)
					: parJure.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(parJure.size()), 2, RoundingMode.HALF_UP);
			scores.put(rendu, new Classe(equipe, rendu, 0, moyenne, parJure.size()));
		});
		return scores;
	}

	private static Map<UUID, Integer> poids(Hackathons.Hackathon h) {
		return h.criteres().stream().collect(Collectors.toMap(Hackathons.Critere::id, Hackathons.Critere::poids));
	}

	// ---------------------------------------------------------------- export

	/** Toutes les notes, une ligne par note, pour l'organisateur. */
	@Transactional(readOnly = true)
	List<LigneExport> exporter(UUID hackathonId, UUID moi) {
		hackathons.exigerOrganisateur(hackathonId, moi);
		return base.sql("""
				SELECT e.nom AS equipe, r.titre AS rendu, n.utilisateur_id AS jure, c.nom AS critere, n.valeur, a.commentaire
				FROM note n
				JOIN rendu r ON r.id = n.rendu_id
				JOIN equipe e ON e.id = r.equipe_id
				JOIN critere c ON c.id = n.critere_id
				LEFT JOIN appreciation a ON a.rendu_id = n.rendu_id AND a.utilisateur_id = n.utilisateur_id
				WHERE r.hackathon_id = :h ORDER BY e.nom, n.utilisateur_id, c.ordre
				""").param("h", hackathonId)
			.query((rs, i) -> new LigneExport(rs.getString("equipe"), rs.getString("rendu"), rs.getObject("jure", UUID.class),
					rs.getString("critere"), rs.getInt("valeur"), rs.getString("commentaire")))
			.list();
	}

}
