package dev.jamii.api.communautes;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Communautés, adhésions et publications (contracts/communautes.yaml). Les tables `communaute`, `adhesion`,
 * `publication` et `reponse` n'appartiennent qu'à ce module.
 */
@Service
class GestionCommunautes {

	/** Contre le spam (contrat) : au plus 10 publications par heure et par personne. */
	static final int PUBLICATIONS_PAR_HEURE = 10;

	record Fiche(UUID id, String slug, String nom, String description, String theme, String pays, int nombreMembres,
			int questionsOuvertes, boolean membre, int activite) {
	}

	record Resume(UUID id, String communaute, String type, String titre, UUID auteurId, int nombreReponses,
			Instant accepteeLe, Instant creeLe) {
	}

	record Detail(Resume resume, UUID communauteId, String contenu, UUID projetId, UUID reponseAccepteeId,
			String propositionIa) {
	}

	record LigneReponse(UUID id, String contenu, UUID auteurId, boolean proposeeParIa, UUID valideeParId,
			Instant creeLe) {
	}

	/** Curseur opaque pour le front : la clé de tri du dernier élément, en base64 (api-conventions.md §4). */
	record Curseur(String a, String b) {

		String encoder() {
			return Base64.getUrlEncoder().withoutPadding().encodeToString((a + "|" + b).getBytes(StandardCharsets.UTF_8));
		}

		static Optional<Curseur> decoder(String texte) {
			try {
				String[] m = new String(Base64.getUrlDecoder().decode(texte), StandardCharsets.UTF_8).split("\\|", 2);
				return m.length == 2 ? Optional.of(new Curseur(m[0], m[1])) : Optional.empty();
			}
			catch (IllegalArgumentException e) {
				return Optional.empty();
			}
		}

	}

	private static final String FICHE = """
			SELECT c.id, c.slug, c.nom, c.description, c.theme, c.pays,
			       (SELECT count(*) FROM adhesion a WHERE a.communaute_id = c.id) AS nombre_membres,
			       (SELECT count(*) FROM publication p WHERE p.communaute_id = c.id
			          AND p.type = 'question' AND p.reponse_acceptee_id IS NULL) AS questions_ouvertes,
			       EXISTS (SELECT 1 FROM adhesion a WHERE a.communaute_id = c.id AND a.utilisateur_id = :moi) AS membre,
			       (SELECT count(*) FROM publication p WHERE p.communaute_id = c.id AND p.cree_le >= :semaine) AS activite
			FROM communaute c
			""";

	private final JdbcClient base;

	GestionCommunautes(JdbcClient base) {
		this.base = base;
	}

	/** Les plus actives cette semaine d'abord, puis par slug : un ordre stable pour le curseur (activite, slug). */
	@Transactional(readOnly = true)
	List<Fiche> lister(UUID moi, boolean seulementMiennes, int limite, Curseur apres, Instant maintenant) {
		String sql = "SELECT * FROM (" + FICHE + ") f WHERE (:toutes OR f.membre)"
				+ (apres == null ? "" : " AND (f.activite < :activite OR (f.activite = :activite AND f.slug > :slug))")
				+ " ORDER BY f.activite DESC, f.slug LIMIT :limite";
		var requete = base.sql(sql).param("moi", moi).param("semaine", Timestamp.from(maintenant.minus(Duration.ofDays(7))))
			.param("toutes", !seulementMiennes).param("limite", limite);
		if (apres != null) {
			requete = requete.param("activite", Long.parseLong(apres.a())).param("slug", apres.b());
		}
		return requete.query(GestionCommunautes::fiche).list();
	}

	@Transactional(readOnly = true)
	Optional<Fiche> lire(String slug, UUID moi, Instant maintenant) {
		return base.sql(FICHE + " WHERE c.slug = :slug").param("slug", slug).param("moi", moi)
			.param("semaine", Timestamp.from(maintenant.minus(Duration.ofDays(7))))
			.query(GestionCommunautes::fiche).optional();
	}

	/** Idempotent : rejoindre deux fois ne crée qu'une adhésion. */
	@Transactional
	void rejoindre(UUID communaute, UUID moi, Instant maintenant) {
		base.sql("""
				INSERT INTO adhesion (communaute_id, utilisateur_id, rejoint_le) VALUES (:c, :u, :t)
				ON CONFLICT (communaute_id, utilisateur_id) DO NOTHING
				""").param("c", communaute).param("u", moi).param("t", Timestamp.from(maintenant)).update();
	}

	/** Idempotent. Les publications restent, signées de mon nom (contrat). */
	@Transactional
	void quitter(UUID communaute, UUID moi) {
		base.sql("DELETE FROM adhesion WHERE communaute_id = :c AND utilisateur_id = :u")
			.param("c", communaute).param("u", moi).update();
	}

	@Transactional(readOnly = true)
	boolean estMembre(UUID communaute, UUID moi) {
		return base.sql("SELECT EXISTS (SELECT 1 FROM adhesion WHERE communaute_id = :c AND utilisateur_id = :u)")
			.param("c", communaute).param("u", moi).query(Boolean.class).single();
	}

