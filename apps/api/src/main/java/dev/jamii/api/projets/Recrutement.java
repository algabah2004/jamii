package dev.jamii.api.projets;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Les rôles recherchés par un projet et leurs candidatures (contracts/projets.yaml). Accepter une candidature fait
 * tout en une transaction : le candidat rejoint l'équipe, le rôle est pourvu, les autres candidatures sont refusées.
 * Les écritures verrouillent d'abord la ligne du rôle ou de la candidature : deux décisions simultanées passent
 * l'une après l'autre.
 */
@Service
class Recrutement {

	static final int CANDIDATURES_PAR_JOUR = 20;

	/** Même forme que le schéma `Competence` du contrat. */
	private static final Pattern COMPETENCE = Pattern.compile("^[a-z0-9][a-z0-9.+#-]{0,49}$");

	record Role(UUID id, UUID projetId, String titre, List<String> competences, String statut, Instant creeLe) {
	}

	record Candidature(UUID id, UUID projetId, Role role, UUID candidatId, String message, String statut, Instant creeLe,
			Instant decideeLe) {
	}

	record Page(List<Candidature> elements, String curseurSuivant) {
	}

	private final JdbcClient base;

	private final GestionProjets projets;

	Recrutement(JdbcClient base, GestionProjets projets) {
		this.base = base;
		this.projets = projets;
	}

	/** Minuscules, sans espace autour, sans doublon. Renvoie vide si une valeur n'est pas une compétence valide. */
	static List<String> normaliser(List<String> brutes) {
		if (brutes == null || brutes.isEmpty() || brutes.size() > 10) {
			return List.of();
		}
		List<String> propres = new ArrayList<>();
		for (String brute : brutes) {
			String c = brute == null ? "" : brute.strip().toLowerCase(java.util.Locale.ROOT);
			if (!COMPETENCE.matcher(c).matches()) {
				return List.of();
			}
			if (!propres.contains(c)) {
				propres.add(c);
			}
		}
		return propres;
	}

	// ---------------------------------------------------------------- rôles

	@Transactional(readOnly = true)
	List<Role> roles(UUID projetId) {
		return base.sql("""
				SELECT id, projet_id, titre, competences, statut, cree_le FROM role_recherche
				WHERE projet_id = :p ORDER BY cree_le, id
				""")
			.param("p", projetId)
			.query((rs, n) -> enRole(rs))
			.list();
	}

	/** Titre du rôle occupé par chaque membre actuel de l'équipe (les membres sans rôle n'y figurent pas). */
	@Transactional(readOnly = true)
	Map<UUID, String> titresDesRolesOccupes(UUID projetId) {
		Map<UUID, String> titres = new HashMap<>();
		base.sql("""
				SELECT m.utilisateur_id, r.titre FROM membre_equipe m JOIN role_recherche r ON r.id = m.role_recherche_id
				WHERE m.projet_id = :p AND m.parti_le IS NULL
				""")
			.param("p", projetId)
			.query((rs, n) -> titres.put(rs.getObject("utilisateur_id", UUID.class), rs.getString("titre")))
			.list();
		return titres;
	}

	@Transactional
	Role publierRole(UUID projetId, UUID moi, String titre, List<String> competences, Instant maintenant) {
		exigerPorteur(projetId, moi);
		UUID id = UUID.randomUUID();
		Timestamp t = Timestamp.from(maintenant);
		base.sql("INSERT INTO role_recherche (id, projet_id, titre, competences, cree_le) VALUES (:id, :p, :titre, CAST(:c AS text[]), :t)")
			.param("id", id).param("p", projetId).param("titre", titre).param("c", enTableau(competences)).param("t", t).update();
		activite(projetId, t);
		return role(id);
	}

