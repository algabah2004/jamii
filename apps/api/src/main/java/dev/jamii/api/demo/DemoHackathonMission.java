package dev.jamii.api.demo;

import static dev.jamii.api.demo.DonneesDemo.AWA;
import static dev.jamii.api.demo.DonneesDemo.FATOU;
import static dev.jamii.api.demo.DonneesDemo.KOFI;
import static dev.jamii.api.demo.DonneesDemo.MOUSSA;
import static dev.jamii.api.demo.DonneesDemo.NIA;
import static dev.jamii.api.demo.DonneesDemo.YANN;
import static dev.jamii.api.demo.DonneesDemo.id;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

import dev.jamii.api.demo.DonneesDemo.Personne;

/**
 * Le hackathon et la mission de la démo (#181) :
 * <ul>
 * <li>« Dakar Cloud Hack », terminé : deux équipes ont rendu leur projet, Nia les a notés, Awa a publié les résultats ;</li>
 * <li>la boutique des tisserandes, une mission d'Awa en cours avec Kofi : le premier jalon est accepté et payé, le
 * deuxième vient d'être livré et attend qu'Awa l'accepte (l'action à montrer pendant la démo).</li>
 * </ul>
 * Écrit directement en base, comme le reste des données de démo, dans la même transaction que `DonneesDemo`.
 */
final class DemoHackathonMission {

	static final UUID HACKATHON = id("hackathon:dakar-cloud");
	static final UUID MISSION = id("mission:tisserandes");

	private static final long JALON_XOF = 250_000;

	private final JdbcClient base;

	private final Instant maintenant;

	DemoHackathonMission(JdbcClient base, Instant maintenant) {
		this.base = base;
		this.maintenant = maintenant;
	}

	void charger() {
		hackathon();
		mission();
	}

	private Timestamp ilYA(Duration duree) {
		return Timestamp.from(maintenant.minus(duree));
	}

	private static Duration jours(int n) {
		return Duration.ofDays(n);
	}

	// ---------------------------------------------------------------- le hackathon

	private void hackathon() {
		base.sql("""
				INSERT INTO hackathon (id, organisateur_id, titre, theme, description, lieu, ville, inscriptions_ouvertes_le,
					inscriptions_fermees_le, debut_le, fin_rendus_le, resultats_le, taille_min, taille_max, mode_inscription,
					deploiement_obligatoire, code_de_conduite, questions, statut_base, version, cree_le, publie_le, resultats_publies_le)
				VALUES (:id, :awa, 'Dakar Cloud Hack', 'Des services utiles aux commerçants, hébergés sur le cloud Systalink',
					:description, 'hybride', 'Dakar', :ouvertes, :fermees, :debut, :fin, :resultats, 2, 4, 'ouvert', TRUE,
					'Respect de chacun, aucun harcèlement, le code de chaque équipe reste le sien.',
					ARRAY['Quel problème d''un commerçant résolvez-vous ?'], 'resultats', 3, :cree, :ouvertes, :publies)
				""")
			.param("id", HACKATHON).param("awa", AWA.id())
			.param("description", "Deux jours pour construire, en équipe, un service qui aide un commerçant de votre quartier. "
					+ "Chaque projet est mis en ligne sur Systalink DataCloud.")
			.param("ouvertes", ilYA(jours(30))).param("fermees", ilYA(jours(20))).param("debut", ilYA(jours(18)))
			.param("fin", ilYA(jours(16))).param("resultats", ilYA(jours(14))).param("cree", ilYA(jours(32)))
			.param("publies", ilYA(jours(13))).update();

		List<UUID> criteres = List.of(critere(1, "Utilité pour un commerçant", 50), critere(2, "Qualité technique", 30),
				critere(3, "Mise en ligne sur DataCloud", 20));
		UUID prixJury = prix(1, "Prix du jury", 500_000L, null);
		UUID prixDataCloud = prix(2, "Meilleur projet sur DataCloud", null, "Systalink");

		UUID baobabs = equipe("baobabs", "Les Baobabs", KOFI, FATOU);
		UUID teranga = equipe("teranga", "Teranga Dev", MOUSSA, YANN);
		UUID stock = rendu(baobabs, "Stock au marché",
				"Les vendeuses du marché Sandaga suivent leur stock par SMS : un message pour une entrée, un autre pour une vente.",
				"https://stock-au-marche.demo.jamii.test", "Une vendeuse ne sait pas ce qu'il lui reste avant d'aller au grossiste.");
		UUID caisse = rendu(teranga, "Carnet de caisse",
				"Un carnet de caisse sur téléphone pour les boutiques de quartier, qui marche sans réseau et se synchronise le soir.",
				"https://carnet-de-caisse.demo.jamii.test", "Le boutiquier perd la trace de ce qu'on lui doit.");

		base.sql("INSERT INTO jure (hackathon_id, utilisateur_id, groupe, cree_le) VALUES (:h, :u, NULL, :t)")
			.param("h", HACKATHON).param("u", NIA.id()).param("t", ilYA(jours(19))).update();
		noter(stock, criteres, List.of(5, 4, 4), "Très utile : les vendeuses l'ont essayé devant nous. Ajoutez un récapitulatif hebdomadaire.");
		noter(caisse, criteres, List.of(4, 3, 3), "Bonne idée et le mode hors ligne marche. La mise en ligne reste à finir.");

		// Le n-ième prix revient à l'équipe classée n-ième : aucun ajustement, donc aucune raison.
		gagnant(prixJury, baobabs);
		gagnant(prixDataCloud, teranga);
	}

