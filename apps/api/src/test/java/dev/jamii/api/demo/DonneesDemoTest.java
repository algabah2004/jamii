package dev.jamii.api.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.annotation.Transactional;

import dev.jamii.api.TestcontainersConfiguration;
import dev.jamii.api.auth.Role;
import dev.jamii.api.auth.UtilisateurConnecte;

/** Le profil `demo` charge les données de la démo au démarrage, lisibles par les vraies routes de l'API. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@Import(TestcontainersConfiguration.class)
class DonneesDemoTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient base;

	@Autowired
	DonneesDemo demo;

	private static UtilisateurConnecte connecte(DonneesDemo.Personne p) {
		return new UtilisateurConnecte(p.id(), Set.of(Role.MEMBRE));
	}

	private long compter(String sql) {
		return base.sql(sql).query(Long.class).single();
	}

	private UUID projetDuTour(UUID cercle, int rang) {
		return base.sql("SELECT projet_id FROM cercle_membre WHERE cercle_id = :c AND rang = :r").param("c", cercle).param("r", rang)
			.query(UUID.class).single();
	}

	@Test
	void le_cercle_de_dakar_a_un_tour_livre_et_un_tour_en_cours() {
		var reponse = mvc.get().uri("/api/cercles/" + DonneesDemo.DAKAR).with(oauth2Login().oauth2User(connecte(DonneesDemo.AWA)));

		assertThat(reponse).hasStatusOk();
		assertThat(reponse).bodyJson().extractingPath("$.statut").isEqualTo("en_cours");
		assertThat(reponse).bodyJson().extractingPath("$.tours.length()").isEqualTo(6);
		assertThat(reponse).bodyJson().extractingPath("$.tours[0].livreLe").isNotNull();
		assertThat(reponse).bodyJson().extractingPath("$.tours[1].projetId").isNotNull();
		assertThat(reponse).bodyJson().extractingPath("$.tours[1].livreLe").isNull();
		assertThat(reponse).bodyJson().extractingPath("$.tours[2].projetId").isNull();

		var livre = mvc.get().uri("/api/projets/" + projetDuTour(DonneesDemo.DAKAR, 1))
			.with(oauth2Login().oauth2User(connecte(DonneesDemo.KOFI)));
		assertThat(livre).bodyJson().extractingPath("$.statut").isEqualTo("livre");
		assertThat(livre).bodyJson().extractingPath("$.origine").isEqualTo("cercle");
		assertThat(livre).bodyJson().extractingPath("$.porteur.nom").isEqualTo("Awa Diop");
		assertThat(mvc.get().uri("/api/projets/" + projetDuTour(DonneesDemo.DAKAR, 2))
			.with(oauth2Login().oauth2User(connecte(DonneesDemo.KOFI)))).bodyJson().extractingPath("$.statut").isEqualTo("en_cours");
	}

	@Test
	void le_tour_de_kofi_avance_avec_7_taches_livrees_sur_12() {
		String projet = "/api/projets/" + projetDuTour(DonneesDemo.DAKAR, 2);
		var taches = mvc.get().uri(projet + "/taches?limite=100").with(oauth2Login().oauth2User(connecte(DonneesDemo.AWA)));

		assertThat(taches).hasStatusOk();
		assertThat(taches).bodyJson().extractingPath("$.elements.length()").isEqualTo(12);
		assertThat(taches).bodyJson().extractingPath("$.elements[?(@.statut == 'livree')]").asArray().hasSize(7);
		assertThat(taches).bodyJson().extractingPath("$.elements[?(@.statut == 'en_cours')]").asArray().hasSize(2);
		assertThat(taches).bodyJson().extractingPath("$.elements[0].titre").isEqualTo("Rencontrer la coopérative et noter ses besoins");
		assertThat(taches).bodyJson().extractingPath("$.elements[0].assignee.nom").isEqualTo("Kofi Mensah");
		assertThat(taches).bodyJson().extractingPath("$.curseurSuivant").isNull();

		var jalons = mvc.get().uri(projet + "/jalons").with(oauth2Login().oauth2User(connecte(DonneesDemo.KOFI)));
		assertThat(jalons).bodyJson().extractingPath("$.length()").isEqualTo(3);
		assertThat(jalons).bodyJson().extractingPath("$[0].statut").isEqualTo("atteint");
		assertThat(jalons).bodyJson().extractingPath("$[1].statut").isEqualTo("prevu");

		// La dernière livraison date d'il y a 2 heures : le projet est actif.
		assertThat(compter("SELECT count(*) FROM projet WHERE id = '" + projetDuTour(DonneesDemo.DAKAR, 2)
				+ "' AND derniere_activite_le > now() - interval '3 hours'")).isEqualTo(1);
	}

	@Test
	void le_tour_livre_d_awa_a_toutes_ses_taches_livrees() {
		UUID projetId = projetDuTour(DonneesDemo.DAKAR, 1);
		String projet = "/api/projets/" + projetId;
		var livrees = mvc.get().uri(projet + "/taches?limite=100&statut=livree").with(oauth2Login().oauth2User(connecte(DonneesDemo.AWA)));

		assertThat(livrees).bodyJson().extractingPath("$.elements.length()").isEqualTo(9);
		assertThat(compter("SELECT count(*) FROM tache WHERE projet_id = '" + projetId + "'")).isEqualTo(9);
		assertThat(mvc.get().uri(projet + "/jalons").with(oauth2Login().oauth2User(connecte(DonneesDemo.AWA))))
			.bodyJson().extractingPath("$[?(@.statut == 'atteint')]").asArray().hasSize(3);
		// Toutes les livraisons tombent pendant le tour, au plus tard quand le projet est livré.
		assertThat(compter("SELECT count(*) FROM tache t JOIN projet p ON p.id = t.projet_id WHERE t.projet_id = '" + projetId
				+ "' AND (t.livree_le <= t.cree_le OR t.livree_le > p.derniere_activite_le)")).isZero();
	}

	@Test
	void les_deux_autres_cercles_un_ouvert_que_l_on_peut_rejoindre_et_un_termine() {
		assertThat(mvc.get().uri("/api/cercles/" + DonneesDemo.ABIDJAN).with(oauth2Login().oauth2User(connecte(DonneesDemo.AWA))))
			.bodyJson().extractingPath("$.statut").isEqualTo("ouvert");
		assertThat(compter("SELECT count(*) FROM cercle_membre WHERE cercle_id = '" + DonneesDemo.ABIDJAN + "'")).isEqualTo(3);
		assertThat(compter("SELECT count(*) FROM cercle WHERE id = '" + DonneesDemo.NAIROBI + "' AND statut = 'termine'")).isEqualTo(1);
		assertThat(compter("""
				SELECT count(*) FROM projet p JOIN cercle_membre m ON m.projet_id = p.id
				WHERE m.cercle_id = '%s' AND p.statut = 'livre'
				""".formatted(DonneesDemo.NAIROBI))).isEqualTo(4);
	}

	@Test
	void recharger_ne_cree_aucun_doublon() {
		long utilisateurs = compter("SELECT count(*) FROM utilisateur");
		long cercles = compter("SELECT count(*) FROM cercle");
		long membresCercles = compter("SELECT count(*) FROM cercle_membre");
		long projets = compter("SELECT count(*) FROM projet");
		long taches = compter("SELECT count(*) FROM tache");
		long messages = compter("SELECT count(*) FROM message_salon");
		long publications = compter("SELECT count(*) FROM publication");
		long propositions = compter("SELECT count(*) FROM proposition");

		assertThat(demo.charger(Instant.now())).isFalse();

		assertThat(compter("SELECT count(*) FROM utilisateur")).isEqualTo(utilisateurs);
		assertThat(compter("SELECT count(*) FROM cercle")).isEqualTo(cercles);
		assertThat(compter("SELECT count(*) FROM cercle_membre")).isEqualTo(membresCercles);
		assertThat(compter("SELECT count(*) FROM projet")).isEqualTo(projets);
		assertThat(compter("SELECT count(*) FROM tache")).isEqualTo(taches);
		assertThat(compter("SELECT count(*) FROM message_salon")).as("les communautés se complètent sans doublon").isEqualTo(messages);
		assertThat(compter("SELECT count(*) FROM publication")).isEqualTo(publications);
		assertThat(compter("SELECT count(*) FROM proposition")).as("les annonces se complètent sans doublon").isEqualTo(propositions);
	}

	@Test
	void les_communautes_ont_des_membres_des_salons_vivants_et_des_questions_a_chaque_etape() {
		var awa = oauth2Login().oauth2User(connecte(DonneesDemo.AWA));

		var flutter = mvc.get().uri("/api/communautes/flutter").with(awa);
		assertThat(flutter).bodyJson().extractingPath("$.membre").isEqualTo(true);
		assertThat(flutter).bodyJson().extractingPath("$.nombreMembres").asNumber().satisfies(n -> assertThat(n.intValue()).isGreaterThanOrEqualTo(5));

		var general = mvc.get().uri("/api/communautes/flutter/salons/general/messages").with(awa);
		assertThat(general).bodyJson().extractingPath("$.elements.length()").isEqualTo(12);
		assertThat(general).bodyJson().extractingPath("$.elements[0].auteur.nom").isEqualTo("Yann Kouassi");

		assertThat(compter("""
				SELECT count(*) FROM publication p JOIN reponse r ON r.id = p.reponse_acceptee_id
				WHERE p.id = '%s' AND r.auteur_id = '%s'
				""".formatted(DonneesDemo.id("publication:webhook"), DonneesDemo.KOFI.id()))).as("réponse humaine acceptée").isEqualTo(1);
		assertThat(compter("""
				SELECT count(*) FROM publication p JOIN reponse r ON r.id = p.reponse_acceptee_id
				WHERE p.id = '%s' AND r.proposee_par_ia AND r.validee_par_id IS NOT NULL
				""".formatted(DonneesDemo.id("publication:apk")))).as("réponse de l'IA validée par un pair").isEqualTo(1);
		assertThat(compter("SELECT count(*) FROM publication WHERE id = '" + DonneesDemo.id("publication:ussd") + "' AND reponse_acceptee_id IS NULL"))
			.as("une question encore ouverte").isEqualTo(1);
	}

	@Test
	void toutes_les_communautes_de_depart_ont_des_membres_et_leurs_trois_salons_des_messages() {
		assertThat(compter("SELECT count(*) FROM communaute c WHERE NOT EXISTS (SELECT 1 FROM adhesion a WHERE a.communaute_id = c.id)"))
			.as("aucune communauté de départ sans membre").isZero();
		assertThat(compter("""
				SELECT count(*) FROM salon s JOIN communaute c ON c.id = s.communaute_id
				WHERE NOT EXISTS (SELECT 1 FROM message_salon m WHERE m.salon_id = s.id)
				""")).as("aucun salon vide").isZero();
		assertThat(compter("SELECT count(*) FROM publication WHERE type = 'montre_ton_projet' AND projet_id IS NOT NULL"))
			.as("des projets montrés, avec un vrai projet").isGreaterThanOrEqualTo(3);
	}

	@Test
	void le_hackathon_de_dakar_est_termine_et_ses_resultats_sont_publics() {
		var reponse = mvc.get().uri("/api/hackathons/" + DemoHackathonMission.HACKATHON + "/resultats");

		assertThat(reponse).hasStatusOk();
		assertThat(reponse).bodyJson().extractingPath("$.gagnants[0].prix.nom").isEqualTo("Prix du jury");
		assertThat(reponse).bodyJson().extractingPath("$.gagnants[0].equipe.nom").isEqualTo("Les Baobabs");
		assertThat(reponse).bodyJson().extractingPath("$.classement[0].rendu.titre").isEqualTo("Stock au marché");
		assertThat(reponse).bodyJson().extractingPath("$.classement[1].retourDuJury.length()").isEqualTo(1);
	}

	@Test
	void la_mission_des_tisserandes_attend_qu_awa_accepte_le_deuxieme_jalon() {
		var reponse = mvc.get().uri("/api/missions/" + DemoHackathonMission.MISSION).with(oauth2Login().oauth2User(connecte(DonneesDemo.AWA)));

		assertThat(reponse).hasStatusOk();
		assertThat(reponse).bodyJson().extractingPath("$.statut").isEqualTo("en_cours");
		assertThat(reponse).bodyJson().extractingPath("$.travail.developpeur.nom").isEqualTo("Kofi Mensah");
		assertThat(reponse).bodyJson().extractingPath("$.travail.devis.jalons[0].statut").isEqualTo("accepte");
		assertThat(reponse).bodyJson().extractingPath("$.travail.devis.jalons[1].statut").isEqualTo("livre");
		assertThat(reponse).bodyJson().extractingPath("$.travail.devis.jalons[2].statut").isEqualTo("en_cours");
	}

	@Test
	void les_listes_publiques_montrent_des_missions_ouvertes_et_un_hackathon_ou_s_inscrire() {
		var missions = mvc.get().uri("/api/missions");
		assertThat(missions).hasStatusOk();
		assertThat(missions).bodyJson().extractingPath("$.elements.length()").asNumber()
			.satisfies(n -> assertThat(n.intValue()).isGreaterThanOrEqualTo(3));
		assertThat(compter("SELECT count(*) FROM proposition WHERE mission_id = '" + DonneesDemo.id("mission:rendez-vous-sante") + "'"))
			.as("des propositions à comparer").isEqualTo(2);

		var hackathon = mvc.get().uri("/api/hackathons/" + DemoAnnonces.HACKATHON_OUVERT);
		assertThat(hackathon).hasStatusOk();
		assertThat(hackathon).bodyJson().extractingPath("$.statut").isEqualTo("publie");
	}

	@Test
	void le_catalogue_ajoute_douze_evenements_avec_des_equipes_et_les_quatre_phases() {
		assertThat(mvc.get().uri("/api/hackathons?limite=100")).hasStatusOk().bodyJson()
			.extractingPath("$.elements.length()").asNumber().satisfies(n -> assertThat(n.intValue()).isGreaterThanOrEqualTo(14));
		for (int i = 1; i <= 12; i++) {
			var reponse = mvc.get().uri("/api/hackathons/" + DemoDecouverteHackathons.identifiant(i));
			assertThat(reponse).hasStatusOk().bodyJson().extractingPath("$.titre").asString().contains("· Démo");
			assertThat(reponse).bodyJson().extractingPath("$.nombreInscrits").isEqualTo(4);
		}
		for (var phase : java.util.Map.of(1, "publie", 9, "en_cours", 11, "evaluation", 12, "resultats").entrySet()) {
			assertThat(mvc.get().uri("/api/hackathons/" + DemoDecouverteHackathons.identifiant(phase.getKey())))
				.bodyJson().extractingPath("$.statut").isEqualTo(phase.getValue());
		}
		assertThat(compter("SELECT count(DISTINCT lieu) FROM hackathon WHERE id::text LIKE 'e2026115-%'")).isEqualTo(3);
	}

	@Test
	void les_resultats_fictifs_de_thies_ont_deux_projets_et_un_prix_publics() {
		var reponse = mvc.get().uri("/api/hackathons/" + DemoDecouverteHackathons.identifiant(12) + "/resultats");
		assertThat(reponse).hasStatusOk().bodyJson().extractingPath("$.classement.length()").isEqualTo(2);
		assertThat(reponse).bodyJson().extractingPath("$.classement[0].rendu.titre").isEqualTo("Ateliers ouverts");
		assertThat(reponse).bodyJson().extractingPath("$.gagnants[0].prix.nom").isEqualTo("Prix du jury · Démo");
	}

	@Test
	@Transactional
	void un_redemarrage_complete_une_base_existante_sans_ecraser_ses_modifications() {
		UUID conserve = DemoDecouverteHackathons.identifiant(1);
		UUID manque = DemoDecouverteHackathons.identifiant(5);
		base.sql("UPDATE hackathon SET titre = 'Titre modifié à conserver' WHERE id = :id").param("id", conserve).update();
		base.sql("DELETE FROM inscription WHERE hackathon_id = :h AND utilisateur_id = :u").param("h", conserve).param("u", DonneesDemo.FATOU.id()).update();
		base.sql("DELETE FROM hackathon WHERE id = :id").param("id", manque).update();
		assertThat(demo.charger(Instant.now().plusSeconds(86400))).isFalse();
		assertThat(compter("SELECT count(*) FROM hackathon WHERE id::text LIKE 'e2026115-%'")).isEqualTo(12);
		assertThat(base.sql("SELECT titre FROM hackathon WHERE id = :id").param("id", conserve).query(String.class).single()).isEqualTo("Titre modifié à conserver");
		assertThat(base.sql("SELECT count(*) FROM inscription WHERE hackathon_id = :id").param("id", conserve).query(Long.class).single()).isEqualTo(3);
	}

	@Test
	void chaque_personne_de_demo_a_un_profil_publie_avec_ses_competences() {
		assertThat(compter("SELECT count(*) FROM utilisateur u JOIN profil p ON p.utilisateur_id = u.id"
				+ " WHERE u.email LIKE '%@demo.jamii.test' AND p.publie")).isEqualTo(18);
		var profil = mvc.get().uri("/api/profils/" + DonneesDemo.KOFI.id());
		assertThat(profil).hasStatusOk();
		assertThat(profil).bodyJson().extractingPath("$.nom").isEqualTo("Kofi Mensah");
		assertThat(profil).bodyJson().extractingPath("$.ville").isEqualTo("Accra");
	}

	@Test
	void le_salon_du_cercle_de_dakar_raconte_son_histoire_et_vit() {
		var salon = mvc.get().uri("/api/cercles/" + DonneesDemo.DAKAR + "/salon?limite=100").with(oauth2Login().oauth2User(connecte(DonneesDemo.AWA)));
		assertThat(salon).hasStatusOk();
		assertThat(salon).bodyJson().extractingPath("$.elements[0].evenement.code").isEqualTo("cercle_cree");
		assertThat(salon).bodyJson().extractingPath("$.elements[*].type").asArray().contains("evenement", "texte", "point", "coup_de_main");
		assertThat(salon).bodyJson().extractingPath("$.coupsDeMain.length()").isEqualTo(1);
		assertThat(salon).bodyJson().extractingPath("$.coupsDeMain[0].auteur.nom").isEqualTo("Moussa Traoré");
		long evenements = compter("SELECT count(*) FROM message_cercle WHERE cercle_id = '" + DonneesDemo.DAKAR + "' AND type = 'evenement'");
		new DemoSalonsCercles(base, java.time.Instant.now()).charger();
		assertThat(compter("SELECT count(*) FROM message_cercle WHERE cercle_id = '" + DonneesDemo.DAKAR + "' AND type = 'evenement'"))
			.as("recharger n'ajoute pas l'histoire une 2e fois").isEqualTo(evenements);
	}

	@Test
	void aucun_compte_de_demo_n_a_de_login_github_ni_d_adresse_joignable() {
		assertThat(compter("SELECT count(*) FROM utilisateur WHERE email LIKE '%@demo.jamii.test'")).as("les 6 de la démo et les 12 des communautés").isEqualTo(18);
		assertThat(compter("SELECT count(*) FROM utilisateur WHERE email LIKE '%@demo.jamii.test' AND login_github IS NOT NULL")).isZero();
	}

	@Test
	void les_nouveaux_cercles_sont_lisibles_par_la_vraie_api_et_ont_des_places_libres() {
		var cercles = mvc.get().uri("/api/cercles?portee=ouverts&limite=100")
			.with(oauth2Login().oauth2User(connecte(DonneesDemo.AWA)));
		assertThat(cercles).hasStatusOk();
		assertThat(cercles).bodyJson().extractingPath("$.elements[*].nom").asArray()
			.contains("Sahel Mobile", "Lagos Builders", "Les Makers de Thiès", "Antananarivo Studio");
		assertThat(cercles).bodyJson().extractingPath("$.elements.length()").isEqualTo(22);
		UUID sahel = DonneesDemo.id("cercle:decouverte:sahel-mobile");
		var detail = mvc.get().uri("/api/cercles/" + sahel)
			.with(oauth2Login().oauth2User(connecte(DonneesDemo.AWA)));
		assertThat(detail).bodyJson().extractingPath("$.ville").isEqualTo("Bamako");
		assertThat(detail).bodyJson().extractingPath("$.nombreMembres").isEqualTo(1);
		assertThat(detail).bodyJson().extractingPath("$.taille").isEqualTo(4);
		assertThat(detail).bodyJson().extractingPath("$.moi.estMembre").isEqualTo(false);
		assertThat(compter("SELECT count(*) FROM cercle")).isEqualTo(24);
	}

	@Test
	@Transactional
	void une_base_de_demo_deja_chargee_recoit_les_nouveaux_cercles() {
		UUID sahel = DonneesDemo.id("cercle:decouverte:sahel-mobile");
		base.sql("DELETE FROM cercle WHERE id = :id").param("id", sahel).update();
		assertThat(compter("SELECT count(*) FROM cercle")).isEqualTo(23);
		assertThat(demo.charger(Instant.now())).as("les anciennes données étaient déjà chargées").isFalse();
		assertThat(compter("SELECT count(*) FROM cercle")).isEqualTo(24);
		assertThat(compter("SELECT count(*) FROM cercle_membre WHERE cercle_id = '" + sahel + "'")).isEqualTo(1);
		assertThat(compter("SELECT count(*) FROM message_cercle WHERE cercle_id = '" + sahel + "'"))
			.as("le salon du cercle raconte sa création").isPositive();
	}

	@Test
	@Transactional
	void recharger_preserve_les_modifications_des_visiteurs() {
		UUID douala = DonneesDemo.id("cercle:decouverte:douala-numerique");
		base.sql("UPDATE cercle SET nom = 'Douala, notre équipe' WHERE id = :id").param("id", douala).update();
		base.sql("DELETE FROM cercle_membre WHERE cercle_id = :c AND utilisateur_id <> (SELECT createur_id FROM cercle WHERE id = :c)")
			.param("c", douala).update();
		demo.charger(Instant.now());
		assertThat(base.sql("SELECT nom FROM cercle WHERE id = :id").param("id", douala).query(String.class).single())
			.isEqualTo("Douala, notre équipe");
		assertThat(compter("SELECT count(*) FROM cercle_membre WHERE cercle_id = '" + douala + "'"))
			.as("les membres retirés ne sont pas réinsérés").isEqualTo(1);
	}

}
