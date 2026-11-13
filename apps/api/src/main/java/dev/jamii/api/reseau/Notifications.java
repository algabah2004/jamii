package dev.jamii.api.reseau;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Les notifications (contracts/reseau.yaml), créées par l'API. Plusieurs messages d'une même personne pas encore lus
 * ne font qu'une notification, remontée en haut de la liste : la liste reste lisible même si quelqu'un écrit beaucoup.
 */
@Service
class Notifications {

	static final Duration CONSERVATION = Duration.ofDays(30);

	record Ligne(UUID id, String type, UUID personneId, UUID cercleId, String lien, Instant creeLe, Instant luLe) {
	}

	private final JdbcClient base;

	private final Clock horloge = Clock.systemUTC();

	Notifications(JdbcClient base) {
		this.base = base;
	}

	/** Prévenir `pour` : une seule notification non lue par type et par personne, rafraîchie si elle existe déjà. */
	@Transactional
	void notifier(UUID pour, String type, UUID personneId, UUID cercleId, String lien) {
		Timestamp maintenant = Timestamp.from(horloge.instant());
		int rafraichies = base.sql("""
				UPDATE notification SET cree_le = :le, cercle_id = :cercle, lien = :lien
				WHERE utilisateur_id = :pour AND type = :type AND personne_id IS NOT DISTINCT FROM :personne AND lu_le IS NULL
				""").param("le", maintenant).param("cercle", cercleId).param("lien", lien).param("pour", pour)
			.param("type", type).param("personne", personneId).update();
		if (rafraichies == 0) {
			base.sql("""
					INSERT INTO notification (id, utilisateur_id, type, personne_id, cercle_id, lien, cree_le)
					VALUES (:id, :pour, :type, :personne, :cercle, :lien, :le)""")
				.param("id", UUID.randomUUID()).param("pour", pour).param("type", type).param("personne", personneId)
				.param("cercle", cercleId).param("lien", lien).param("le", maintenant).update();
		}
	}

	/** Les notifications des 30 derniers jours, les plus récentes d'abord. */
	Messagerie.Page<Ligne> lister(UUID moi, int limite, String curseur) {
		Messagerie.Curseur apres = curseur == null ? null : Messagerie.Curseur.decoder(curseur);
		Instant maintenant = horloge.instant();
		List<Ligne> lignes = base.sql("""
				SELECT id, type, personne_id, cercle_id, lien, cree_le, lu_le FROM notification
				WHERE utilisateur_id = :moi AND cree_le > :depuis AND (:sansCurseur OR (cree_le, id) < (:date, :dernier))
				ORDER BY cree_le DESC, id DESC LIMIT :limite
				""").param("moi", moi).param("depuis", Timestamp.from(maintenant.minus(CONSERVATION)))
			.param("sansCurseur", apres == null)
			.param("date", Timestamp.from(apres == null ? Instant.EPOCH : apres.envoyeLe()))
			.param("dernier", apres == null ? new UUID(0, 0) : apres.id())
			.param("limite", limite + 1)
			.query((l, i) -> {
				Timestamp lu = l.getTimestamp("lu_le");
				return new Ligne(l.getObject("id", UUID.class), l.getString("type"), l.getObject("personne_id", UUID.class),
						l.getObject("cercle_id", UUID.class), l.getString("lien"), l.getTimestamp("cree_le").toInstant(),
						lu == null ? null : lu.toInstant());
			})
			.list();
		boolean suite = lignes.size() > limite;
		List<Ligne> elements = suite ? lignes.subList(0, limite) : lignes;
		Ligne derniere = elements.isEmpty() ? null : elements.get(elements.size() - 1);
		return new Messagerie.Page<>(elements, suite ? new Messagerie.Curseur(derniere.creeLe(), derniere.id()).encoder() : null);
	}

	void toutMarquerLu(UUID moi) {
		base.sql("UPDATE notification SET lu_le = :le WHERE utilisateur_id = :moi AND lu_le IS NULL")
			.param("le", Timestamp.from(horloge.instant())).param("moi", moi).update();
	}

	/** Lire la conversation avec quelqu'un rend lues ses notifications de message et d'invitation. */
	void marquerLuesDe(UUID moi, UUID personneId, List<String> types) {
		base.sql("""
				UPDATE notification SET lu_le = :le
				WHERE utilisateur_id = :moi AND personne_id = :personne AND type IN (:types) AND lu_le IS NULL""")
			.param("le", Timestamp.from(horloge.instant())).param("moi", moi).param("personne", personneId)
			.param("types", types).update();
	}

	int nonLues(UUID moi) {
		return base.sql("SELECT count(*) FROM notification WHERE utilisateur_id = :moi AND lu_le IS NULL AND cree_le > :depuis")
			.param("moi", moi).param("depuis", Timestamp.from(horloge.instant().minus(CONSERVATION)))
			.query(Integer.class).single();
	}

	List<Ligne> toutes(UUID moi) {
		return lister(moi, 1000, null).elements();
	}

}
