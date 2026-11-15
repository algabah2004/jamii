package dev.jamii.api.demo;

import static dev.jamii.api.demo.DonneesDemo.AWA;
import static dev.jamii.api.demo.DonneesDemo.FATOU;
import static dev.jamii.api.demo.DonneesDemo.KOFI;
import static dev.jamii.api.demo.DonneesDemo.MOUSSA;
import static dev.jamii.api.demo.DonneesDemo.NIA;
import static dev.jamii.api.demo.DonneesDemo.YANN;
import static dev.jamii.api.demo.DonneesDemo.id;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

import dev.jamii.api.demo.DonneesDemo.Personne;

/** Catalogue fictif du profil demo, ajouté aussi aux bases distantes déjà initialisées. */
final class DemoDecouverteHackathons {

	private record Defi(String titre, String theme, String ville, String lieu, String statut, int debut, int duree,
			long montant, String devise, String projet1, String projet2) { }

	private static final List<Defi> DEFIS = List.of(
			new Defi("Sahel Mobile Challenge", "Des services mobiles accessibles sans connexion permanente", "Bamako", "en_ligne", "publie", 4, 2, 500000, "XOF", "Carnet des coopératives", "Marché par SMS"),
			new Defi("Lagos Fintech Builders", "Des paiements simples pour les petits commerces", "Lagos", "hybride", "publie", 7, 3, 25000000, "NGN", "Caisse des artisans", "Reçus mobiles"),
			new Defi("Nairobi Open Source Days", "Des outils libres pour les associations de quartier", "Nairobi", "sur_place", "publie", 10, 2, 10000000, "KES", "Annuaire associatif", "Prêt de matériel"),
			new Defi("Accra Design for Impact", "Des interfaces inclusives pour les services essentiels", "Accra", "en_ligne", "publie", 13, 5, 500000, "GHS", "Services accessibles", "Parcours citoyens"),
			new Defi("Casablanca Green Tech", "Réduire les déchets et mieux partager les ressources", "Casablanca", "hybride", "publie", 16, 10, 1000000, "MAD", "Collecte de quartier", "Réemploi local"),
			new Defi("Douala Education Lab", "Des outils pédagogiques adaptés aux connexions limitées", "Douala", "sur_place", "publie", 20, 3, 400000, "XAF", "Cours hors ligne", "Bibliothèque partagée"),
			new Defi("Kigali Health Innovation", "Faciliter le quotidien des centres de santé", "Kigali", "en_ligne", "publie", 23, 14, 100000, "USD", "Rendez-vous santé", "Suivi des consultations"),
			new Defi("Tunis Data Weekend", "Ouvrir les données et les rendre utiles aux habitants", "Tunis", "hybride", "publie", 27, 2, 80000, "USD", "Données du quartier", "Mobilité ouverte"),
			new Defi("Dakar AI for Good", "Des assistants utiles aux petites entreprises", "Dakar", "hybride", "en_cours", -1, 3, 700000, "XOF", "Assistant démarches", "Conseiller de quartier"),
			new Defi("Cape Town Climate Code", "Construire des services pour la résilience climatique", "Cape Town", "en_ligne", "en_cours", -4, 10, 1500000, "ZAR", "Eau partagée", "Alertes chaleur"),
			new Defi("Cotonou Civic Tech", "Simplifier les démarches des associations locales", "Cotonou", "sur_place", "evaluation", -3, 2, 350000, "XOF", "Démarches associatives", "Budget participatif"),
			new Defi("Thiès Makers Festival", "Faire connaître les métiers et ateliers de proximité", "Thiès", "hybride", "resultats", -16, 3, 600000, "XOF", "Ateliers ouverts", "Carnet des réparateurs"));

	private final JdbcClient base;
	private final Instant maintenant;

	DemoDecouverteHackathons(JdbcClient base, Instant maintenant) {
		this.base = base;
		this.maintenant = maintenant;
	}

	static UUID identifiant(int numero) {
		return UUID.fromString("e2026115-0000-4000-8000-%012d".formatted(numero));
	}

	void charger() {
		for (int i = 0; i < DEFIS.size(); i++) charger(identifiant(i + 1), DEFIS.get(i), i == 0 ? AWA : NIA);
	}

	private Timestamp date(long jours) {
		return Timestamp.from(maintenant.plus(Duration.ofDays(jours)));
	}

	private UUID enfant(UUID h, String cle) {
		return id("hackathon:catalogue:" + h + ":" + cle);
	}

