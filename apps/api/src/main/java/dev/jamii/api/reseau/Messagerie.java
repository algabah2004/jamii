package dev.jamii.api.reseau;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

	/** `salleAppel` : un ancien appel par Jitsi, avant les appels WebRTC (#157). Ces messages restent lisibles. */
	record Ligne(UUID id, UUID expediteurId, UUID destinataireId, String texte, UUID cercleId, Instant envoyeLe,
			Instant luLe, String salleAppel, List<UUID> pieces, UUID reponseA, Instant modifieLe, Instant supprimeLe,
			boolean transfere) {

		Ligne(UUID id, UUID expediteurId, UUID destinataireId, String texte, UUID cercleId, Instant envoyeLe, Instant luLe,
				String salleAppel, List<UUID> pieces) {
			this(id, expediteurId, destinataireId, texte, cercleId, envoyeLe, luLe, salleAppel, pieces, null, null, null, false);
		}

	}

	/** On peut corriger un message pendant 15 minutes après l'avoir envoyé (#157). */
	static final Duration DELAI_MODIFICATION = Duration.ofMinutes(15);

	/** Un transfert part vers 5 conversations au plus : au-delà, c'est de la diffusion. */
	static final int TRANSFERT_MAX = 5;

	/** Un appel est « en cours » pendant une heure après son lancement, comme dans les salons. */
	static final Duration DUREE_APPEL = Duration.ofHours(1);

	/** Le texte des messages qui annoncent un appel, pour l'export et les écrans qui ne connaissent pas les appels. */
	static final String ANNONCE_APPEL = "📞 Appel vocal";

	static final String ANNONCE_APPEL_VIDEO = "📹 Appel vidéo";

	/** La dernière ligne de chaque conversation, l'autre personne et ses messages non lus. */
	record Fil(UUID autre, Ligne dernier, int nonLus) {
	}

	record Page<T>(List<T> elements, String curseurSuivant) {
	}

	private static final String COLONNES = "id, expediteur_id, destinataire_id, texte, cercle_id, envoye_le, lu_le, salle_appel, pieces, reponse_a, modifie_le, supprime_le, transfere";

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
		return envoyer(moi, destinataire, texteBrut, cercleId, List.of());
	}

	/** Un message avec ses pièces jointes (déjà vérifiées par le module fichiers) : le texte devient alors facultatif. */
	@Transactional
	Ligne envoyer(UUID moi, UUID destinataire, String texteBrut, UUID cercleId, List<UUID> pieces) {
		return envoyer(moi, destinataire, texteBrut, cercleId, pieces, null);
	}

	/** `reponseA` : le message auquel on répond, forcément dans la même conversation. */
	@Transactional
	Ligne envoyer(UUID moi, UUID destinataire, String texteBrut, UUID cercleId, List<UUID> pieces, UUID reponseA) {
		String texte = texteBrut == null ? "" : texteBrut.strip();
		if (texte.isEmpty() && pieces.isEmpty()) {
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
		if (reponseA != null && dansLaConversation(reponseA, moi, destinataire).isEmpty()) {
			throw ErreurReseau.validation("reponseA", "inconnu");
		}
		Instant maintenant = horloge.instant();
		limiter(moi, destinataire, maintenant);

		return ecrire(moi, destinataire, texte, cercleId, maintenant, null, pieces, reponseA, false);
	}

	// ---------------------------------------------------------------- modifier, supprimer, transférer (#157)

	/** Un message de la conversation entre deux personnes, dans un sens ou dans l'autre. */
	Optional<Ligne> dansLaConversation(UUID id, UUID moi, UUID autre) {
		return base.sql("SELECT " + COLONNES + " FROM message WHERE id = :id AND ((expediteur_id = :moi AND destinataire_id = :autre) OR (expediteur_id = :autre AND destinataire_id = :moi))")
			.param("id", id).param("moi", moi).param("autre", autre).query((l, i) -> ligne(l)).optional();
	}

	/** Les messages cités par d'autres, par identifiant. */
	Map<UUID, Ligne> lignes(Collection<UUID> ids) {
		if (ids.isEmpty()) return Map.of();
		Map<UUID, Ligne> trouvees = new HashMap<>();
		base.sql("SELECT " + COLONNES + " FROM message WHERE id IN (:ids)").param("ids", ids)
			.query((l, i) -> ligne(l)).list().forEach(l -> trouvees.put(l.id(), l));
		return trouvees;
	}

	/** Mon message, pas encore supprimé, et ni une invitation ni un appel : seul son texte change. */
	private Ligne monMessage(UUID moi, UUID autre, UUID id) {
		var ligne = dansLaConversation(id, moi, autre).filter(l -> l.expediteurId().equals(moi)).orElseThrow(ErreurReseau::introuvable);
		if (ligne.supprimeLe() != null) {
			throw ErreurReseau.conflit("message_supprime", "Ce message a été supprimé");
		}
		return ligne;
	}

	@Transactional
	Ligne modifier(UUID moi, UUID autre, UUID id, String texteBrut, boolean estUnAppel) {
		var ligne = monMessage(moi, autre, id);
		if (ligne.cercleId() != null || ligne.salleAppel() != null || estUnAppel) {
			throw ErreurReseau.conflit("non_modifiable", "Une invitation ou un appel ne se modifie pas");
		}
		Instant maintenant = horloge.instant();
		if (maintenant.isAfter(ligne.envoyeLe().plus(DELAI_MODIFICATION))) {
			throw ErreurReseau.conflit("delai_depasse", "Un message se modifie pendant 15 minutes après son envoi");
		}
		String texte = texteBrut == null ? "" : texteBrut.strip();
		if (texte.isEmpty() && ligne.pieces().isEmpty()) {
			throw ErreurReseau.validation("texte", "obligatoire");
		}
		if (texte.length() > LONGUEUR_MAX) {
			throw ErreurReseau.validation("texte", "trop_long");
		}
		if (texte.equals(ligne.texte())) {
			return ligne;
		}
		base.sql("UPDATE message SET texte = :texte, modifie_le = :le WHERE id = :id")
			.param("texte", texte).param("le", Timestamp.from(maintenant)).param("id", id).update();
		return dansLaConversation(id, moi, autre).orElseThrow();
	}

	/**
	 * Supprime mon message pour tout le monde : il garde sa place (« Message supprimé »), mais son texte, son invitation
	 * et ses pièces jointes disparaissent. Renvoie les pièces jointes qu'il avait, pour que leurs fichiers soient effacés.
	 */
	@Transactional
	List<UUID> supprimer(UUID moi, UUID autre, UUID id) {
		var ligne = dansLaConversation(id, moi, autre).filter(l -> l.expediteurId().equals(moi)).orElseThrow(ErreurReseau::introuvable);
		if (ligne.supprimeLe() != null) {
			return List.of();
		}
		base.sql("UPDATE message SET texte = '', pieces = '{}', cercle_id = NULL, supprime_le = :le WHERE id = :id")
			.param("le", Timestamp.from(horloge.instant())).param("id", id).update();
		return ligne.pieces();
	}

	/**
	 * Transfère un message que je peux lire (envoyé ou reçu) vers d'autres conversations : une copie par personne,
	 * marquée « transféré ». `copier` fait une copie privée de ses fichiers pour chaque destinataire.
	 */
	@Transactional
	List<Ligne> transferer(UUID moi, UUID autre, UUID id, List<UUID> destinataires, boolean estUnAppel,
			java.util.function.BiFunction<List<UUID>, UUID, List<UUID>> copier) {
		var source = dansLaConversation(id, moi, autre).orElseThrow(ErreurReseau::introuvable);
		if (source.supprimeLe() != null) {
			throw ErreurReseau.conflit("message_supprime", "Ce message a été supprimé");
		}
		if (source.cercleId() != null || source.salleAppel() != null || estUnAppel) {
			throw ErreurReseau.conflit("non_transferable", "Une invitation ou un appel ne se transfère pas");
		}
		List<UUID> uniques = destinataires == null ? List.of() : new ArrayList<>(new LinkedHashSet<>(destinataires));
		if (uniques.isEmpty() || uniques.size() > TRANSFERT_MAX) {
			throw ErreurReseau.validation("destinataires", uniques.isEmpty() ? "obligatoire" : "trop_nombreux");
		}
		Instant maintenant = horloge.instant();
		List<Ligne> copies = new ArrayList<>();
		for (UUID destinataire : uniques) {
			if (moi.equals(destinataire)) {
				throw ErreurReseau.validation("destinataires", "soi_meme");
			}
			if (annuaire.lire(destinataire).isEmpty()) {
				throw ErreurReseau.introuvable();
			}
			limiter(moi, destinataire, maintenant);
			copies.add(ecrire(moi, destinataire, source.texte(), null, maintenant, null, copier.apply(source.pieces(), destinataire), null, true));
		}
		return copies;
	}

	/**
	 * Le message qui garde la trace d'un appel WebRTC dans la conversation (#157, {@link Appels}). Mêmes règles qu'un
	 * message (pas à soi-même, limite anti-spam), puisque c'en est un. La personne appelée en est notifiée.
	 */
	@Transactional
	Ligne annoncerAppel(UUID moi, UUID destinataire, boolean video, Instant maintenant) {
		if (moi.equals(destinataire)) {
			throw ErreurReseau.conflit("soi_meme", "On ne s'appelle pas soi-même");
		}
		if (annuaire.lire(destinataire).isEmpty()) {
			throw ErreurReseau.introuvable();
		}
		limiter(moi, destinataire, maintenant);
		return ecrire(moi, destinataire, video ? ANNONCE_APPEL_VIDEO : ANNONCE_APPEL, null, maintenant, null, List.of(), null, false);
	}

	private Ligne ecrire(UUID moi, UUID destinataire, String texte, UUID cercleId, Instant maintenant, String salle,
			List<UUID> pieces, UUID reponseA, boolean transfere) {
		var ligne = new Ligne(UUID.randomUUID(), moi, destinataire, texte, cercleId, maintenant, null, salle, pieces, reponseA,
				null, null, transfere);
		base.sql("INSERT INTO message (" + COLONNES + ") VALUES (:id, :de, :a, :texte, :cercle, :le, NULL, :salle, CAST(:pieces AS UUID[]), :reponseA, NULL, NULL, :transfere)")
			.param("reponseA", reponseA).param("transfere", transfere)
			.param("pieces", dev.jamii.api.fichiers.Fichiers.tableau(pieces))
			.param("id", ligne.id())
			.param("de", moi)
			.param("a", destinataire)
			.param("texte", texte)
			.param("cercle", cercleId)
			.param("le", Timestamp.from(maintenant))
			.param("salle", salle)
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
				l.getTimestamp("envoye_le").toInstant(), lu == null ? null : lu.toInstant(), l.getString("salle_appel"),
				dev.jamii.api.fichiers.Fichiers.lireTableau(l, "pieces"), l.getObject("reponse_a", UUID.class),
				instant(l, "modifie_le"), instant(l, "supprime_le"), l.getBoolean("transfere"));
	}

	private static Instant instant(java.sql.ResultSet l, String colonne) throws java.sql.SQLException {
		Timestamp t = l.getTimestamp(colonne);
		return t == null ? null : t.toInstant();
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
