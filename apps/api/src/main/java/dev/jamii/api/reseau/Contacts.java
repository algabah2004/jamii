package dev.jamii.api.reseau;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jamii.api.auth.Annuaire;

/**
 * Les contacts (contracts/reseau.yaml) : une demande, puis une acceptation. Une seule ligne par paire de personnes.
 * Demander à quelqu'un qui m'a déjà demandé, c'est accepter. Contre le spam : 20 demandes par jour.
 */
@Service
class Contacts {

	static final int DEMANDES_PAR_JOUR = 20;

	/** Le lien vu par `moi` : aucun, envoyee, recue ou accepte. */
	record Lien(String statut, Instant depuis) {

		static final Lien AUCUN = new Lien("aucun", null);

	}

	record Ligne(UUID autre, String statut, Instant depuis) {
	}

	private final JdbcClient base;

	private final Annuaire annuaire;

	private final Notifications notifications;

	private final Clock horloge = Clock.systemUTC();

	Contacts(JdbcClient base, Annuaire annuaire, Notifications notifications) {
		this.base = base;
		this.annuaire = annuaire;
		this.notifications = notifications;
	}

	Lien lien(UUID moi, UUID autre) {
		return ligne(moi, autre).map(l -> new Lien(statut(l, moi), l.accepte ? l.accepteLe : l.demandeLe)).orElse(Lien.AUCUN);
	}

	@Transactional
	Lien ajouter(UUID moi, UUID autre) {
		if (moi.equals(autre)) {
			throw ErreurReseau.conflit("soi_meme", "On ne s'ajoute pas soi-même");
		}
		if (annuaire.lire(autre).isEmpty()) {
			throw ErreurReseau.introuvable();
		}
		Instant maintenant = horloge.instant();
		var existante = ligne(moi, autre);
		if (existante.isPresent()) {
			var l = existante.get();
			// Elle m'avait demandé : j'accepte. Sinon (déjà envoyée ou acceptée), rien ne change : la route est idempotente.
			if (!l.accepte && l.destinataireId.equals(moi)) {
				base.sql("UPDATE contact SET accepte = TRUE, accepte_le = :le WHERE demandeur_id = :autre AND destinataire_id = :moi")
					.param("le", Timestamp.from(maintenant)).param("autre", autre).param("moi", moi).update();
				notifications.notifier(autre, "contact_accepte", moi, null, "/contacts");
				return new Lien("accepte", maintenant);
			}
			return lien(moi, autre);
		}
		int aujourdhui = base.sql("SELECT count(*) FROM contact WHERE demandeur_id = :moi AND demande_le > :depuis")
			.param("moi", moi).param("depuis", Timestamp.from(maintenant.minusSeconds(86_400))).query(Integer.class).single();
		if (aujourdhui >= DEMANDES_PAR_JOUR) {
			throw ErreurReseau.tropDeRequetes(3600);
		}
		// Deux demandes croisées au même instant : l'index de la paire n'en garde qu'une, sans erreur.
		int creees = base.sql("INSERT INTO contact (demandeur_id, destinataire_id, demande_le) VALUES (:moi, :autre, :le) ON CONFLICT DO NOTHING")
			.param("moi", moi).param("autre", autre).param("le", Timestamp.from(maintenant)).update();
		if (creees == 0) {
			return lien(moi, autre);
		}
		notifications.notifier(autre, "demande_contact", moi, null, "/contacts");
		return new Lien("envoyee", maintenant);
	}

	/** Refuser, annuler ou retirer : la ligne disparaît. Idempotent. */
	void retirer(UUID moi, UUID autre) {
		base.sql("""
				DELETE FROM contact WHERE (demandeur_id = :moi AND destinataire_id = :autre)
				  OR (demandeur_id = :autre AND destinataire_id = :moi)""").param("moi", moi).param("autre", autre).update();
	}

	/** Mes contacts (`accepte`), les demandes reçues (`recue`) ou envoyées (`envoyee`), les plus récents d'abord. */
	List<Ligne> lister(UUID moi, String statut, int limite) {
		String condition = switch (statut) {
			case "recue" -> "NOT accepte AND destinataire_id = :moi";
			case "envoyee" -> "NOT accepte AND demandeur_id = :moi";
			default -> "accepte AND (demandeur_id = :moi OR destinataire_id = :moi)";
		};
		return base.sql("""
				SELECT CASE WHEN demandeur_id = :moi THEN destinataire_id ELSE demandeur_id END AS autre,
				  COALESCE(accepte_le, demande_le) AS depuis
				FROM contact WHERE %s ORDER BY depuis DESC LIMIT :limite""".formatted(condition))
			.param("moi", moi).param("limite", limite)
			.query((l, i) -> new Ligne(l.getObject("autre", UUID.class), statut, l.getTimestamp("depuis").toInstant()))
			.list();
	}

	int demandesRecues(UUID moi) {
		return base.sql("SELECT count(*) FROM contact WHERE destinataire_id = :moi AND NOT accepte")
			.param("moi", moi).query(Integer.class).single();
	}

	private record Brute(UUID demandeurId, UUID destinataireId, boolean accepte, Instant demandeLe, Instant accepteLe) {
	}

	private Optional<Brute> ligne(UUID moi, UUID autre) {
		return base.sql("""
				SELECT demandeur_id, destinataire_id, accepte, demande_le, accepte_le FROM contact
				WHERE (demandeur_id = :moi AND destinataire_id = :autre) OR (demandeur_id = :autre AND destinataire_id = :moi)""")
			.param("moi", moi).param("autre", autre)
			.query((l, i) -> {
				Timestamp accepte = l.getTimestamp("accepte_le");
				return new Brute(l.getObject("demandeur_id", UUID.class), l.getObject("destinataire_id", UUID.class),
						l.getBoolean("accepte"), l.getTimestamp("demande_le").toInstant(),
						accepte == null ? null : accepte.toInstant());
			})
			.optional();
	}

	private static String statut(Brute l, UUID moi) {
		if (l.accepte) {
			return "accepte";
		}
		return l.demandeurId.equals(moi) ? "envoyee" : "recue";
	}

}
