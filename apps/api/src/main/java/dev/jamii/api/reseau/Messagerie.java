package dev.jamii.api.reseau;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jamii.api.auth.Annuaire;

/**
 * Les messages entre deux personnes (contracts/reseau.yaml). Une conversation est la paire de personnes, dans un sens
 * ou dans l'autre. Contre le spam : 30 messages par heure, et 10 premières prises de contact par jour.
 */
@Service
class Messagerie {

	static final int LONGUEUR_MAX = 2000;

	static final int MESSAGES_PAR_HEURE = 30;

	static final int PREMIERS_CONTACTS_PAR_JOUR = 10;

	record Ligne(UUID id, UUID expediteurId, UUID destinataireId, String texte, UUID cercleId, Instant envoyeLe,
			Instant luLe) {
	}

	/** La dernière ligne de chaque conversation, l'autre personne et ses messages non lus. */
	record Fil(UUID autre, Ligne dernier, int nonLus) {
	}

	record Page<T>(List<T> elements, String curseurSuivant) {
	}

	private static final String COLONNES = "id, expediteur_id, destinataire_id, texte, cercle_id, envoye_le, lu_le";

	private final JdbcClient base;

	private final Annuaire annuaire;

	private final LectureCercles cercles;

	private final Notifications notifications;

	private final Clock horloge = Clock.systemUTC();

	/** Les notifications qu'une conversation lue rend lues. */
	static final List<String> NOTIFICATIONS_DE_MESSAGE = List.of("message_recu", "invitation_cercle");

	Messagerie(JdbcClient base, Annuaire annuaire, LectureCercles cercles, Notifications notifications) {
		this.base = base;
		this.annuaire = annuaire;
		this.cercles = cercles;
		this.notifications = notifications;
	}

	@Transactional
	Ligne envoyer(UUID moi, UUID destinataire, String texteBrut, UUID cercleId) {
		String texte = texteBrut == null ? "" : texteBrut.strip();
		if (texte.isEmpty()) {
			throw ErreurReseau.validation("texte", "obligatoire");
		}
		if (texte.length() > LONGUEUR_MAX) {
			throw ErreurReseau.validation("texte", "trop_long");
		}
		if (moi.equals(destinataire)) {
			throw ErreurReseau.conflit("soi_meme", "On ne s'écrit pas à soi-même");
		}
		if (annuaire.lire(destinataire).isEmpty()) {
			throw ErreurReseau.introuvable();
		}
		if (cercleId != null) {
			var cercle = cercles.lire(cercleId).orElseThrow(ErreurReseau::introuvable);
			if (!cercle.membres().contains(moi)) {
				throw ErreurReseau.conflit("pas_membre_du_cercle", "Seuls les membres du cercle peuvent inviter");
			}
			if (!cercle.accueille()) {
				throw ErreurReseau.conflit("cercle_ferme", "Le cercle est complet ou déjà tiré");
			}
		}
		Instant maintenant = horloge.instant();
		limiter(moi, destinataire, maintenant);

		var ligne = new Ligne(UUID.randomUUID(), moi, destinataire, texte, cercleId, maintenant, null);
		base.sql("INSERT INTO message (" + COLONNES + ") VALUES (:id, :de, :a, :texte, :cercle, :le, NULL)")
			.param("id", ligne.id())
			.param("de", moi)
			.param("a", destinataire)
			.param("texte", texte)
			.param("cercle", cercleId)
			.param("le", Timestamp.from(maintenant))
			.update();
		notifications.notifier(destinataire, cercleId == null ? "message_recu" : "invitation_cercle", moi, cercleId,
				"/messages/" + moi);
		return ligne;
	}

	private void limiter(UUID moi, UUID destinataire, Instant maintenant) {
		int derniereHeure = base.sql("SELECT count(*) FROM message WHERE expediteur_id = :moi AND envoye_le > :depuis")
			.param("moi", moi)
			.param("depuis", Timestamp.from(maintenant.minus(Duration.ofHours(1))))
			.query(Integer.class)
			.single();
		if (derniereHeure >= MESSAGES_PAR_HEURE) {
			throw ErreurReseau.tropDeRequetes(Duration.ofHours(1).toSeconds());
		}
		boolean dejaEchange = base.sql("""
				SELECT EXISTS (SELECT 1 FROM message WHERE (expediteur_id = :moi AND destinataire_id = :autre)
				  OR (expediteur_id = :autre AND destinataire_id = :moi))
				""").param("moi", moi).param("autre", destinataire).query(Boolean.class).single();
		if (dejaEchange) {
			return;
		}
		// Les conversations que j'ai ouvertes moi-même depuis 24 h : leur premier message vient de moi.
		int premiersContacts = base.sql("""
				SELECT count(*) FROM (
				  SELECT DISTINCT ON (LEAST(expediteur_id, destinataire_id), GREATEST(expediteur_id, destinataire_id))
				    expediteur_id, envoye_le
				  FROM message WHERE expediteur_id = :moi OR destinataire_id = :moi
				  ORDER BY LEAST(expediteur_id, destinataire_id), GREATEST(expediteur_id, destinataire_id), envoye_le) premiers
				WHERE expediteur_id = :moi AND envoye_le > :depuis
				""").param("moi", moi).param("depuis", Timestamp.from(maintenant.minus(Duration.ofDays(1))))
			.query(Integer.class)
			.single();
		if (premiersContacts >= PREMIERS_CONTACTS_PAR_JOUR) {
			throw ErreurReseau.tropDeRequetes(Duration.ofHours(1).toSeconds());
		}
	}