	private void charger(UUID h, Defi d, Personne organisateur) {
		boolean termine = d.statut().equals("resultats");
		int insere = base.sql("""
				INSERT INTO hackathon (id, organisateur_id, titre, theme, description, lieu, ville, inscriptions_ouvertes_le,
					inscriptions_fermees_le, debut_le, fin_rendus_le, resultats_le, taille_min, taille_max, mode_inscription,
					deploiement_obligatoire, code_de_conduite, questions, statut_base, version, cree_le, publie_le, resultats_publies_le)
				VALUES (:id, :organisateur, :titre, :theme, :description, :lieu, :ville, :ouvertes, :fermees, :debut, :fin, :resultats,
					2, 4, 'ouvert', FALSE, 'Démo fictive : respect de chacun, aucun harcèlement.',
					ARRAY['Quel problème concret résout votre projet ?'], :statut, 1, :cree, :ouvertes, :publies)
				ON CONFLICT (id) DO NOTHING
				""")
			.param("id", h).param("organisateur", organisateur.id()).param("titre", d.titre() + " · Démo")
			.param("theme", d.theme()).param("description", "Événement fictif de démonstration. " + d.theme()
					+ ". Les équipes construisent un prototype, le présentent et partagent leur code. Aucun événement réel ni inscription payante.")
			.param("lieu", d.lieu()).param("ville", d.ville()).param("ouvertes", date(d.debut() - 40))
			.param("fermees", date(d.debut() - 1)).param("debut", date(d.debut())).param("fin", date(d.debut() + d.duree()))
			.param("resultats", date(d.debut() + d.duree() + 2)).param("cree", date(d.debut() - 42))
			.param("statut", termine ? "resultats" : "publie")
			.param("publies", termine ? date(d.debut() + d.duree() + 2) : null, Types.TIMESTAMP).update();
		// Ne pas réinscrire un membre parti, recréer un rendu supprimé ou écraser un événement modifié.
		if (insere == 0) return;
		List<UUID> criteres = List.of(enfant(h, "critere:1"), enfant(h, "critere:2"), enfant(h, "critere:3"));
		List<String> noms = List.of("Utilité", "Qualité technique", "Présentation");
		List<Integer> poids = List.of(50, 30, 20);
		for (int i = 0; i < criteres.size(); i++) {
			base.sql("INSERT INTO critere (id, hackathon_id, ordre, nom, poids) VALUES (:id, :h, :o, :nom, :poids)")
				.param("id", criteres.get(i)).param("h", h).param("o", i + 1).param("nom", noms.get(i)).param("poids", poids.get(i)).update();
		}
		UUID prix = enfant(h, "prix");
		base.sql("INSERT INTO prix (id, hackathon_id, ordre, nom, montant_centimes, devise) VALUES (:id, :h, 1, 'Prix du jury · Démo', :montant, :devise)")
			.param("id", prix).param("h", h).param("montant", d.montant()).param("devise", d.devise()).update();
		List<List<Personne>> membres = List.of(List.of(KOFI, FATOU), List.of(MOUSSA, YANN));
		if (termine || d.statut().equals("evaluation")) {
			base.sql("INSERT INTO jure (hackathon_id, utilisateur_id, groupe, cree_le) VALUES (:h, :u, NULL, :t)")
				.param("h", h).param("u", AWA.id()).param("t", date(d.debut() - 2)).update();
		}
		for (int i = 0; i < membres.size(); i++) {
			UUID equipe = equipe(h, d, i, membres.get(i));
			if (!d.statut().equals("publie")) {
				UUID rendu = rendu(h, d, equipe, i);
				if (termine) {
					List<Integer> notes = i == 0 ? List.of(5, 4, 5) : List.of(4, 4, 3);
					for (int c = 0; c < criteres.size(); c++) {
						base.sql("INSERT INTO note (rendu_id, utilisateur_id, critere_id, valeur) VALUES (:r, :u, :c, :v)")
							.param("r", rendu).param("u", AWA.id()).param("c", criteres.get(c)).param("v", notes.get(c)).update();
					}
					base.sql("INSERT INTO appreciation (rendu_id, utilisateur_id, commentaire, modifie_le) VALUES (:r, :u, :c, :t)")
						.param("r", rendu).param("u", AWA.id()).param("c", "Projet fictif : une idée claire et un prototype à poursuivre.")
						.param("t", date(d.debut() + d.duree() + 1)).update();
					if (i == 0) base.sql("INSERT INTO gagnant (hackathon_id, prix_id, equipe_id) VALUES (:h, :p, :e)")
						.param("h", h).param("p", prix).param("e", equipe).update();
				}
			}
		}
	}

	private UUID equipe(UUID h, Defi d, int numero, List<Personne> membres) {
		UUID equipe = enfant(h, "equipe:" + numero);
		Timestamp creeLe = date(Math.min(d.debut() - 7, -3));
		base.sql("INSERT INTO equipe (id, hackathon_id, nom, chef_id, cree_le) VALUES (:id, :h, :nom, :chef, :t)")
			.param("id", equipe).param("h", h).param("nom", numero == 0 ? "Les Baobabs" : "Collectif Horizon")
			.param("chef", membres.getFirst().id()).param("t", creeLe).update();
		for (Personne p : membres) {
			base.sql("INSERT INTO inscription (id, hackathon_id, utilisateur_id, equipe_id, statut, cherche_equipe, cree_le) VALUES (:id, :h, :u, :e, 'acceptee', FALSE, :t)")
				.param("id", enfant(h, "inscription:" + p.cle())).param("h", h).param("u", p.id()).param("e", equipe).param("t", creeLe).update();
		}
		return equipe;
	}

	private UUID rendu(UUID h, Defi d, UUID equipe, int numero) {
		UUID rendu = enfant(h, "rendu:" + numero);
		base.sql("""
				INSERT INTO rendu (id, hackathon_id, equipe_id, titre, description, lien_produit, reponses, version, modifie_le)
				VALUES (:id, :h, :e, :titre, :description, :lien, :reponses, 1, :t)
				""")
			.param("id", rendu).param("h", h).param("e", equipe).param("titre", numero == 0 ? d.projet1() : d.projet2())
			.param("description", "Prototype fictif pour la démonstration de « " + d.titre() + " ». " + d.theme() + ".")
			.param("lien", "https://" + rendu + ".demo.jamii.test").param("reponses", new String[] { d.theme() })
			.param("t", date(Math.min(d.debut() + d.duree() - 1, 0))).update();
		return rendu;
	}
}