	private UUID critere(int ordre, String nom, int poids) {
		UUID critere = id("hackathon:dakar-cloud:critere:" + ordre);
		base.sql("INSERT INTO critere (id, hackathon_id, ordre, nom, poids) VALUES (:id, :h, :o, :n, :p)")
			.param("id", critere).param("h", HACKATHON).param("o", ordre).param("n", nom).param("p", poids).update();
		return critere;
	}

	private UUID prix(int ordre, String nom, Long montantXof, String sponsor) {
		UUID prix = id("hackathon:dakar-cloud:prix:" + ordre);
		base.sql("""
				INSERT INTO prix (id, hackathon_id, ordre, nom, montant_centimes, devise, sponsor)
				VALUES (:id, :h, :o, :n, :m, :d, :s)
				""")
			.param("id", prix).param("h", HACKATHON).param("o", ordre).param("n", nom)
			.param("m", montantXof, java.sql.Types.BIGINT).param("d", montantXof == null ? null : "XOF", java.sql.Types.CHAR)
			.param("s", sponsor, java.sql.Types.VARCHAR).update();
		return prix;
	}

	/** L'équipe, son chef en premier, et l'inscription acceptée de chacun de ses membres. */
	private UUID equipe(String cle, String nom, Personne chef, Personne... membres) {
		UUID equipe = id("hackathon:dakar-cloud:equipe:" + cle);
		base.sql("INSERT INTO equipe (id, hackathon_id, nom, chef_id, cree_le) VALUES (:id, :h, :n, :c, :t)")
			.param("id", equipe).param("h", HACKATHON).param("n", nom).param("c", chef.id()).param("t", ilYA(jours(25))).update();
		List<Personne> tous = new java.util.ArrayList<>(List.of(chef));
		tous.addAll(List.of(membres));
		for (Personne p : tous) {
			base.sql("""
					INSERT INTO inscription (id, hackathon_id, utilisateur_id, equipe_id, statut, cherche_equipe, cree_le)
					VALUES (:id, :h, :u, :e, 'acceptee', FALSE, :t)
					""")
				.param("id", id("hackathon:dakar-cloud:inscription:" + p.cle())).param("h", HACKATHON).param("u", p.id())
				.param("e", equipe).param("t", ilYA(jours(25))).update();
		}
		return equipe;
	}

	private UUID rendu(UUID equipe, String titre, String description, String lien, String reponse) {
		UUID rendu = id("hackathon:dakar-cloud:rendu:" + equipe);
		base.sql("""
				INSERT INTO rendu (id, hackathon_id, equipe_id, titre, description, lien_produit, reponses, lien_repond,
					lien_verifie_le, version, modifie_le)
				VALUES (:id, :h, :e, :titre, :description, :lien, :reponses, TRUE, :t, 1, :t)
				""")
			.param("id", rendu).param("h", HACKATHON).param("e", equipe).param("titre", titre).param("description", description)
			.param("lien", lien).param("reponses", new String[] { reponse }).param("t", ilYA(jours(16).plusHours(1))).update();
		return rendu;
	}

	private void noter(UUID rendu, List<UUID> criteres, List<Integer> valeurs, String commentaire) {
		for (int i = 0; i < criteres.size(); i++) {
			base.sql("INSERT INTO note (rendu_id, utilisateur_id, critere_id, valeur) VALUES (:r, :u, :c, :v)")
				.param("r", rendu).param("u", NIA.id()).param("c", criteres.get(i)).param("v", valeurs.get(i)).update();
		}
		base.sql("INSERT INTO appreciation (rendu_id, utilisateur_id, commentaire, modifie_le) VALUES (:r, :u, :c, :t)")
			.param("r", rendu).param("u", NIA.id()).param("c", commentaire).param("t", ilYA(jours(15))).update();
	}

	private void gagnant(UUID prix, UUID equipe) {
		base.sql("INSERT INTO gagnant (hackathon_id, prix_id, equipe_id) VALUES (:h, :p, :e)")
			.param("h", HACKATHON).param("p", prix).param("e", equipe).update();
	}

	// ---------------------------------------------------------------- la mission

