package dev.jamii.api.demo;

import static dev.jamii.api.demo.DonneesDemo.ABIDJAN;
import static dev.jamii.api.demo.DonneesDemo.AWA;
import static dev.jamii.api.demo.DonneesDemo.DAKAR;
import static dev.jamii.api.demo.DonneesDemo.FATOU;
import static dev.jamii.api.demo.DonneesDemo.KOFI;
import static dev.jamii.api.demo.DonneesDemo.MOUSSA;
import static dev.jamii.api.demo.DonneesDemo.NIA;
import static dev.jamii.api.demo.DonneesDemo.YANN;
import static dev.jamii.api.demo.DonneesDemo.id;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

import dev.jamii.api.demo.DonneesDemo.Personne;

/**
 * Les salons des cercles de démo (V20261115_1500). D'abord l'histoire de chaque cercle (création, arrivées, tirage,
 * tours), écrite seulement si son salon est vide. Puis une vraie conversation dans le cercle de Dakar, au 3e jour du
 * tour de Kofi : points du jour, un coup de main résolu, un autre ouvert. Identifiants stables, `ON CONFLICT DO NOTHING`.
 */
final class DemoSalonsCercles {

	private final JdbcClient base;

	private final Instant maintenant;

	DemoSalonsCercles(JdbcClient base, Instant maintenant) {
		this.base = base;
		this.maintenant = maintenant;
	}

	void charger() {
		detaillerTaches();
		histoire();
		dakar();
		abidjan();
	}

	/** L'histoire des cercles dont le salon est vide, dans l'ordre du temps (comme la migration pour les anciens). */
	private void histoire() {
		var cercles = new java.util.ArrayList<>(java.util.List.of(DAKAR, ABIDJAN, DonneesDemo.NAIROBI));
		cercles.addAll(DemoDecouverteCercles.identifiants());
		histoire(cercles);
	}

	/** L'histoire de ces cercles, seulement pour ceux dont le salon est encore vide. */
	void histoire(java.util.List<UUID> cercles) {
		base.sql("""
				INSERT INTO message_cercle (id, cercle_id, type, evenement, evenement_tour, evenement_personne, envoye_le)
				SELECT gen_random_uuid(), h.cercle_id, 'evenement', h.evenement, h.tour, h.personne, h.le
				FROM (
					SELECT c.id AS cercle_id, 'cercle_cree' AS evenement, NULL::INTEGER AS tour, c.createur_id AS personne, c.cree_le AS le, 0 AS rang
					FROM cercle c
					UNION ALL
					SELECT m.cercle_id, 'membre_rejoint', NULL, m.utilisateur_id, m.rejoint_le, 1
					FROM cercle_membre m JOIN cercle c ON c.id = m.cercle_id WHERE m.utilisateur_id <> c.createur_id
					UNION ALL
					SELECT c.id, 'tirage', NULL, COALESCE(c.tire_par, c.createur_id), c.tirage_le, 2 FROM cercle c WHERE c.tirage_le IS NOT NULL
					UNION ALL
					SELECT m.cercle_id, 'tour_ouvert', m.rang, m.utilisateur_id,
						COALESCE((SELECT p.livre_le FROM cercle_membre p WHERE p.cercle_id = m.cercle_id AND p.rang = m.rang - 1), c.tirage_le), 3
					FROM cercle_membre m JOIN cercle c ON c.id = m.cercle_id
					WHERE m.projet_id IS NOT NULL
					UNION ALL
					SELECT m.cercle_id, 'tour_livre', m.rang, m.utilisateur_id, m.livre_le, 2 FROM cercle_membre m WHERE m.livre_le IS NOT NULL
					UNION ALL
					SELECT c.id, 'cercle_termine', NULL, NULL, (SELECT max(livre_le) FROM cercle_membre WHERE cercle_id = c.id), 4
					FROM cercle c WHERE c.statut = 'termine'
				) h
				WHERE h.le IS NOT NULL AND h.cercle_id IN (:cercles)
				  AND NOT EXISTS (SELECT 1 FROM message_cercle x WHERE x.cercle_id = h.cercle_id)
				ORDER BY h.le, h.rang
				""").param("cercles", cercles).update();
	}

	/** Les tâches du tour de Kofi, détaillées : ce qu'il faut faire, quand c'est fini, et par quoi commencer. */
	private void detaillerTaches() {
		String[][] details = {
				{ "Synchroniser les pesées au retour du réseau", "haute",
						"Quand le téléphone retrouve du réseau, envoyer au serveur les pesées faites hors ligne.\nFini quand deux téléphones qui ont pesé hors ligne retrouvent les mêmes totaux, sans doublon." },
				{ "Enregistrer les pesées sans réseau", "haute",
						"Garder chaque pesée sur le téléphone tant qu'il n'y a pas de réseau.\nFini quand une pesée faite en mode avion est toujours là après redémarrage." },
				{ "Envoyer un reçu par SMS au producteur", "normale",
						"Après chaque pesée, le producteur reçoit un SMS : poids, prix du kilo, montant dû.\nFini quand le SMS arrive sur un vrai téléphone en moins d'une minute." },
				{ "Mettre l'API en ligne sur DataCloud", "normale",
						"Déployer l'API et sa base sur DataCloud, avec une adresse fixe.\nFini quand l'appli d'un peseur synchronise avec l'API en ligne." },
				{ "Former deux peseurs à l'application", "basse",
						"Une séance d'une heure à la coopérative, avec deux peseurs.\nFini quand chacun a saisi seul dix pesées." } };
		for (String[] d : details) {
			base.sql("""
					UPDATE tache SET priorite = :priorite, description = :description
					WHERE titre = :titre AND description IS NULL
					  AND projet_id = (SELECT projet_id FROM cercle_membre WHERE cercle_id = :c AND rang = 2)
					""").param("titre", d[0]).param("priorite", d[1]).param("description", d[2]).param("c", DAKAR).update();
		}
	}