	/**
	 * Modifie un rôle. `statut` ne peut valoir que `ouvert` ou `ferme`. Fermer refuse les candidatures en attente.
	 * Changer le statut d'un rôle déjà pourvu : `409 role_pourvu`.
	 */
	@Transactional
	Role modifierRole(UUID projetId, UUID roleId, UUID moi, String titre, List<String> competences, String statut, Instant maintenant) {
		exigerPorteur(projetId, moi);
		Role role = verrouillerRole(projetId, roleId);
		Timestamp t = Timestamp.from(maintenant);
		if (statut != null && !statut.equals(role.statut())) {
			if ("pourvu".equals(role.statut())) {
				throw ErreurProjets.conflit("role_pourvu", "Ce rôle est déjà pourvu");
			}
			base.sql("UPDATE role_recherche SET statut = :s WHERE id = :id").param("s", statut).param("id", roleId).update();
			if ("ferme".equals(statut)) {
				base.sql("UPDATE candidature SET statut = 'refusee', decidee_le = :t WHERE role_recherche_id = :id AND statut = 'envoyee'")
					.param("t", t).param("id", roleId).update();
			}
		}
		if (titre != null) {
			base.sql("UPDATE role_recherche SET titre = :titre WHERE id = :id").param("titre", titre).param("id", roleId).update();
		}
		if (competences != null) {
			base.sql("UPDATE role_recherche SET competences = CAST(:c AS text[]) WHERE id = :id")
				.param("c", enTableau(competences)).param("id", roleId).update();
		}
		activite(projetId, t);
		return role(roleId);
	}

	// ---------------------------------------------------------------- candidatures

	@Transactional
	Candidature candidater(UUID projetId, UUID roleId, UUID moi, String message, Instant maintenant) {
		var projet = projets.lire(projetId).orElseThrow(ErreurProjets::introuvable);
		Role role = verrouillerRole(projetId, roleId);
		if (projet.porteurId().equals(moi)) {
			throw ErreurProjets.conflit("propre_projet", "On ne candidate pas à son propre projet");
		}
		if (estMembreActuel(projetId, moi)) {
			throw ErreurProjets.conflit("deja_membre", "Tu fais déjà partie de l'équipe");
		}
		if (!"ouvert".equals(role.statut())) {
			throw ErreurProjets.conflit("role_pourvu", "Ce rôle est pourvu ou fermé");
		}
		if (base.sql("SELECT EXISTS (SELECT 1 FROM candidature WHERE role_recherche_id = :r AND candidat_id = :c AND statut = 'envoyee')")
			.param("r", roleId).param("c", moi).query(Boolean.class).single()) {
			throw ErreurProjets.conflit("candidature_existante", "Tu as déjà candidaté à ce rôle");
		}
		List<Instant> recentes = base.sql("SELECT cree_le FROM candidature WHERE candidat_id = :c AND cree_le > :depuis ORDER BY cree_le")
			.param("c", moi).param("depuis", Timestamp.from(maintenant.minus(Duration.ofDays(1))))
			.query((rs, n) -> rs.getTimestamp("cree_le").toInstant()).list();
		if (recentes.size() >= CANDIDATURES_PAR_JOUR) {
			long attente = Math.max(1, Duration.between(maintenant, recentes.getFirst().plus(Duration.ofDays(1))).toSeconds());
			throw new ErreurProjets(HttpStatus.TOO_MANY_REQUESTS, "trop_de_requetes", "Trop de candidatures aujourd'hui", null, attente);
		}
		UUID id = UUID.randomUUID();
		try {
			base.sql("INSERT INTO candidature (id, role_recherche_id, candidat_id, message, cree_le) VALUES (:id, :r, :c, :m, :t)")
				.param("id", id).param("r", roleId).param("c", moi).param("m", message).param("t", Timestamp.from(maintenant)).update();
		}
		catch (DuplicateKeyException doublon) {
			throw ErreurProjets.conflit("candidature_existante", "Tu as déjà candidaté à ce rôle");
		}
		return candidature(id);
	}

