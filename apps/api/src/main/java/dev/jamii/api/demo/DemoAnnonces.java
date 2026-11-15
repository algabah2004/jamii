package dev.jamii.api.demo;

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
 * Ce qu'un visiteur voit en ouvrant « Missions » et « Hackathons » pendant la démo : trois missions publiées, avec
 * des propositions, et un hackathon aux inscriptions ouvertes. Sans elles, ces deux listes publiques sont vides (la
 * mission et le hackathon de {@link DemoHackathonMission} sont en cours ou terminés).
 * <p>
 * Comme les communautés, ces annonces se complètent à chaque démarrage (ON CONFLICT DO NOTHING) : une base déjà
 * chargée, comme celle en ligne, les reçoit sans rien perdre. Les dates partent du premier chargement.
 */
final class DemoAnnonces {

	static final UUID HACKATHON_OUVERT = id("hackathon:abidjan-sante");

	private final JdbcClient base;

	private final Instant maintenant;

	DemoAnnonces(JdbcClient base, Instant maintenant) {
		this.base = base;
		this.maintenant = maintenant;
	}

	void completer() {
		mission("rendez-vous-sante", FATOU, "Prendre rendez-vous au centre de santé par SMS",
				"Le centre de santé de notre quartier veut que les patients prennent rendez-vous par SMS, avec un rappel la veille. "
						+ "Beaucoup de patients n'ont pas de smartphone : tout doit marcher avec un simple SMS.",
				List.of("spring", "sms"), 400_000, "Dakar", "SN", List.of(KOFI, NIA));
		mission("soutien-scolaire", MOUSSA, "Mettre en relation élèves et étudiants du quartier",
				"Notre association veut un site simple où un élève de terminale trouve un étudiant du quartier pour du soutien, "
						+ "avec les horaires de chacun. Les parents doivent pouvoir suivre les séances.",
				List.of("vue", "postgres"), 300_000, "Bamako", "ML", List.of(NIA));
		mission("salle-de-quartier", YANN, "Réserver une salle de la mairie de quartier",
				"La mairie veut afficher ses salles libres et laisser les habitants les réserver pour un baptême ou une réunion, "
						+ "avec une confirmation par SMS et un paiement mobile de la caution.",
				List.of("react", "paiement-mobile"), 500_000, "Abidjan", "CI", List.of());
		hackathon();
	}

	private Timestamp dans(Duration duree) {
		return Timestamp.from(maintenant.plus(duree));
	}

	// ---------------------------------------------------------------- les missions publiées

	private void mission(String cle, Personne client, String titre, String besoin, List<String> competences, long budgetXof,
			String ville, String pays, List<Personne> candidats) {
		UUID mission = id("mission:" + cle);
		base.sql("""
				INSERT INTO mission (id, client_id, titre, besoin, competences, budget_centimes, devise, date_souhaitee, visibilite,
					ville, pays, a_distance, statut, version, cree_le, publiee_le)
				VALUES (:id, :client, :titre, :besoin, :competences, :budget, 'XOF', :souhaitee, 'publique', :ville, :pays, TRUE,
					'publiee', 2, :cree, :publiee)
				ON CONFLICT (id) DO NOTHING
				""")
			.param("id", mission).param("client", client.id()).param("titre", titre).param("besoin", besoin)
			.param("competences", competences.toArray(String[]::new)).param("budget", budgetXof)
			.param("souhaitee", java.sql.Date.valueOf(maintenant.plus(Duration.ofDays(30)).atZone(java.time.ZoneOffset.UTC).toLocalDate()))
			.param("ville", ville).param("pays", pays).param("cree", dans(Duration.ofDays(-3)))
			.param("publiee", dans(Duration.ofDays(-3))).update();
		for (Personne qui : candidats) {
			base.sql("""
					INSERT INTO proposition (id, mission_id, developpeur_id, message, prix_centimes, devise, delai_jours, statut, cree_le)
					VALUES (:id, :m, :d, :message, :prix, 'XOF', 21, 'envoyee', :t)
					ON CONFLICT DO NOTHING
					""")
				.param("id", id("mission:" + cle + ":proposition:" + qui.cle())).param("m", mission).param("d", qui.id())
				.param("message", "J'ai déjà construit un service proche pour une association. Je propose trois jalons, "
						+ "chacun montré avant de continuer.")
				.param("prix", budgetXof).param("t", dans(Duration.ofDays(-2))).update();
		}
	}