	private void dakar() {
		texte(DAKAR, 1, AWA, "Bravo à tous pour mon tour, les tisserandes ont déjà 40 commandes ! Place au carnet de Kofi 🚀", heures(70));
		texte(DAKAR, 2, KOFI, "Merci ! J'ai découpé le projet en 12 tâches, prenez celles qui vous parlent.", heures(69));
		point(DAKAR, 3, MOUSSA, "Le schéma de la base (coopératives, producteurs, pesées).", "Les écrans de saisie des pesées.", null, heures(46));
		coupDeMain(DAKAR, 4, FATOU, "La synchro hors ligne écrase les pesées faites sur deux téléphones. Quelqu'un a déjà géré ça ?", heures(30),
				YANN, heures(27));
		texte(DAKAR, 5, YANN, "Oui : on garde un numéro de version par pesée et on fusionne côté serveur. Je t'envoie un exemple.", heures(29));
		point(DAKAR, 6, KOFI, "La connexion par numéro de téléphone.", "Le tableau des pesées du jour.", null, heures(5));
		point(DAKAR, 7, NIA, "Les tests de l'export CSV.", "L'export PDF pour la coopérative.",
				"Je n'ai pas les vrais formats de bordereau : Kofi, tu peux en demander un ?", heures(3));
		coupDeMain(DAKAR, 8, MOUSSA, "L'APK ne s'installe pas sur les vieux Android 8. Une idée ?", heures(1), null, null);
		texte(DAKAR, 9, AWA, "Appel demain 19 h pour la démo du tableau ? 🙌", Duration.ofMinutes(40));
		inserer(DAKAR, 10, KOFI, "decision", "On garde Flutter pour l'appli des pesées : tout le cercle le connaît.", null, null, null, heures(68), null, null);
		inserer(DAKAR, 11, KOFI, "decision", "Démo intermédiaire jeudi à 19 h, livraison du tour le mardi suivant.", null, null, null, heures(20), null, null);
	}

	private void abidjan() {
		texte(ABIDJAN, 1, YANN, "Bienvenue dans le cercle ! On s'organise ici : un point par jour, et le bouton « Je bloque » si ça coince.",
				heures(20));
		texte(ABIDJAN, 2, FATOU, "Hâte de commencer. Je peux prendre tout ce qui est design au premier tour.", heures(19));
	}

	// ---------------------------------------------------------------- écritures

	private void texte(UUID cercle, int n, Personne qui, String texte, Duration ilYa) {
		inserer(cercle, n, qui, "texte", texte, null, null, null, ilYa, null, null);
	}

	private void point(UUID cercle, int n, Personne qui, String fait, String prevu, String bloque, Duration ilYa) {
		inserer(cercle, n, qui, "point", null, fait, prevu, bloque, ilYa, null, null);
	}

	private void coupDeMain(UUID cercle, int n, Personne qui, String texte, Duration ilYa, Personne resoluPar, Duration resoluIlYa) {
		inserer(cercle, n, qui, "coup_de_main", texte, null, null, null, ilYa, resoluPar, resoluIlYa);
	}

	private void inserer(UUID cercle, int n, Personne qui, String type, String texte, String fait, String prevu, String bloque,
			Duration ilYa, Personne resoluPar, Duration resoluIlYa) {
		base.sql("""
				INSERT INTO message_cercle (id, cercle_id, auteur_id, type, texte, point_fait, point_prevu, point_bloque,
				                            resolu_le, resolu_par, envoye_le)
				SELECT :id, :c, :a, :type, :texte, :fait, :prevu, :bloque, :resoluLe, :resoluPar, :le
				WHERE EXISTS (SELECT 1 FROM cercle_membre WHERE cercle_id = :c AND utilisateur_id = :a)
				ON CONFLICT (id) DO NOTHING
				""")
			.param("id", id("salon-cercle:" + cercle + ":" + n)).param("c", cercle).param("a", qui.id()).param("type", type)
			.param("texte", texte).param("fait", fait).param("prevu", prevu).param("bloque", bloque)
			.param("resoluLe", resoluIlYa == null ? null : Timestamp.from(maintenant.minus(resoluIlYa)))
			.param("resoluPar", resoluPar == null ? null : resoluPar.id())
			.param("le", Timestamp.from(maintenant.minus(ilYa))).update();
	}

	private static Duration heures(long n) {
		return Duration.ofHours(n);
	}

}