	/** Les plus récentes d'abord, curseur (cree_le, id). */
	@Transactional(readOnly = true)
	List<Resume> publications(UUID communaute, String type, boolean seulementOuvertes, int limite, Curseur apres) {
		String sql = RESUME + " WHERE p.communaute_id = :c"
				+ (type == null ? "" : " AND p.type = :type")
				+ (seulementOuvertes ? " AND p.type = 'question' AND p.reponse_acceptee_id IS NULL" : "")
				+ (apres == null ? "" : " AND (p.cree_le, p.id) < (:avant, :id)")
				+ " ORDER BY p.cree_le DESC, p.id DESC LIMIT :limite";
		var requete = base.sql(sql).param("c", communaute).param("limite", limite);
		if (type != null) {
			requete = requete.param("type", type);
		}
		if (apres != null) {
			requete = requete.param("avant", Timestamp.from(Instant.parse(apres.a()))).param("id", UUID.fromString(apres.b()));
		}
		return requete.query(GestionCommunautes::resume).list();
	}

	/** Secondes à attendre avant de pouvoir publier à nouveau, 0 si c'est permis. Compté en base : vrai sur plusieurs instances. */
	@Transactional(readOnly = true)
	long attenteAvantPublication(UUID moi, Instant maintenant) {
		List<Timestamp> recentes = base.sql("""
				SELECT cree_le FROM publication WHERE auteur_id = :u AND cree_le > :depuis ORDER BY cree_le LIMIT :max
				""").param("u", moi).param("depuis", Timestamp.from(maintenant.minus(Duration.ofHours(1))))
			.param("max", PUBLICATIONS_PAR_HEURE).query(Timestamp.class).list();
		if (recentes.size() < PUBLICATIONS_PAR_HEURE) {
			return 0;
		}
		return Math.max(1, Duration.between(maintenant, recentes.get(0).toInstant().plus(Duration.ofHours(1))).toSeconds());
	}

	@Transactional
	UUID publier(UUID communaute, UUID auteur, String type, String titre, String contenu, UUID projetId,
			String propositionIa, Instant maintenant) {
		UUID id = UUID.randomUUID();
		base.sql("""
				INSERT INTO publication (id, communaute_id, auteur_id, type, titre, contenu, projet_id, proposition_ia, cree_le)
				VALUES (:id, :c, :auteur, :type, :titre, :contenu, :projet, :ia, :t)
				""").param("id", id).param("c", communaute).param("auteur", auteur).param("type", type)
			.param("titre", titre).param("contenu", contenu).param("projet", projetId).param("ia", propositionIa)
			.param("t", Timestamp.from(maintenant)).update();
		return id;
	}

	@Transactional(readOnly = true)
	Optional<Detail> lirePublication(UUID id) {
		return base.sql(RESUME.replaceFirst("SELECT ", "SELECT p.communaute_id, p.contenu, p.projet_id, p.reponse_acceptee_id, p.proposition_ia, ")
				+ " WHERE p.id = :id")
			.param("id", id)
			.query((rs, n) -> new Detail(resume(rs, n), rs.getObject("communaute_id", UUID.class), rs.getString("contenu"),
					rs.getObject("projet_id", UUID.class), rs.getObject("reponse_acceptee_id", UUID.class),
					rs.getString("proposition_ia")))
			.optional();
	}

	/** La réponse acceptée d'abord, puis celle de l'IA, puis les autres des plus anciennes aux plus récentes (contrat). */
	@Transactional(readOnly = true)
	List<LigneReponse> reponses(UUID publication) {
		return base.sql("""
				SELECT r.id, r.contenu, r.auteur_id, r.proposee_par_ia, r.validee_par_id, r.cree_le
				FROM reponse r JOIN publication p ON p.id = r.publication_id
				WHERE r.publication_id = :p
				ORDER BY (r.id = p.reponse_acceptee_id) IS TRUE DESC, r.proposee_par_ia DESC, r.cree_le, r.id
				""").param("p", publication)
			.query((rs, n) -> new LigneReponse(rs.getObject("id", UUID.class), rs.getString("contenu"),
					rs.getObject("auteur_id", UUID.class), rs.getBoolean("proposee_par_ia"),
					rs.getObject("validee_par_id", UUID.class), rs.getTimestamp("cree_le").toInstant()))
			.list();
	}

	private static final String RESUME = """
			SELECT p.id, c.slug, p.type, p.titre, p.auteur_id, p.acceptee_le, p.cree_le,
			       (SELECT count(*) FROM reponse r WHERE r.publication_id = p.id) AS nombre_reponses
			FROM publication p JOIN communaute c ON c.id = p.communaute_id
			""";

	private static Resume resume(ResultSet rs, int n) throws SQLException {
		Timestamp acceptee = rs.getTimestamp("acceptee_le");
		return new Resume(rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("type"), rs.getString("titre"),
				rs.getObject("auteur_id", UUID.class), rs.getInt("nombre_reponses"),
				acceptee == null ? null : acceptee.toInstant(), rs.getTimestamp("cree_le").toInstant());
	}

	private static Fiche fiche(ResultSet rs, int n) throws SQLException {
		return new Fiche(rs.getObject("id", UUID.class), rs.getString("slug"), rs.getString("nom"),
				rs.getString("description"), rs.getString("theme"), rs.getString("pays"), rs.getInt("nombre_membres"),
				rs.getInt("questions_ouvertes"), rs.getBoolean("membre"), rs.getInt("activite"));
	}

}