	// ---------------------------------------------------------------- le hackathon ouvert

	/** Inscriptions ouvertes depuis la veille du premier chargement, pour 4 jours : Awa et Kofi peuvent s'inscrire. */
	private void hackathon() {
		Timestamp ouvertes = dans(Duration.ofDays(-1));
		base.sql("""
				INSERT INTO hackathon (id, organisateur_id, titre, theme, description, lieu, ville, inscriptions_ouvertes_le,
					inscriptions_fermees_le, debut_le, fin_rendus_le, resultats_le, taille_min, taille_max, mode_inscription,
					deploiement_obligatoire, code_de_conduite, questions, statut_base, version, cree_le, publie_le)
				VALUES (:id, :nia, 'Abidjan Santé Hack', 'Les centres de santé de quartier', :description, 'hybride', 'Abidjan',
					:ouvertes, :fermees, :debut, :fin, :resultats, 2, 4, 'ouvert', TRUE,
					'Respect de chacun, aucun harcèlement, le code de chaque équipe reste le sien.',
					ARRAY['Quel patient aidez-vous, et comment ?'], 'publie', 1, :ouvertes, :ouvertes)
				ON CONFLICT (id) DO NOTHING
				""")
			.param("id", HACKATHON_OUVERT).param("nia", NIA.id())
			.param("description", "Un week-end pour construire, en équipe, un service qui aide un centre de santé de quartier : "
					+ "rendez-vous, rappels, files d'attente. Chaque projet est mis en ligne sur Systalink DataCloud.")
			.param("ouvertes", ouvertes).param("fermees", dans(Duration.ofDays(4))).param("debut", dans(Duration.ofDays(5)))
			.param("fin", dans(Duration.ofDays(7))).param("resultats", dans(Duration.ofDays(9))).update();
		int ordre = 1;
		for (var c : List.of(List.of("Utilité pour les patients", "50"), List.of("Qualité technique", "30"),
				List.of("Mise en ligne sur DataCloud", "20"))) {
			base.sql("INSERT INTO critere (id, hackathon_id, ordre, nom, poids) VALUES (:id, :h, :o, :n, :p) ON CONFLICT DO NOTHING")
				.param("id", id("hackathon:abidjan-sante:critere:" + ordre)).param("h", HACKATHON_OUVERT).param("o", ordre)
				.param("n", c.get(0)).param("p", Integer.parseInt(c.get(1))).update();
			ordre++;
		}
		base.sql("""
				INSERT INTO prix (id, hackathon_id, ordre, nom, montant_centimes, devise, sponsor)
				VALUES (:id, :h, 1, 'Un an d''hébergement sur DataCloud', NULL, NULL, 'Systalink')
				ON CONFLICT DO NOTHING
				""")
			.param("id", id("hackathon:abidjan-sante:prix:1")).param("h", HACKATHON_OUVERT).update();
		// Deux personnes seules qui cherchent une équipe : de quoi montrer « Je cherche une équipe ».
		for (Personne p : List.of(MOUSSA, YANN)) {
			base.sql("""
					INSERT INTO inscription (id, hackathon_id, utilisateur_id, equipe_id, statut, cherche_equipe, competences, cree_le)
					VALUES (:id, :h, :u, NULL, 'acceptee', TRUE, :c, :t)
					ON CONFLICT DO NOTHING
					""")
				.param("id", id("hackathon:abidjan-sante:inscription:" + p.cle())).param("h", HACKATHON_OUVERT).param("u", p.id())
				.param("c", new String[] { "spring", "ux-design" }).param("t", ouvertes).update();
		}
	}

}
