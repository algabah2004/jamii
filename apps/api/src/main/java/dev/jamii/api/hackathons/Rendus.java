package dev.jamii.api.hackathons;

import java.net.URI;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Le rendu d'une équipe (contracts/hackathons.yaml, tag « rendus ») : un seul par équipe, rendu puis modifié par ses
 * membres pendant le hackathon, **jusqu'à `finRendusLe`** (409 `delai_depasse`, même si l'écran était ouvert avant).
 *
 * <p>Le rendu est enregistré avant que son lien soit vérifié : une panne réseau n'empêche jamais de rendre.
 */
@Service
class Rendus {

	record Rendu(UUID id, UUID hackathonId, UUID equipeId, String titre, String description, String lienProduit, String depot,
			String video, List<String> reponses, Boolean lienRepond, Instant lienVerifieLe, int version, Instant modifieLe) {
	}

	/** Un rendu déjà validé par le contrôleur ; `version` est celle lue juste avant, `null` pour un premier rendu. */
	record Nouveau(Integer version, String titre, String description, URI lienProduit, URI depot, URI video, List<String> reponses) {
	}

	/** Le rendu enregistré, et s'il vient d'être créé (201) ou modifié (200). */
	record Enregistre(Rendu rendu, boolean cree) {
	}

	private static final String COLONNES = """
			id, hackathon_id, equipe_id, titre, description, lien_produit, depot, video, reponses, lien_repond, lien_verifie_le,
			version, modifie_le""";

	private final JdbcClient base;

	private final Hackathons hackathons;

	private final VerificationLien verification;

	Rendus(JdbcClient base, Hackathons hackathons, VerificationLien verification) {
		this.base = base;
		this.hackathons = hackathons;
		this.verification = verification;
	}

	/**
	 * Créer ou modifier le rendu de mon équipe. Dans l'ordre : le hackathon et l'équipe existent pour moi (404), je suis
	 * membre de l'équipe (403), le hackathon a commencé et n'est pas annulé (409 `etat_invalide`), l'heure limite n'est
	 * pas passée (409 `delai_depasse`), personne n'a modifié le rendu entre-temps (409 `modification_concurrente`).
	 */
	@Transactional
	Enregistre rendre(UUID hackathonId, UUID equipeId, UUID moi, Nouveau n) {
		Hackathons.Hackathon h = hackathons.charger(hackathonId, true);
		hackathons.role(h, moi).orElseThrow(ErreurHackathons::introuvable);
		equipeDe(hackathonId, equipeId);
		if (!membre(equipeId, moi)) {
			throw ErreurHackathons.interdit();
		}
		Instant maintenant = hackathons.maintenant();
		if ("annule".equals(h.statutBase()) || h.calendrier().debutLe().isAfter(maintenant)) {
			throw ErreurHackathons.conflit("etat_invalide", "Le hackathon n'a pas commencé");
		}
		if (!h.calendrier().finRendusLe().isAfter(maintenant)) {
			throw ErreurHackathons.conflit("delai_depasse", "L'heure limite des rendus est passée");
		}
		if (n.reponses().size() > h.questions().size()) {
			throw ErreurHackathons.validation("reponses", "trop_nombreux");
		}
		Optional<Rendu> existant = lireParEquipe(equipeId);
		Timestamp t = Timestamp.from(maintenant);
		if (existant.isEmpty()) {
			UUID id = UUID.randomUUID();
			base.sql("""
					INSERT INTO rendu (id, hackathon_id, equipe_id, titre, description, lien_produit, depot, video, reponses, modifie_le)
					VALUES (:id, :h, :e, :titre, :description, :lien, :depot, :video, :reponses, :t)
					""")
				.param("id", id).param("h", hackathonId).param("e", equipeId).param("titre", n.titre())
				.param("description", n.description()).param("lien", n.lienProduit().toString())
				.param("depot", texte(n.depot()), java.sql.Types.VARCHAR).param("video", texte(n.video()), java.sql.Types.VARCHAR)
				.param("reponses", n.reponses().toArray(String[]::new)).param("t", t).update();
			return new Enregistre(lireParEquipe(equipeId).orElseThrow(), true);
		}
		Rendu r = existant.get();
		if (n.version() == null || n.version() != r.version()) {
			throw ErreurHackathons.conflit("modification_concurrente", "Le rendu a changé entre-temps");
		}
		boolean memeLien = r.lienProduit().equals(n.lienProduit().toString());
		// Un nouveau lien n'a pas encore été vérifié : on efface l'ancien résultat.
		base.sql("""
				UPDATE rendu SET titre = :titre, description = :description, lien_produit = :lien, depot = :depot, video = :video,
				  reponses = :reponses, modifie_le = :t, version = version + 1
				""" + (memeLien ? "" : ", lien_repond = NULL, lien_verifie_le = NULL") + " WHERE id = :id")
			.param("titre", n.titre()).param("description", n.description()).param("lien", n.lienProduit().toString())
			.param("depot", texte(n.depot()), java.sql.Types.VARCHAR).param("video", texte(n.video()), java.sql.Types.VARCHAR)
			.param("reponses", n.reponses().toArray(String[]::new)).param("t", t).param("id", r.id()).update();
		return new Enregistre(lireParEquipe(equipeId).orElseThrow(), false);
	}