	/** Mes conversations, la plus récente d'abord. */
	Page<Fil> conversations(UUID moi, int limite, String curseur) {
		Curseur apres = curseur == null ? null : Curseur.decoder(curseur);
		List<Fil> fils = base.sql("""
				WITH derniers AS (
				  SELECT DISTINCT ON (LEAST(expediteur_id, destinataire_id), GREATEST(expediteur_id, destinataire_id))
				    %s, CASE WHEN expediteur_id = :moi THEN destinataire_id ELSE expediteur_id END AS autre
				  FROM message WHERE expediteur_id = :moi OR destinataire_id = :moi
				  ORDER BY LEAST(expediteur_id, destinataire_id), GREATEST(expediteur_id, destinataire_id), envoye_le DESC, id DESC)
				SELECT d.*, (SELECT count(*) FROM message n
				  WHERE n.expediteur_id = d.autre AND n.destinataire_id = :moi AND n.lu_le IS NULL) AS non_lus
				FROM derniers d
				WHERE :sansCurseur OR (d.envoye_le, d.id) < (:date, :dernier)
				ORDER BY d.envoye_le DESC, d.id DESC LIMIT :limite
				""".formatted(COLONNES))
			.param("moi", moi)
			.param("sansCurseur", apres == null)
			.param("date", Timestamp.from(apres == null ? Instant.EPOCH : apres.envoyeLe()))
			.param("dernier", apres == null ? new UUID(0, 0) : apres.id())
			.param("limite", limite + 1)
			.query((l, i) -> new Fil(l.getObject("autre", UUID.class), ligne(l), l.getInt("non_lus")))
			.list();
		return page(fils, limite, f -> new Curseur(f.dernier().envoyeLe(), f.dernier().id()));
	}

	/** Les messages échangés avec une personne, les plus récents d'abord ; ceux que j'ai reçus passent à « lus ». */
	@Transactional
	Page<Ligne> lire(UUID moi, UUID autre, int limite, String curseur) {
		Curseur apres = curseur == null ? null : Curseur.decoder(curseur);
		List<Ligne> lignes = base.sql("""
				SELECT %s FROM message
				WHERE LEAST(expediteur_id, destinataire_id) = LEAST(:moi, :autre)
				  AND GREATEST(expediteur_id, destinataire_id) = GREATEST(:moi, :autre)
				  AND (:sansCurseur OR (envoye_le, id) < (:date, :dernier))
				ORDER BY envoye_le DESC, id DESC LIMIT :limite
				""".formatted(COLONNES))
			.param("moi", moi)
			.param("autre", autre)
			.param("sansCurseur", apres == null)
			.param("date", Timestamp.from(apres == null ? Instant.EPOCH : apres.envoyeLe()))
			.param("dernier", apres == null ? new UUID(0, 0) : apres.id())
			.param("limite", limite + 1)
			.query((l, i) -> ligne(l))
			.list();
		base.sql("UPDATE message SET lu_le = :le WHERE expediteur_id = :autre AND destinataire_id = :moi AND lu_le IS NULL")
			.param("le", Timestamp.from(horloge.instant()))
			.param("autre", autre)
			.param("moi", moi)
			.update();
		notifications.marquerLuesDe(moi, autre, NOTIFICATIONS_DE_MESSAGE);
		return page(lignes, limite, m -> new Curseur(m.envoyeLe(), m.id()));
	}

	int nonLus(UUID moi) {
		return base.sql("SELECT count(*) FROM message WHERE destinataire_id = :moi AND lu_le IS NULL")
			.param("moi", moi).query(Integer.class).single();
	}

	/** Les personnes déjà invitées à ce cercle (par n'importe quel membre). */
	List<UUID> invites(UUID cercleId) {
		return base.sql("SELECT DISTINCT destinataire_id FROM message WHERE cercle_id = :c")
			.param("c", cercleId).query(UUID.class).list();
	}

	/** Pour l'export du compte : mes messages, envoyés et reçus. */
	List<Ligne> tous(UUID moi) {
		return base.sql("SELECT " + COLONNES + " FROM message WHERE expediteur_id = :moi OR destinataire_id = :moi ORDER BY envoye_le, id")
			.param("moi", moi).query((l, i) -> ligne(l)).list();
	}

	private static Ligne ligne(java.sql.ResultSet l) throws java.sql.SQLException {
		Timestamp lu = l.getTimestamp("lu_le");
		return new Ligne(l.getObject("id", UUID.class), l.getObject("expediteur_id", UUID.class),
				l.getObject("destinataire_id", UUID.class), l.getString("texte"), l.getObject("cercle_id", UUID.class),
				l.getTimestamp("envoye_le").toInstant(), lu == null ? null : lu.toInstant());
	}

	private static <T> Page<T> page(List<T> lignes, int limite, java.util.function.Function<T, Curseur> cle) {
		boolean suite = lignes.size() > limite;
		List<T> elements = suite ? lignes.subList(0, limite) : lignes;
		return new Page<>(elements, suite ? cle.apply(elements.get(elements.size() - 1)).encoder() : null);
	}

	/** Le curseur : la date et l'identifiant du dernier élément de la page, en base64 (opaque pour le front). */
	record Curseur(Instant envoyeLe, UUID id) {

		String encoder() {
			return Base64.getUrlEncoder().withoutPadding()
				.encodeToString((envoyeLe + "|" + id).getBytes(StandardCharsets.UTF_8));
		}

		static Curseur decoder(String texte) {
			try {
				String[] morceaux = new String(Base64.getUrlDecoder().decode(texte), StandardCharsets.UTF_8).split("\\|");
				return new Curseur(Instant.parse(morceaux[0]), UUID.fromString(morceaux[1]));
			}
			catch (RuntimeException e) {
				throw ErreurReseau.validation("curseur", "format_invalide");
			}
		}

	}

}