	private void mission() {
		base.sql("""
				INSERT INTO mission (id, client_id, titre, besoin, competences, budget_centimes, devise, visibilite, ville, pays,
					a_distance, statut, version, cree_le, publiee_le, developpeur_id, informations_depart, demarre_le)
				VALUES (:id, :awa, 'La boutique en ligne des tisserandes de Thiès', :besoin, ARRAY['vue', 'paiement-mobile'],
					750000, 'XOF', 'publique', 'Thiès', 'SN', TRUE, 'en_cours', 5, :cree, :cree, :kofi, :depart, :demarre)
				""")
			.param("id", MISSION).param("awa", AWA.id()).param("kofi", KOFI.id())
			.param("besoin", "Les tisserandes de Thiès veulent montrer leurs pagnes en ligne, recevoir les commandes par WhatsApp "
					+ "et être payées par paiement mobile. Elles n'ont qu'un téléphone : tout doit marcher dessus.")
			.param("depart", "Les photos des pagnes sont dans le dossier partagé. Contact sur place : la présidente du groupement.")
			.param("cree", ilYA(jours(12))).param("demarre", ilYA(jours(8))).update();

		UUID propositionKofi = proposition(KOFI, "acceptee",
				"J'ai déjà fait une boutique pour une coopérative de cacao. Je propose trois jalons, chacun montré aux tisserandes.");
		proposition(FATOU, "non_retenue", "Je peux faire la boutique en Flutter, avec une version qui marche sans réseau.");

		UUID devis = id("mission:tisserandes:devis");
		base.sql("""
				INSERT INTO devis (id, mission_id, proposition_id, total_centimes, devise, retouches_comprises, conditions, cree_le, accepte_le)
				VALUES (:id, :m, :p, :total, 'XOF', 2, 'Paiement à chaque jalon accepté, par paiement mobile.', :cree, :accepte)
				""")
			.param("id", devis).param("m", MISSION).param("p", propositionKofi).param("total", 3 * JALON_XOF)
			.param("cree", ilYA(jours(10))).param("accepte", ilYA(jours(9))).update();

		UUID catalogue = jalon(devis, 1, "Le catalogue des pagnes", 5, "accepte", ilYA(jours(4)), ilYA(jours(3)), "declare_recu");
		UUID commande = jalon(devis, 2, "La commande par WhatsApp", 5, "livre", ilYA(Duration.ofHours(2)), null, "non_paye");
		jalon(devis, 3, "Le paiement mobile et la mise en ligne sur DataCloud", 7, "en_cours", null, null, "non_paye");

		livraison(catalogue, "Le catalogue est en ligne : 24 pagnes, avec motif, taille et prix. Testé sur un téléphone d'entrée de gamme.",
				ilYA(jours(4)));
		livraison(commande, "Le bouton « Commander » ouvre WhatsApp avec le pagne choisi. Les commandes arrivent dans un tableau.",
				ilYA(Duration.ofHours(2)));
	}

	private UUID proposition(Personne qui, String statut, String message) {
		UUID proposition = id("mission:tisserandes:proposition:" + qui.cle());
		base.sql("""
				INSERT INTO proposition (id, mission_id, developpeur_id, message, prix_centimes, devise, delai_jours, statut, cree_le)
				VALUES (:id, :m, :d, :msg, 750000, 'XOF', 20, :s, :t)
				""")
			.param("id", proposition).param("m", MISSION).param("d", qui.id()).param("msg", message).param("s", statut)
			.param("t", ilYA(jours(11))).update();
		return proposition;
	}

	private UUID jalon(UUID devis, int ordre, String titre, int delai, String statut, Timestamp livre, Timestamp accepte, String paiement) {
		UUID jalon = id("mission:tisserandes:jalon:" + ordre);
		base.sql("""
				INSERT INTO jalon_mission (id, devis_id, ordre, titre, montant_centimes, delai_jours, statut, livre_le, accepte_le, paiement)
				VALUES (:id, :d, :o, :titre, :montant, :delai, :statut, :livre, :accepte, :paiement)
				""")
			.param("id", jalon).param("d", devis).param("o", ordre).param("titre", titre).param("montant", JALON_XOF)
			.param("delai", delai).param("statut", statut).param("livre", livre, java.sql.Types.TIMESTAMP)
			.param("accepte", accepte, java.sql.Types.TIMESTAMP).param("paiement", paiement).update();
		return jalon;
	}

	private void livraison(UUID jalon, String message, Timestamp quand) {
		base.sql("INSERT INTO livraison (id, jalon_id, message, liens, livree_le) VALUES (:id, :j, :msg, :liens, :t)")
			.param("id", id("mission:livraison:" + jalon)).param("j", jalon).param("msg", message)
			.param("liens", new String[] { "https://tisserandes.demo.jamii.test" }).param("t", quand).update();
	}

}