	/**
	 * Vérifie le lien du produit, **après** l'enregistrement du rendu. Si l'adaptateur ne sait pas vérifier, ou s'il
	 * tombe, le rendu reste « pas encore vérifié ». Le résultat n'écrase pas un lien changé pendant la vérification.
	 */
	Rendu verifierLien(Rendu r) {
		Optional<Boolean> repond;
		try {
			repond = verification.verifier(URI.create(r.lienProduit()));
		}
		catch (RuntimeException e) {
			repond = Optional.empty();
		}
		if (repond.isEmpty()) {
			return r;
		}
		base.sql("UPDATE rendu SET lien_repond = :r, lien_verifie_le = :t WHERE id = :id AND lien_produit = :lien")
			.param("r", repond.get()).param("t", Timestamp.from(hackathons.maintenant())).param("id", r.id())
			.param("lien", r.lienProduit()).update();
		return lireParEquipe(r.equipeId()).orElse(r);
	}

	/**
	 * Le rendu d'une équipe : pour ses membres et l'organisateur, et pour tout le monde une fois les résultats publiés.
	 * Pour les autres, il n'existe pas (404).
	 */
	@Transactional(readOnly = true)
	Rendu lire(UUID hackathonId, UUID equipeId, UUID moi) {
		Hackathons.Hackathon h = hackathons.charger(hackathonId, false);
		Hackathons.Lecture l = hackathons.role(h, moi).orElseThrow(ErreurHackathons::introuvable);
		equipeDe(hackathonId, equipeId);
		boolean visible = "organisateur".equals(l.role()) || "resultats".equals(h.statut(hackathons.maintenant()))
				|| moi != null && membre(equipeId, moi);
		if (!visible) {
			throw ErreurHackathons.introuvable();
		}
		return lireParEquipe(equipeId).orElseThrow(ErreurHackathons::introuvable);
	}

	private void equipeDe(UUID hackathonId, UUID equipeId) {
		base.sql("SELECT 1 FROM equipe WHERE id = :e AND hackathon_id = :h").param("e", equipeId).param("h", hackathonId)
			.query(Integer.class).optional().orElseThrow(ErreurHackathons::introuvable);
	}

	private boolean membre(UUID equipeId, UUID moi) {
		return base.sql("SELECT 1 FROM inscription WHERE equipe_id = :e AND utilisateur_id = :u AND statut <> 'refusee'")
			.param("e", equipeId).param("u", moi).query(Integer.class).optional().isPresent();
	}

	private Optional<Rendu> lireParEquipe(UUID equipeId) {
		return base.sql("SELECT " + COLONNES + " FROM rendu WHERE equipe_id = :e").param("e", equipeId).query(Rendus::rendu).optional();
	}

	private static String texte(URI lien) {
		return lien == null ? null : lien.toString();
	}

	private static Rendu rendu(ResultSet rs, int ligne) throws SQLException {
		boolean repond = rs.getBoolean("lien_repond");
		Boolean lienRepond = rs.wasNull() ? null : repond;
		Timestamp verifie = rs.getTimestamp("lien_verifie_le");
		return new Rendu(rs.getObject("id", UUID.class), rs.getObject("hackathon_id", UUID.class), rs.getObject("equipe_id", UUID.class),
				rs.getString("titre"), rs.getString("description"), rs.getString("lien_produit"), rs.getString("depot"),
				rs.getString("video"), List.of((String[]) rs.getArray("reponses").getArray()), lienRepond,
				verifie == null ? null : verifie.toInstant(), rs.getInt("version"), rs.getTimestamp("modifie_le").toInstant());
	}

}
