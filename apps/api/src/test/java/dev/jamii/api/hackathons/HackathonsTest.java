package dev.jamii.api.hackathons;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import dev.jamii.api.TestcontainersConfiguration;
import dev.jamii.api.auth.Role;
import dev.jamii.api.auth.UtilisateurConnecte;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Créer, modifier, publier, annuler et lire un hackathon (contracts/hackathons.yaml, tag « hackathons »), sur un vrai PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class HackathonsTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient base;

	@Autowired
	JsonMapper json;

	// ---------------------------------------------------------------- aides

	private UtilisateurConnecte utilisateur(String nom) {
		UUID id = UUID.randomUUID();
		Timestamp t = Timestamp.from(Instant.now());
		base.sql("INSERT INTO utilisateur (id, nom, email, cree_le, mis_a_jour_le) VALUES (:id, :nom, :email, :t, :t)")
			.param("id", id).param("nom", nom).param("email", nom + "." + id + "@secret.example").param("t", t).update();
		return new UtilisateurConnecte(id, Set.of(Role.MEMBRE));
	}

	private static String date(long jours) {
		return Instant.now().plus(jours, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS).toString();
	}

	/** Un hackathon valide dont le début est dans `debutDans` jours (les inscriptions ferment la veille). */
	private static String corps(long debutDans) {
		return """
				{"titre":"Dakar Cloud Hack","theme":"Des services utiles aux commerçants","description":"Deux jours pour construire un service qui aide un commerçant.",
				 "lieu":"hybride","ville":"Dakar",
				 "calendrier":{"inscriptionsOuvertesLe":"%s","inscriptionsFermeesLe":"%s","debutLe":"%s","finRendusLe":"%s","resultatsLe":"%s"},
				 "tailleEquipe":{"min":2,"max":5},"modeInscription":"ouvert","deploiementObligatoire":true,
				 "codeDeConduite":"Respect de chacun, aucun harcèlement, le code de chaque équipe reste le sien.",
				 "questions":["Quel problème résolvez-vous ?"],
				 "criteres":[{"nom":"Utilité","poids":60},{"nom":"Qualité technique","poids":40}],
				 "prix":[{"nom":"Prix du jury","montant":{"centimes":1000000,"devise":"XOF"}},{"nom":"Meilleur projet sur DataCloud","sponsor":"Systalink"}]}
				""".formatted(date(-1), date(debutDans - 1), date(debutDans), date(debutDans + 2), date(debutDans + 4));
	}

	private MvcTestResult creer(String corps, UtilisateurConnecte qui) {
		return mvc.post().uri("/api/hackathons").contentType(MediaType.APPLICATION_JSON).content(corps)
			.with(csrf()).with(oauth2Login().oauth2User(qui)).exchange();
	}

	private MvcTestResult action(String chemin, String corps, UtilisateurConnecte qui) {
		return mvc.post().uri(chemin).contentType(MediaType.APPLICATION_JSON).content(corps)
			.with(csrf()).with(oauth2Login().oauth2User(qui)).exchange();
	}

	private MvcTestResult modifier(String id, String corps, UtilisateurConnecte qui) {
		return mvc.patch().uri("/api/hackathons/" + id).contentType(new MediaType("application", "merge-patch+json")).content(corps)
			.with(csrf()).with(oauth2Login().oauth2User(qui)).exchange();
	}

	private MvcTestResult lire(String uri, UtilisateurConnecte qui) {
		return qui == null ? mvc.get().uri(uri).exchange() : mvc.get().uri(uri).with(oauth2Login().oauth2User(qui)).exchange();
	}

	private JsonNode corpsDe(MvcTestResult r) throws Exception {
		return json.readTree(r.getResponse().getContentAsString());
	}

	private JsonNode brouillon(UtilisateurConnecte orga, long debutDans) throws Exception {
		MvcTestResult r = creer(corps(debutDans), orga);
		assertThat(r).hasStatus(HttpStatus.CREATED);
		return corpsDe(r);
	}

	private JsonNode publie(UtilisateurConnecte orga, long debutDans) throws Exception {
		JsonNode h = brouillon(orga, debutDans);
		MvcTestResult r = action("/api/hackathons/" + h.get("id").asString() + "/publier", "{\"version\":" + h.get("version") + "}", orga);
		assertThat(r).hasStatus(HttpStatus.OK);
		return corpsDe(r);
	}

	// ---------------------------------------------------------------- la discussion

	@Test
	void l_organisateur_annonce_et_lie_une_communaute_que_tous_voient() throws Exception {
		UtilisateurConnecte awa = utilisateur("awa");
		UtilisateurConnecte kofi = utilisateur("kofi");
		JsonNode brouillon = brouillon(awa, 10);
		String b = brouillon.get("id").asString();
		// Un brouillon reste caché, discussion comprise.
		assertThat(lire("/api/hackathons/" + b + "/discussion", kofi)).hasStatus(HttpStatus.NOT_FOUND);

		String id = publie(awa, 10).get("id").asString();
		String annonces = "/api/hackathons/" + id + "/annonces";
		assertThat(action(annonces, "{\"texte\":\"Je veux annoncer\"}", kofi)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(action(annonces, "{\"texte\":\"   \"}", awa)).hasStatus(HttpStatus.BAD_REQUEST);
		MvcTestResult cree = action(annonces, "{\"texte\":\"Le dépôt des rendus ouvre demain à 9 h.\"}", awa);
		assertThat(cree).hasStatus(HttpStatus.CREATED);
		assertThat(corpsDe(cree).at("/auteur/nom").asString()).isEqualTo("awa");

		MvcTestResult lien = mvc.put().uri("/api/hackathons/" + id + "/communaute").contentType(MediaType.APPLICATION_JSON)
			.content("{\"communaute\":\"dakar-cloud-hack\"}").with(csrf()).with(oauth2Login().oauth2User(awa)).exchange();
		assertThat(lien).hasStatus(HttpStatus.NO_CONTENT);
		MvcTestResult mauvais = mvc.put().uri("/api/hackathons/" + id + "/communaute").contentType(MediaType.APPLICATION_JSON)
			.content("{\"communaute\":\"../admin\"}").with(csrf()).with(oauth2Login().oauth2User(awa)).exchange();
		assertThat(mauvais).hasStatus(HttpStatus.BAD_REQUEST);
		MvcTestResult intrus = mvc.put().uri("/api/hackathons/" + id + "/communaute").contentType(MediaType.APPLICATION_JSON)
			.content("{\"communaute\":\"autre\"}").with(csrf()).with(oauth2Login().oauth2User(kofi)).exchange();
		assertThat(intrus).hasStatus(HttpStatus.FORBIDDEN);

		// Un visiteur sans connexion lit les annonces et le lien de la communauté.
		JsonNode vue = corpsDe(lire("/api/hackathons/" + id + "/discussion", null));
		assertThat(vue.get("communaute").asString()).isEqualTo("dakar-cloud-hack");
		assertThat(vue.get("annonces")).hasSize(1);
		assertThat(vue.at("/annonces/0/texte").asString()).isEqualTo("Le dépôt des rendus ouvre demain à 9 h.");
		assertThat(vue.toString()).doesNotContain("@secret.example");

		String annonce = corpsDe(cree).get("id").asString();
		MvcTestResult retiree = mvc.delete().uri(annonces + "/" + annonce).with(csrf()).with(oauth2Login().oauth2User(awa)).exchange();
		assertThat(retiree).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(corpsDe(lire("/api/hackathons/" + id + "/discussion", kofi)).get("annonces")).isEmpty();
	}

	// ---------------------------------------------------------------- créer et publier

	@Test
	void un_brouillon_n_est_visible_que_de_son_organisateur() throws Exception {
		UtilisateurConnecte awa = utilisateur("awa");
		MvcTestResult cree = creer(corps(10), awa);
		assertThat(cree).hasStatus(HttpStatus.CREATED);
		JsonNode h = corpsDe(cree);
		String id = h.get("id").asString();
		assertThat(cree.getResponse().getHeader("Location")).isEqualTo("/api/hackathons/" + id);
		assertThat(h.get("statut").asString()).isEqualTo("brouillon");
		assertThat(h.at("/organisateur/nom").asString()).isEqualTo("awa");
		assertThat(h.get("criteres")).hasSize(2);
		assertThat(h.at("/criteres/0/id").isNull()).isFalse();
		assertThat(h.at("/prix/1/sponsor").asString()).isEqualTo("Systalink");
		assertThat(h.at("/moi/role").asString()).isEqualTo("organisateur");
		assertThat(h.toString()).doesNotContain("@secret.example");

		assertThat(lire("/api/hackathons/" + id, awa)).hasStatus(HttpStatus.OK);
		assertThat(lire("/api/hackathons/" + id, utilisateur("kofi"))).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(lire("/api/hackathons/" + id, null)).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void publier_rend_le_hackathon_public_sans_connexion() throws Exception {
		JsonNode h = publie(utilisateur("awa"), 10);
		assertThat(h.get("statut").asString()).isEqualTo("publie");

		MvcTestResult anonyme = lire("/api/hackathons/" + h.get("id").asString(), null);
		assertThat(anonyme).hasStatus(HttpStatus.OK);
		JsonNode vue = corpsDe(anonyme);
		assertThat(vue.at("/moi/role").asString()).isEqualTo("visiteur");
		assertThat(vue.at("/moi/actions")).isEmpty();
	}

	@Test
	void un_visiteur_connecte_peut_s_inscrire_tant_que_les_inscriptions_sont_ouvertes() throws Exception {
		JsonNode h = publie(utilisateur("awa"), 10);
		JsonNode vue = corpsDe(lire("/api/hackathons/" + h.get("id").asString(), utilisateur("kofi")));
		assertThat(vue.at("/moi/actions").toString()).contains("sinscrire");
	}

	@Test
	void le_statut_suit_le_calendrier() throws Exception {
		UtilisateurConnecte awa = utilisateur("awa");
		String aVenir = publie(awa, 10).get("id").asString();
		String enCours = publie(awa, 10).get("id").asString();
		String enEvaluation = publie(awa, 10).get("id").asString();
		// On recule le calendrier : le même hackathon change de statut sans qu'aucune tâche ne tourne.
		recaler(enCours, -3, 1);
		recaler(enEvaluation, -10, -5);

		assertThat(corpsDe(lire("/api/hackathons/" + aVenir, null)).get("statut").asString()).isEqualTo("publie");
		assertThat(corpsDe(lire("/api/hackathons/" + enCours, null)).get("statut").asString()).isEqualTo("en_cours");
		assertThat(corpsDe(lire("/api/hackathons/" + enEvaluation, null)).get("statut").asString()).isEqualTo("evaluation");
	}

	/** Décale un hackathon : début `debut` jours et fin des rendus `fin` jours à partir de maintenant. */
	private void recaler(String id, long debut, long fin) {
		Instant maintenant = Instant.now();
		base.sql("""
				UPDATE hackathon SET inscriptions_ouvertes_le = :o, inscriptions_fermees_le = :f, debut_le = :d, fin_rendus_le = :fr,
				  resultats_le = :r WHERE id = :id
				""")
			.param("o", Timestamp.from(maintenant.plus(debut - 5, ChronoUnit.DAYS))).param("f", Timestamp.from(maintenant.plus(debut - 1, ChronoUnit.DAYS)))
			.param("d", Timestamp.from(maintenant.plus(debut, ChronoUnit.DAYS))).param("fr", Timestamp.from(maintenant.plus(fin, ChronoUnit.DAYS)))
			.param("r", Timestamp.from(maintenant.plus(fin + 2, ChronoUnit.DAYS))).param("id", UUID.fromString(id)).update();
	}

	// ---------------------------------------------------------------- validation

	@Test
	void un_calendrier_dans_le_desordre_est_refuse() throws Exception {
		UtilisateurConnecte awa = utilisateur("awa");
		String desordre = corps(10).replaceFirst("\"finRendusLe\":\"[^\"]*\"", "\"finRendusLe\":\"" + date(2) + "\"");
		MvcTestResult r = creer(desordre, awa);
		assertThat(r).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(corpsDe(r).get("erreurs").toString()).contains("calendrier");
	}

	@Test
	void tous_les_champs_en_erreur_sont_signales_ensemble() throws Exception {
		MvcTestResult r = creer("""
				{"titre":"a","theme":"x","description":"court","lieu":"lune","criteres":[],"modeInscription":"secret",
				 "calendrier":{"debutLe":"pas une date"},"tailleEquipe":{"min":5,"max":2}}
				""", utilisateur("awa"));
		assertThat(r).hasStatus(HttpStatus.BAD_REQUEST);
		String erreurs = corpsDe(r).get("erreurs").toString();
		assertThat(erreurs).contains("titre", "description", "lieu", "criteres", "modeInscription", "calendrier.debutLe", "tailleEquipe");
	}

	@Test
	void un_critere_ou_un_prix_invalide_est_signale_avec_son_rang() throws Exception {
		String mauvais = corps(10).replace("{\"nom\":\"Utilité\",\"poids\":60}", "{\"nom\":\"U\",\"poids\":500}")
			.replace("\"devise\":\"XOF\"", "\"devise\":\"xof\"");
		MvcTestResult r = creer(mauvais, utilisateur("awa"));
		assertThat(r).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(corpsDe(r).get("erreurs").toString()).contains("criteres[0].nom", "criteres[0].poids", "prix[0].montant.devise");
	}

	@Test
	void publier_sans_code_de_conduite_est_refuse() throws Exception {
		UtilisateurConnecte awa = utilisateur("awa");
		JsonNode h = corpsDe(creer(corps(10).replaceFirst("\"codeDeConduite\":\"[^\"]*\",", ""), awa));
		MvcTestResult r = action("/api/hackathons/" + h.get("id").asString() + "/publier", "{\"version\":1}", awa);
		assertThat(r).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(corpsDe(r).get("erreurs").toString()).contains("codeDeConduite");
	}

	@Test
	void on_ne_publie_pas_un_hackathon_deja_termine() throws Exception {
		UtilisateurConnecte awa = utilisateur("awa");
		JsonNode h = brouillon(awa, 10);
		recaler(h.get("id").asString(), -20, -10);
		MvcTestResult r = action("/api/hackathons/" + h.get("id").asString() + "/publier", "{\"version\":1}", awa);
		assertThat(r).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(corpsDe(r).get("erreurs").toString()).contains("calendrier");
	}

	// ---------------------------------------------------------------- droits et concurrence

	@Test
	void seul_l_organisateur_publie_et_les_autres_ne_voient_pas_un_brouillon() throws Exception {
		UtilisateurConnecte awa = utilisateur("awa");
		JsonNode brouillon = brouillon(awa, 10);
		assertThat(action("/api/hackathons/" + brouillon.get("id").asString() + "/publier", "{\"version\":1}", utilisateur("kofi")))
			.as("un brouillon n'existe pas pour un autre").hasStatus(HttpStatus.NOT_FOUND);

		JsonNode publie = publie(awa, 10);
		assertThat(action("/api/hackathons/" + publie.get("id").asString() + "/annuler", "{\"version\":2,\"raison\":\"Une raison suffisante\"}", utilisateur("kofi")))
			.hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void publier_deux_fois_ou_avec_une_version_perimee_est_refuse() throws Exception {
		UtilisateurConnecte awa = utilisateur("awa");
		JsonNode h = brouillon(awa, 10);
		String chemin = "/api/hackathons/" + h.get("id").asString() + "/publier";

		MvcTestResult perimee = action(chemin, "{\"version\":9}", awa);
		assertThat(perimee).hasStatus(HttpStatus.CONFLICT);
		assertThat(corpsDe(perimee).get("code").asString()).isEqualTo("modification_concurrente");

		JsonNode publie = corpsDe(action(chemin, "{\"version\":1}", awa));
		MvcTestResult encore = action(chemin, "{\"version\":" + publie.get("version") + "}", awa);
		assertThat(encore).hasStatus(HttpStatus.CONFLICT);
		assertThat(corpsDe(encore).get("code").asString()).isEqualTo("etat_invalide");
	}

	@Test
	void plus_de_trois_publications_par_jour_repondent_429_sans_rien_publier() throws Exception {
		UtilisateurConnecte awa = utilisateur("awa");
		for (int i = 0; i < 3; i++) {
			publie(awa, 10);
		}
		JsonNode quatrieme = brouillon(awa, 10);
		MvcTestResult r = action("/api/hackathons/" + quatrieme.get("id").asString() + "/publier", "{\"version\":1}", awa);
		assertThat(r).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(Long.parseLong(r.getResponse().getHeader("Retry-After"))).isBetween(1L, 86_400L);
		assertThat(corpsDe(r).get("code").asString()).isEqualTo("limite_atteinte");
		assertThat(corpsDe(lire("/api/hackathons/" + quatrieme.get("id").asString(), awa)).get("statut").asString()).isEqualTo("brouillon");
	}

	// ---------------------------------------------------------------- modifier et annuler

	@Test
	void modifier_change_seulement_les_champs_envoyes_et_incremente_la_version() throws Exception {
		UtilisateurConnecte awa = utilisateur("awa");
		JsonNode h = brouillon(awa, 10);
		MvcTestResult r = modifier(h.get("id").asString(), "{\"version\":1,\"titre\":\"Dakar Cloud Hack 2\",\"ville\":null}", awa);
		assertThat(r).hasStatus(HttpStatus.OK);
		JsonNode apres = corpsDe(r);
		assertThat(apres.get("titre").asString()).isEqualTo("Dakar Cloud Hack 2");
		assertThat(apres.get("ville").isNull()).isTrue();
		assertThat(apres.get("theme").asString()).isEqualTo("Des services utiles aux commerçants");
		assertThat(apres.get("version").asInt()).isEqualTo(2);
		assertThat(apres.get("criteres")).hasSize(2);

		assertThat(modifier(h.get("id").asString(), "{\"version\":1,\"titre\":\"Trop tard\"}", awa)).hasStatus(HttpStatus.CONFLICT);
		assertThat(modifier(h.get("id").asString(), "{\"version\":2,\"titre\":null}", awa)).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void une_fois_publie_les_criteres_ne_changent_plus() throws Exception {
		UtilisateurConnecte awa = utilisateur("awa");
		JsonNode h = publie(awa, 10);
		MvcTestResult r = modifier(h.get("id").asString(), "{\"version\":" + h.get("version") + ",\"criteres\":[{\"nom\":\"Autre\",\"poids\":100}]}", awa);
		assertThat(r).hasStatus(HttpStatus.CONFLICT);
		assertThat(corpsDe(r).get("code").asString()).isEqualTo("etat_invalide");
		assertThat(modifier(h.get("id").asString(), "{\"version\":" + h.get("version") + ",\"theme\":\"Un nouveau thème pour tous\"}", awa))
			.hasStatus(HttpStatus.OK);
	}

	@Test
	void un_hackathon_commence_ne_se_modifie_plus() throws Exception {
		UtilisateurConnecte awa = utilisateur("awa");
		JsonNode h = publie(awa, 10);
		recaler(h.get("id").asString(), -3, -1);
		MvcTestResult r = modifier(h.get("id").asString(), "{\"version\":" + h.get("version") + ",\"titre\":\"Tard\"}", awa);
		assertThat(r).hasStatus(HttpStatus.CONFLICT);
	}

	@Test
	void annuler_un_hackathon_publie() throws Exception {
		UtilisateurConnecte awa = utilisateur("awa");
		JsonNode h = publie(awa, 10);
		String chemin = "/api/hackathons/" + h.get("id").asString() + "/annuler";
		MvcTestResult courte = action(chemin, "{\"version\":" + h.get("version") + ",\"raison\":\"non\"}", awa);
		assertThat(courte).hasStatus(HttpStatus.BAD_REQUEST);

		MvcTestResult r = action(chemin, "{\"version\":" + h.get("version") + ",\"raison\":\"Le lieu n'est plus disponible\"}", awa);
		assertThat(r).hasStatus(HttpStatus.OK);
		assertThat(corpsDe(r).get("statut").asString()).isEqualTo("annule");
		assertThat(action(chemin, "{\"version\":" + corpsDe(r).get("version") + ",\"raison\":\"Une seconde raison\"}", awa)).hasStatus(HttpStatus.CONFLICT);
	}

	// ---------------------------------------------------------------- la liste

	@Test
	void la_liste_ne_montre_ni_les_brouillons_ni_les_hackathons_annules() throws Exception {
		UtilisateurConnecte awa = utilisateur("awa");
		String ouvert = publie(awa, 10).get("id").asString();
		String brouillon = brouillon(awa, 10).get("id").asString();
		UtilisateurConnecte yann = utilisateur("yann");
		JsonNode aAnnuler = publie(yann, 10);
		assertThat(action("/api/hackathons/" + aAnnuler.get("id").asString() + "/annuler",
				"{\"version\":" + aAnnuler.get("version") + ",\"raison\":\"Plus de salle disponible\"}", yann)).hasStatus(HttpStatus.OK);

		String elements = corpsDe(lire("/api/hackathons?limite=100", null)).get("elements").toString();
		assertThat(elements).contains(ouvert).doesNotContain(brouillon);
		assertThat(elements).doesNotContain(aAnnuler.get("id").asString());
	}

	@Test
	void les_miens_montrent_ce_que_j_organise_brouillons_compris_et_ce_ou_je_suis_inscrit() throws Exception {
		UtilisateurConnecte awa = utilisateur("awa");
		UtilisateurConnecte kofi = utilisateur("kofi");
		String ouvert = publie(awa, 10).get("id").asString();
		String brouillon = brouillon(awa, 10).get("id").asString();
		String autre = publie(utilisateur("yann"), 10).get("id").asString();
		assertThat(action("/api/hackathons/" + ouvert + "/inscriptions", "{}", kofi)).hasStatus(HttpStatus.CREATED);

		assertThat(corpsDe(lire("/api/hackathons?vue=miens", awa)).get("elements").toString())
			.contains(ouvert, brouillon).doesNotContain(autre);
		assertThat(corpsDe(lire("/api/hackathons?vue=miens", kofi)).get("elements").toString())
			.contains(ouvert).doesNotContain(brouillon).doesNotContain(autre);
		assertThat(lire("/api/hackathons?vue=miens", null)).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(lire("/api/hackathons?vue=autre", awa)).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void la_liste_se_filtre_par_statut_calcule_et_se_parcourt_par_curseur() throws Exception {
		UtilisateurConnecte awa = utilisateur("awa");
		String aVenir = publie(awa, 10).get("id").asString();
		String enCours = publie(awa, 10).get("id").asString();
		recaler(enCours, -3, 1);

		String publies = corpsDe(lire("/api/hackathons?statut=publie&limite=100", null)).get("elements").toString();
		assertThat(publies).contains(aVenir).doesNotContain(enCours);
		String encours = corpsDe(lire("/api/hackathons?statut=en_cours&limite=100", null)).get("elements").toString();
		assertThat(encours).contains(enCours).doesNotContain(aVenir);
		assertThat(lire("/api/hackathons?statut=inconnu", null)).hasStatus(HttpStatus.BAD_REQUEST);

		JsonNode premiere = corpsDe(lire("/api/hackathons?limite=1", null));
		assertThat(premiere.get("elements")).hasSize(1);
		assertThat(premiere.get("curseurSuivant").isNull()).isFalse();
		JsonNode seconde = corpsDe(lire("/api/hackathons?limite=1&curseur=" + premiere.get("curseurSuivant").asString(), null));
		assertThat(seconde.get("elements").get(0).get("id").asString()).isNotEqualTo(premiere.get("elements").get(0).get("id").asString());
	}

}
