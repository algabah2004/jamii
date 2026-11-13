package dev.jamii.api.communautes;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Répondre, accepter une réponse, valider la réponse de l'IA (contracts/communautes.yaml). */
@Service
class GestionReponses {

	/** Contre le spam (contrat) : au plus 30 réponses par heure et par personne. */
	static final int REPONSES_PAR_HEURE = 30;

	/** Tout ce qu'il faut savoir d'une réponse pour décider qui peut l'accepter ou la valider. */
	record Cible(UUID reponseId, UUID publicationId, UUID communauteId, String type, UUID auteurQuestion,
			UUID auteurReponse, boolean proposeeParIa) {
	}

	private final JdbcClient base;

	GestionReponses(JdbcClient base) {
		this.base = base;
	}

	@Transactional(readOnly = true)
	Optional<Cible> cible(UUID reponseId) {
		return base.sql("""
				SELECT r.id, r.publication_id, p.communaute_id, p.type, p.auteur_id AS auteur_question,
				       r.auteur_id AS auteur_reponse, r.proposee_par_ia
				FROM reponse r JOIN publication p ON p.id = r.publication_id WHERE r.id = :r
				""").param("r", reponseId)
			.query((rs, n) -> new Cible(rs.getObject("id", UUID.class), rs.getObject("publication_id", UUID.class),
					rs.getObject("communaute_id", UUID.class), rs.getString("type"),
					rs.getObject("auteur_question", UUID.class), rs.getObject("auteur_reponse", UUID.class),
					rs.getBoolean("proposee_par_ia")))
			.optional();
	}

	/** Secondes à attendre avant de pouvoir répondre à nouveau, 0 si c'est permis. Compté en base. */
	@Transactional(readOnly = true)
	long attenteAvantReponse(UUID moi, Instant maintenant) {
		List<Timestamp> recentes = base.sql("""
				SELECT cree_le FROM reponse WHERE auteur_id = :u AND cree_le > :depuis ORDER BY cree_le LIMIT :max
				""").param("u", moi).param("depuis", Timestamp.from(maintenant.minus(Duration.ofHours(1))))
			.param("max", REPONSES_PAR_HEURE).query(Timestamp.class).list();
		if (recentes.size() < REPONSES_PAR_HEURE) {
			return 0;
		}
		return Math.max(1, Duration.between(maintenant, recentes.get(0).toInstant().plus(Duration.ofHours(1))).toSeconds());
	}

	@Transactional
	UUID repondre(UUID publicationId, UUID auteur, String contenu, Instant maintenant) {
		UUID id = UUID.randomUUID();
		base.sql("INSERT INTO reponse (id, publication_id, auteur_id, contenu, cree_le) VALUES (:id, :p, :a, :c, :t)")
			.param("id", id).param("p", publicationId).param("a", auteur).param("c", contenu)
			.param("t", Timestamp.from(maintenant)).update();
		return id;
	}

	/**
	 * Une seule réponse acceptée par question, même si deux personnes cliquent en même temps : la mise à jour ne passe
	 * que si aucune réponse n'est encore acceptée. Renvoie faux si une autre l'a été avant.
	 */
	@Transactional
	boolean accepter(UUID publicationId, UUID reponseId, Instant maintenant) {
		return base.sql("""
				UPDATE publication SET reponse_acceptee_id = :r, acceptee_le = :t, version = version + 1
				WHERE id = :p AND reponse_acceptee_id IS NULL
				""").param("r", reponseId).param("t", Timestamp.from(maintenant)).param("p", publicationId).update() == 1;
	}

	/** Le pair se porte garant de la réponse de l'IA : elle devient la réponse acceptée, dans la même transaction. */
	@Transactional
	boolean valider(UUID publicationId, UUID reponseId, UUID pair, Instant maintenant) {
		if (!accepter(publicationId, reponseId, maintenant)) {
			return false;
		}
		base.sql("UPDATE reponse SET validee_par_id = :pair WHERE id = :r").param("pair", pair).param("r", reponseId).update();
		return true;
	}

	/** La réponse de l'IA arrive. Ignorée si la proposition n'est plus attendue (déjà prête ou abandonnée). */
	@Transactional
	void propositionPrete(UUID publicationId, String texte, Instant maintenant) {
		int attendue = base.sql("UPDATE publication SET proposition_ia = 'prete' WHERE id = :p AND proposition_ia = 'en_attente'")
			.param("p", publicationId).update();
		if (attendue == 1) {
			base.sql("""
					INSERT INTO reponse (id, publication_id, auteur_id, contenu, proposee_par_ia, cree_le)
					VALUES (:id, :p, NULL, :c, TRUE, :t)
					""").param("id", UUID.randomUUID()).param("p", publicationId)
				.param("c", texte.length() > 10000 ? texte.substring(0, 10000) : texte)
				.param("t", Timestamp.from(maintenant)).update();
		}
	}

	@Transactional
	void propositionIndisponible(UUID publicationId) {
		base.sql("UPDATE publication SET proposition_ia = 'indisponible' WHERE id = :p AND proposition_ia = 'en_attente'")
			.param("p", publicationId).update();
	}

}
