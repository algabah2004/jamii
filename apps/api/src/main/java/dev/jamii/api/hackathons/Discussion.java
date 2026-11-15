package dev.jamii.api.hackathons;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * La discussion d'un hackathon (contracts/hackathons.yaml, tag « discussion ») : l'organisateur publie des annonces à
 * tous, et lie une communauté (module communautés) où les participants posent leurs questions et échangent dans des
 * salons. Lire suit les droits du hackathon : qui peut le voir voit ses annonces ; tout le reste est à l'organisateur.
 */
@Service
class Discussion {

	static final int TEXTE_MAX = 2000;

	/** Contre le spam d'un compte d'organisateur volé : au plus 10 annonces par heure et par hackathon. */
	static final int ANNONCES_PAR_HEURE = 10;

	/** Le format des noms courts de communautés (minuscules, chiffres, tirets). */
	private static final Pattern SLUG = Pattern.compile("[a-z0-9]([a-z0-9-]{0,78}[a-z0-9])?");

	record Annonce(UUID id, UUID auteurId, String texte, Instant publieeLe) {
	}

	record Etat(String communaute, List<Annonce> annonces) {
	}

	private final JdbcClient base;

	private final Hackathons hackathons;

	Discussion(JdbcClient base, Hackathons hackathons) {
		this.base = base;
		this.hackathons = hackathons;
	}

	@Transactional(readOnly = true)
	Etat lire(UUID hackathonId, UUID moi) {
		hackathons.lire(hackathonId, moi);
		List<Annonce> annonces = base.sql("""
				SELECT id, auteur_id, texte, publiee_le FROM annonce_hackathon
				WHERE hackathon_id = :h ORDER BY publiee_le DESC, id DESC LIMIT 50""")
			.param("h", hackathonId)
			.query((rs, i) -> new Annonce(rs.getObject("id", UUID.class), rs.getObject("auteur_id", UUID.class),
					rs.getString("texte"), rs.getTimestamp("publiee_le").toInstant()))
			.list();
		Optional<String> communaute = base.sql("SELECT communaute_slug FROM communaute_hackathon WHERE hackathon_id = :h")
			.param("h", hackathonId).query(String.class).optional();
		return new Etat(communaute.orElse(null), annonces);
	}

	/** Une annonce à tous (organisateur), sauf sur un hackathon annulé. */
	@Transactional
	Annonce annoncer(UUID hackathonId, UUID moi, String texte) {
		Hackathons.Hackathon h = hackathons.organisateurDe(hackathonId, moi);
		Instant maintenant = hackathons.maintenant();
		if ("annule".equals(h.statut(maintenant))) {
			throw ErreurHackathons.conflit("etat_invalide", "Ce hackathon est annulé");
		}
		String propre = texte == null ? "" : texte.strip();
		if (propre.isEmpty()) {
			throw ErreurHackathons.validation("texte", "obligatoire");
		}
		if (propre.length() > TEXTE_MAX) {
			throw ErreurHackathons.validation("texte", "trop_long");
		}
		List<Instant> recentes = base.sql("""
				SELECT publiee_le FROM annonce_hackathon WHERE hackathon_id = :h AND publiee_le > :depuis ORDER BY publiee_le""")
			.param("h", hackathonId).param("depuis", Timestamp.from(maintenant.minus(Duration.ofHours(1))))
			.query((rs, i) -> rs.getTimestamp(1).toInstant()).list();
		if (recentes.size() >= ANNONCES_PAR_HEURE) {
			Instant liberee = recentes.get(recentes.size() - ANNONCES_PAR_HEURE).plus(Duration.ofHours(1));
			throw ErreurHackathons.limiteAtteinte("Trop d'annonces en une heure", Duration.between(maintenant, liberee).toSeconds() + 1);
		}
		UUID id = UUID.randomUUID();
		base.sql("INSERT INTO annonce_hackathon (id, hackathon_id, auteur_id, texte, publiee_le) VALUES (:id, :h, :a, :t, :le)")
			.param("id", id).param("h", hackathonId).param("a", moi).param("t", propre).param("le", Timestamp.from(maintenant))
			.update();
		return new Annonce(id, moi, propre, maintenant);
	}

	/** Retirer une annonce (organisateur). Idempotent. */
	@Transactional
	void retirer(UUID hackathonId, UUID moi, UUID annonceId) {
		hackathons.organisateurDe(hackathonId, moi);
		base.sql("DELETE FROM annonce_hackathon WHERE id = :id AND hackathon_id = :h").param("id", annonceId).param("h", hackathonId).update();
	}

	/** Lier une communauté au hackathon, ou la délier avec `null` (organisateur). */
	@Transactional
	void lierCommunaute(UUID hackathonId, UUID moi, String slug) {
		hackathons.organisateurDe(hackathonId, moi);
		if (slug == null) {
			base.sql("DELETE FROM communaute_hackathon WHERE hackathon_id = :h").param("h", hackathonId).update();
			return;
		}
		if (!SLUG.matcher(slug).matches()) {
			throw ErreurHackathons.validation("communaute", "format");
		}
		base.sql("""
				INSERT INTO communaute_hackathon (hackathon_id, communaute_slug, liee_le) VALUES (:h, :s, :le)
				ON CONFLICT (hackathon_id) DO UPDATE SET communaute_slug = EXCLUDED.communaute_slug, liee_le = EXCLUDED.liee_le""")
			.param("h", hackathonId).param("s", slug).param("le", Timestamp.from(hackathons.maintenant())).update();
	}

}
