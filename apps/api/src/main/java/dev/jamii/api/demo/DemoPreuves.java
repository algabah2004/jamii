package dev.jamii.api.demo;

import static dev.jamii.api.demo.DonneesDemo.AWA;
import static dev.jamii.api.demo.DonneesDemo.FATOU;
import static dev.jamii.api.demo.DonneesDemo.YANN;
import static dev.jamii.api.demo.DonneesDemo.id;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

import dev.jamii.api.auth.Annuaire;

/**
 * Ce qui remplit l'indice de fiabilité des profils de la démo (#157) : il est calculé à partir de ce qui s'est passé
 * sur Jamii (profils.Preuves), il faut donc que cela se soit passé.
 * <ul>
 * <li>les tâches livrées des cercles reçoivent l'échéance qu'elles avaient : la plupart livrées à temps, une sur cinq
 * en retard (un indice parfait ne serait pas crédible) ;</li>
 * <li>deux missions terminées, avec l'avis du client : Fatou et Yann ;</li>
 * <li>pour chaque visiteur ({@link #pourVisiteur}), sa propre mission terminée, sur invitation : personne d'autre ne la
 * voit.</li>
 * </ul>
 * Aucun projet « en ligne » : ce badge dit qu'une vraie adresse a répondu à la vérification, la démo ne l'invente pas.
 * <p>
 * Rejoué à chaque démarrage, sans doublon.
 */
final class DemoPreuves {

	private static final long PRIX_XOF = 30_000_000L;

	private final JdbcClient base;

	private final Instant maintenant;

	DemoPreuves(JdbcClient base, Instant maintenant) {
		this.base = base;
		this.maintenant = maintenant;
	}

	void charger() {
		echeances();
		mission("logo-tisserandes", AWA.id(), FATOU.id(), "Le logo et les étiquettes des tisserandes de Thiès",
				"Un logo simple, lisible en petit sur une étiquette cousue, et les étiquettes prêtes à imprimer.", 40, 5,
				"Fatou a écouté les tisserandes avant de dessiner. Le logo leur ressemble, et les étiquettes sont déjà cousues.");
		mission("site-couturieres", DemoCommunautes.AICHA.id(), YANN.id(), "Le site de l'association des couturières de Bamako",
				"Une page par couturière, avec ses modèles et son numéro WhatsApp. Facile à mettre à jour depuis un téléphone.", 25, 5,
				"Livré avant la date, et Yann a formé deux couturières pour qu'elles ajoutent leurs modèles elles-mêmes.");
	}

	/** La mission terminée d'un visiteur : un avis de 4 sur 5, honnête sur un jalon en retard. */
	void pourVisiteur(UUID visiteur, String cle) {
		mission("tontine:" + cle, DemoCommunautes.AICHA.id(), visiteur, "Le carnet en ligne de la tontine du marché de Médina",
				"Noter qui a cotisé chaque semaine et qui reçoit la cagnotte, sans cahier qui se perd.", 15, 4,
				"Le carnet marche bien et les membres le consultent chaque semaine. Un jalon a pris deux jours de plus que prévu.",
				"invitation");
	}

	/** Les tâches livrées des personnes de la démo, sans échéance : celle qu'elles avaient, à 2 jours près. */
	private void echeances() {
		base.sql("""
				UPDATE tache SET echeance = (livree_le AT TIME ZONE 'UTC')::date
					+ CASE WHEN get_byte(uuid_send(id), 15) % 5 = 0 THEN -1 ELSE 2 END
				WHERE statut = 'livree' AND echeance IS NULL
				  AND assignee_id IN (SELECT id FROM utilisateur WHERE email LIKE :domaine)
				""").param("domaine", "%" + Annuaire.DOMAINE_DEMO).update();
	}

	/** Une mission terminée : proposition retenue, devis accepté, deux jalons acceptés et payés, l'avis du client. */
	private void mission(String cle, UUID client, UUID developpeur, String titre, String besoin, int ilYaJours, int note, String avis) {
		mission(cle, client, developpeur, titre, besoin, ilYaJours, note, avis, "publique");
	}

	private void mission(String cle, UUID client, UUID developpeur, String titre, String besoin, int ilYaJours, int note, String avis,
			String visibilite) {
		UUID mission = id("mission:terminee:" + cle);
		Timestamp publiee = ilYA(Duration.ofDays(ilYaJours + 6));
		Timestamp demarree = ilYA(Duration.ofDays(ilYaJours + 3));
		Timestamp terminee = ilYA(Duration.ofDays(ilYaJours));
		base.sql("""
				INSERT INTO mission (id, client_id, titre, besoin, competences, budget_centimes, devise, visibilite, a_distance,
					statut, version, cree_le, publiee_le, developpeur_id, demarre_le)
				VALUES (:id, :client, :titre, :besoin, ARRAY['web'], :prix, 'XOF', :visibilite, TRUE, 'terminee', 6, :publiee,
					:publiee, :dev, :demarree)
				ON CONFLICT (id) DO NOTHING
				""").param("id", mission).param("client", client).param("titre", titre).param("besoin", besoin).param("prix", PRIX_XOF)
			.param("visibilite", visibilite).param("publiee", publiee).param("dev", developpeur).param("demarree", demarree).update();
		UUID proposition = id("mission:terminee:" + cle + ":proposition");
		base.sql("""
				INSERT INTO proposition (id, mission_id, developpeur_id, message, prix_centimes, devise, delai_jours, statut, cree_le)
				VALUES (:id, :m, :dev, 'Je propose deux jalons, chacun montré avant de continuer.', :prix, 'XOF', 10, 'acceptee', :t)
				ON CONFLICT (id) DO NOTHING
				""").param("id", proposition).param("m", mission).param("dev", developpeur).param("prix", PRIX_XOF).param("t", publiee).update();
		UUID devis = id("mission:terminee:" + cle + ":devis");
		base.sql("""
				INSERT INTO devis (id, mission_id, proposition_id, total_centimes, devise, retouches_comprises, conditions, cree_le, accepte_le)
				VALUES (:id, :m, :p, :prix, 'XOF', 1, 'Paiement à chaque jalon accepté, par paiement mobile.', :t, :t)
				ON CONFLICT (id) DO NOTHING
				""").param("id", devis).param("m", mission).param("p", proposition).param("prix", PRIX_XOF).param("t", demarree).update();
		for (int ordre = 1; ordre <= 2; ordre++) {
			Timestamp accepte = ordre == 2 ? terminee : ilYA(Duration.ofDays(ilYaJours + 1));
			base.sql("""
					INSERT INTO jalon_mission (id, devis_id, ordre, titre, montant_centimes, delai_jours, statut, livre_le, accepte_le, paiement)
					VALUES (:id, :d, :o, :titre, :montant, 5, 'accepte', :t, :t, 'declare_recu')
					ON CONFLICT (id) DO NOTHING
					""").param("id", id("mission:terminee:" + cle + ":jalon:" + ordre)).param("d", devis).param("o", ordre)
				.param("titre", ordre == 1 ? "La première version, montrée au client" : "La version finale")
				.param("montant", PRIX_XOF / 2).param("t", accepte).update();
		}
		base.sql("""
				INSERT INTO avis_mission (mission_id, auteur, auteur_id, note, texte, donne_le)
				VALUES (:m, 'client', :client, :note, :texte, :t)
				ON CONFLICT (mission_id, auteur) DO NOTHING
				""").param("m", mission).param("client", client).param("note", note).param("texte", avis).param("t", terminee).update();
	}

	private Timestamp ilYA(Duration duree) {
		return Timestamp.from(maintenant.minus(duree));
	}

}