	/** `decision` : `accepter` ou `refuser` (le porteur), `retirer` (le candidat). */
	@Transactional
	Candidature decider(UUID projetId, UUID candidatureId, String decision, UUID moi, Instant maintenant) {
		var projet = projets.lire(projetId).orElseThrow(ErreurProjets::introuvable);
		// La candidature doit appartenir à ce projet, sinon elle n'existe pas pour cet appel.
		Candidature c = base.sql("""
				SELECT c.id FROM candidature c JOIN role_recherche r ON r.id = c.role_recherche_id
				WHERE c.id = :c AND r.projet_id = :p
				""")
			.param("c", candidatureId).param("p", projetId).query(UUID.class).optional()
			.map(this::candidature).orElseThrow(ErreurProjets::introuvable);
		boolean porteur = projet.porteurId().equals(moi);
		switch (decision) {
			case "accepter", "refuser" -> {
				if (!porteur) {
					throw ErreurProjets.interdit();
				}
			}
			case "retirer" -> {
				if (!c.candidatId().equals(moi)) {
					throw ErreurProjets.interdit();
				}
			}
			default -> throw ErreurProjets.introuvable();
		}
		// Le rôle d'abord (même ordre de verrous pour tout le monde), puis la candidature relue sous verrou.
		Role role = verrouillerRole(projetId, c.role().id());
		String statut = base.sql("SELECT statut FROM candidature WHERE id = :c FOR UPDATE").param("c", candidatureId)
			.query(String.class).single();
		if (!"envoyee".equals(statut)) {
			throw ErreurProjets.conflit("transition_invalide", "Cette candidature est déjà décidée");
		}
		Timestamp t = Timestamp.from(maintenant);
		switch (decision) {
			case "refuser" -> fermerCandidature(candidatureId, "refusee", t);
			case "retirer" -> fermerCandidature(candidatureId, "retiree", t);
			default -> {
				if (!"ouvert".equals(role.statut())) {
					throw ErreurProjets.conflit("role_pourvu", "Ce rôle est pourvu ou fermé");
				}
				base.sql("""
						INSERT INTO membre_equipe (projet_id, utilisateur_id, rejoint_le, role_recherche_id)
						VALUES (:p, :u, :t, :r)
						ON CONFLICT (projet_id, utilisateur_id)
						DO UPDATE SET parti_le = NULL, rejoint_le = :t, role_recherche_id = :r
						""")
					.param("p", projetId).param("u", c.candidatId()).param("t", t).param("r", role.id()).update();
				base.sql("UPDATE role_recherche SET statut = 'pourvu' WHERE id = :r").param("r", role.id()).update();
				base.sql("UPDATE candidature SET statut = 'refusee', decidee_le = :t WHERE role_recherche_id = :r AND statut = 'envoyee' AND id <> :c")
					.param("t", t).param("r", role.id()).param("c", candidatureId).update();
				fermerCandidature(candidatureId, "acceptee", t);
				activite(projetId, t);
			}
		}
		return candidature(candidatureId);
	}

	@Transactional(readOnly = true)
	Page candidaturesDuProjet(UUID projetId, UUID moi, String statut, int limite, String curseur) {
		exigerPorteur(projetId, moi);
		return page("r.projet_id = :p", Map.of("p", projetId), statut, limite, curseur);
	}

	@Transactional(readOnly = true)
	Page mesCandidatures(UUID moi, String statut, int limite, String curseur) {
		return page("c.candidat_id = :m", Map.of("m", moi), statut, limite, curseur);
	}

	// ---------------------------------------------------------------- privé

	private Page page(String filtre, Map<String, Object> parametres, String statut, int limite, String curseur) {
		Instant avant = null;
		UUID avantId = null;
		if (curseur != null) {
			try {
				String[] morceaux = new String(Base64.getUrlDecoder().decode(curseur)).split("\\|");
				avant = Instant.parse(morceaux[0]);
				avantId = UUID.fromString(morceaux[1]);
			}
			catch (RuntimeException e) {
				throw ErreurProjets.validation("curseur", "format_invalide");
			}
		}
		String sql = """
				SELECT c.id FROM candidature c JOIN role_recherche r ON r.id = c.role_recherche_id
				WHERE %s %s %s
				ORDER BY c.cree_le DESC, c.id DESC LIMIT :limite
				""".formatted(filtre, statut == null ? "" : "AND c.statut = :statut",
				avant == null ? "" : "AND (c.cree_le, c.id) < (:avant, :avantId)");
		var requete = base.sql(sql).param("limite", limite + 1);
		for (var e : parametres.entrySet()) {
			requete = requete.param(e.getKey(), e.getValue());
		}
		if (statut != null) {
			requete = requete.param("statut", statut);
		}
		if (avant != null) {
			requete = requete.param("avant", Timestamp.from(avant)).param("avantId", avantId);
		}
		List<UUID> ids = requete.query(UUID.class).list();
		List<Candidature> elements = ids.stream().limit(limite).map(this::candidature).toList();
		String suivant = null;
		if (ids.size() > limite) {
			Candidature derniere = elements.getLast();
			suivant = Base64.getUrlEncoder().withoutPadding().encodeToString((derniere.creeLe() + "|" + derniere.id()).getBytes());
		}
		return new Page(elements, suivant);
	}

	private void exigerPorteur(UUID projetId, UUID moi) {
		var projet = projets.lire(projetId).orElseThrow(ErreurProjets::introuvable);
		if (!projet.porteurId().equals(moi)) {
			throw ErreurProjets.interdit();
		}
	}

	private Role verrouillerRole(UUID projetId, UUID roleId) {
		return base.sql("""
				SELECT id, projet_id, titre, competences, statut, cree_le FROM role_recherche
				WHERE id = :r AND projet_id = :p FOR UPDATE
				""")
			.param("r", roleId).param("p", projetId).query((rs, n) -> enRole(rs)).optional()
			.orElseThrow(ErreurProjets::introuvable);
	}

	private Role role(UUID id) {
		return base.sql("SELECT id, projet_id, titre, competences, statut, cree_le FROM role_recherche WHERE id = :id")
			.param("id", id).query((rs, n) -> enRole(rs)).single();
	}

	private Candidature candidature(UUID id) {
		return base.sql("""
				SELECT c.id, c.candidat_id, c.message, c.statut, c.cree_le, c.decidee_le, r.id AS role_id, r.projet_id,
				       r.titre, r.competences, r.statut AS role_statut, r.cree_le AS role_cree_le
				FROM candidature c JOIN role_recherche r ON r.id = c.role_recherche_id WHERE c.id = :id
				""")
			.param("id", id)
			.query((rs, n) -> {
				Role role = new Role(rs.getObject("role_id", UUID.class), rs.getObject("projet_id", UUID.class), rs.getString("titre"),
						liste(rs.getArray("competences")), rs.getString("role_statut"), rs.getTimestamp("role_cree_le").toInstant());
				return new Candidature(rs.getObject("id", UUID.class), role.projetId(), role, rs.getObject("candidat_id", UUID.class),
						rs.getString("message"), rs.getString("statut"), rs.getTimestamp("cree_le").toInstant(),
						rs.getTimestamp("decidee_le") == null ? null : rs.getTimestamp("decidee_le").toInstant());
			})
			.single();
	}

	private void fermerCandidature(UUID id, String statut, Timestamp t) {
		base.sql("UPDATE candidature SET statut = :s, decidee_le = :t WHERE id = :id").param("s", statut).param("t", t).param("id", id).update();
	}

	private boolean estMembreActuel(UUID projetId, UUID utilisateurId) {
		return base.sql("SELECT EXISTS (SELECT 1 FROM membre_equipe WHERE projet_id = :p AND utilisateur_id = :u AND parti_le IS NULL)")
			.param("p", projetId).param("u", utilisateurId).query(Boolean.class).single();
	}

	private void activite(UUID projetId, Timestamp t) {
		base.sql("UPDATE projet SET derniere_activite_le = :t, mis_a_jour_le = :t WHERE id = :p").param("t", t).param("p", projetId).update();
	}

	/** Une valeur de tableau PostgreSQL. Sûr : les compétences ne contiennent que [a-z0-9.+#-] (voir {@link #normaliser}). */
	private static String enTableau(List<String> competences) {
		return "{" + String.join(",", competences) + "}";
	}

	private static Role enRole(ResultSet rs) throws SQLException {
		return new Role(rs.getObject("id", UUID.class), rs.getObject("projet_id", UUID.class), rs.getString("titre"),
				liste(rs.getArray("competences")), rs.getString("statut"), rs.getTimestamp("cree_le").toInstant());
	}

	private static List<String> liste(Array tableau) throws SQLException {
		return List.of((String[]) tableau.getArray());
	}

}
