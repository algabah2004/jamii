package dev.jamii.api.projets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
import tools.jackson.databind.json.JsonMapper;

/** Rôles recherchés et candidatures (contracts/projets.yaml), sur un vrai PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class RecrutementTest {

	private static final MediaType FUSION = new MediaType("application", "merge-patch+json");

	private static final String MESSAGE = "{\"message\":\"J'ai déjà fait trois applications Flutter pour des commerces.\"}";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient base;

	@Autowired
	Recrutement recrutement;

	@Autowired
	JsonMapper json;

	// ---------------------------------------------------------------- aides

	private UtilisateurConnecte utilisateur(String nom) {
		UUID id = UUID.randomUUID();
		Timestamp t = Timestamp.from(Instant.now());
		base.sql("INSERT INTO utilisateur (id, nom, cree_le, mis_a_jour_le) VALUES (:id, :nom, :t, :t)")
			.param("id", id).param("nom", nom).param("t", t).update();
		return new UtilisateurConnecte(id, Set.of(Role.MEMBRE));
	}

	private MvcTestResult ecrire(String uri, UtilisateurConnecte qui, String corps) {
		var requete = mvc.post().uri(uri);
		if (corps != null) {
			requete = requete.contentType(MediaType.APPLICATION_JSON).content(corps);
		}
		return requete.with(csrf()).with(oauth2Login().oauth2User(qui)).exchange();
	}

	private MvcTestResult modifier(String uri, UtilisateurConnecte qui, String corps) {
		return mvc.patch().uri(uri).contentType(FUSION).content(corps).with(csrf()).with(oauth2Login().oauth2User(qui)).exchange();
	}

	private MvcTestResult lire(String uri, UtilisateurConnecte qui) {
		return mvc.get().uri(uri).with(oauth2Login().oauth2User(qui)).exchange();
	}

	private String projet(UtilisateurConnecte porteur) {
		MvcTestResult r = ecrire("/api/projets", porteur,
				"{\"titre\":\"Caisse hors ligne\",\"description\":\"Une caisse qui marche sans internet.\",\"origine\":\"idee\"}");
		assertThat(r).hasStatus(HttpStatus.CREATED);
		return r.getResponse().getHeader("Location").substring("/api/projets/".length());
	}

	private String role(String projet, UtilisateurConnecte porteur, String titre, String... competences) {
		String liste = String.join("\",\"", competences);
		MvcTestResult r = ecrire("/api/projets/" + projet + "/roles", porteur,
				"{\"titre\":\"" + titre + "\",\"competences\":[\"" + liste + "\"]}");
		assertThat(r).hasStatus(HttpStatus.CREATED);
		return extraire(r, "id");
	}

	private String candidater(String projet, String role, UtilisateurConnecte qui) {
		MvcTestResult r = ecrire("/api/projets/" + projet + "/roles/" + role + "/candidatures", qui, MESSAGE);
		assertThat(r).hasStatus(HttpStatus.CREATED);
		return extraire(r, "id");
	}

	private static String extraire(MvcTestResult r, String champ) {
		try {
			String corps = r.getResponse().getContentAsString();
			int i = corps.indexOf("\"" + champ + "\":\"");
			return corps.substring(i + champ.length() + 4, corps.indexOf('"', i + champ.length() + 4));
		}
		catch (java.io.UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
	}

	private String statutCandidature(String id) {
		return base.sql("SELECT statut FROM candidature WHERE id = :i").param("i", UUID.fromString(id)).query(String.class).single();
	}

	// ---------------------------------------------------------------- publier un rôle

	@Test
	void le_porteur_publie_un_role_et_le_projet_le_montre() {
		UtilisateurConnecte porteur = utilisateur("Awa");
		String projet = projet(porteur);

		MvcTestResult reponse = ecrire("/api/projets/" + projet + "/roles", porteur,
				"{\"titre\":\"Développeur mobile Flutter\",\"competences\":[\" Flutter \",\"Dart\",\"flutter\"]}");

		assertThat(reponse).hasStatus(HttpStatus.CREATED);
		assertThat(reponse).bodyJson().extractingPath("$.statut").isEqualTo("ouvert");
		assertThat(reponse).bodyJson().extractingPath("$.competences").isEqualTo(List.of("flutter", "dart"));
		MvcTestResult projetLu = lire("/api/projets/" + projet, utilisateur("Curieux"));
		assertThat(projetLu).bodyJson().extractingPath("$.roles[0].titre").isEqualTo("Développeur mobile Flutter");
		assertThat(projetLu).bodyJson().extractingPath("$.competencesRecherchees").isEqualTo(List.of("dart", "flutter"));
	}

	@Test
	void publier_un_role_est_reserve_au_porteur_et_valide_ses_champs() {
		UtilisateurConnecte porteur = utilisateur("Awa");
		String projet = projet(porteur);
		String uri = "/api/projets/" + projet + "/roles";

		assertThat(ecrire(uri, utilisateur("Intrus"), "{\"titre\":\"Un rôle\",\"competences\":[\"flutter\"]}")).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(ecrire("/api/projets/" + UUID.randomUUID() + "/roles", porteur, "{\"titre\":\"Un rôle\",\"competences\":[\"flutter\"]}"))
			.hasStatus(HttpStatus.NOT_FOUND);
		String[][] invalides = {
				{ "{\"titre\":\"ab\",\"competences\":[\"flutter\"]}", "titre" },
				{ "{\"titre\":\"Un rôle\",\"competences\":[]}", "competences" },
				{ "{\"titre\":\"Un rôle\"}", "competences" },
				{ "{\"titre\":\"Un rôle\",\"competences\":[\"avec espace\"]}", "competences" },
				{ "{\"titre\":\"Un rôle\",\"competences\":[\"\"]}", "competences" },
				{ "{\"titre\":\"Un rôle\",\"competences\":[\"a\",\"b\",\"c\",\"d\",\"e\",\"f\",\"g\",\"h\",\"i\",\"j\",\"k\"]}", "competences" } };
		for (String[] c : invalides) {
			MvcTestResult reponse = ecrire(uri, porteur, c[0]);
			assertThat(reponse).as(c[0]).hasStatus(HttpStatus.BAD_REQUEST);
			assertThat(reponse).bodyJson().extractingPath("$.erreurs[0].champ").isEqualTo(c[1]);
		}
		assertThat(mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content("{\"titre\":\"Un rôle\",\"competences\":[\"a\"]}")
			.with(oauth2Login().oauth2User(porteur))).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(mvc.post().uri(uri).contentType(MediaType.APPLICATION_JSON).content("{}").with(csrf()))
			.hasStatus(HttpStatus.UNAUTHORIZED);
	}

	// ---------------------------------------------------------------- modifier un rôle

	@Test
	void modifier_un_role_par_fusion_ne_change_que_les_champs_envoyes() {
		UtilisateurConnecte porteur = utilisateur("Awa");
		String projet = projet(porteur);
		String role = role(projet, porteur, "Développeur mobile", "flutter");
		String uri = "/api/projets/" + projet + "/roles/" + role;

		MvcTestResult reponse = modifier(uri, porteur, "{\"titre\":\"Développeuse Flutter\"}");

		assertThat(reponse).hasStatusOk();
		assertThat(reponse).bodyJson().extractingPath("$.titre").isEqualTo("Développeuse Flutter");
		assertThat(reponse).bodyJson().extractingPath("$.competences").isEqualTo(List.of("flutter"));
		assertThat(modifier(uri, porteur, "{\"competences\":[\"Dart\",\"firebase\"]}"))
			.bodyJson().extractingPath("$.competences").isEqualTo(List.of("dart", "firebase"));
	}

	@Test
	void modification_invalide_ou_hors_porteur_est_refusee() {
		UtilisateurConnecte porteur = utilisateur("Awa");
		String projet = projet(porteur);
		String role = role(projet, porteur, "Développeur mobile", "flutter");
		String uri = "/api/projets/" + projet + "/roles/" + role;

		assertThat(modifier(uri, utilisateur("Intrus"), "{\"titre\":\"Un autre titre\"}")).hasStatus(HttpStatus.FORBIDDEN);
		for (String corps : List.of("{\"titre\":null}", "{\"statut\":\"pourvu\"}", "{\"inconnu\":1}", "{\"competences\":[]}",
				"{\"titre\":\"ab\"}", "{\"competences\":[1,2]}")) {
			assertThat(modifier(uri, porteur, corps)).as(corps).hasStatus(HttpStatus.BAD_REQUEST);
		}
		assertThat(modifier("/api/projets/" + projet + "/roles/" + UUID.randomUUID(), porteur, "{\"titre\":\"Un autre titre\"}"))
			.hasStatus(HttpStatus.NOT_FOUND);
		String autreProjet = projet(porteur);
		assertThat(modifier("/api/projets/" + autreProjet + "/roles/" + role, porteur, "{\"titre\":\"Un autre titre\"}"))
			.as("un rôle d'un autre projet n'existe pas ici").hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void fermer_un_role_refuse_ses_candidatures_en_attente_et_on_peut_le_rouvrir() {
		UtilisateurConnecte porteur = utilisateur("Awa");
		String projet = projet(porteur);
		String role = role(projet, porteur, "Développeur mobile", "flutter");
		String candidature = candidater(projet, role, utilisateur("Kofi"));
		String uri = "/api/projets/" + projet + "/roles/" + role;

		assertThat(modifier(uri, porteur, "{\"statut\":\"ferme\"}")).bodyJson().extractingPath("$.statut").isEqualTo("ferme");

		assertThat(statutCandidature(candidature)).isEqualTo("refusee");
		MvcTestResult ferme = ecrire(uri + "/candidatures", utilisateur("Fatou"), MESSAGE);
		assertThat(ferme).hasStatus(HttpStatus.CONFLICT);
		assertThat(ferme).bodyJson().extractingPath("$.code").isEqualTo("role_pourvu");
		assertThat(modifier(uri, porteur, "{\"statut\":\"ouvert\"}")).bodyJson().extractingPath("$.statut").isEqualTo("ouvert");
	}

	// ---------------------------------------------------------------- candidater

	@Test
	void candidater_envoie_une_candidature_visible_du_porteur_et_du_candidat() {
		UtilisateurConnecte porteur = utilisateur("Awa");
		UtilisateurConnecte kofi = utilisateur("Kofi");
		String projet = projet(porteur);
		String role = role(projet, porteur, "Développeur mobile", "flutter");

		MvcTestResult reponse = ecrire("/api/projets/" + projet + "/roles/" + role + "/candidatures", kofi, MESSAGE);

		assertThat(reponse).hasStatus(HttpStatus.CREATED);
		assertThat(reponse).bodyJson().extractingPath("$.statut").isEqualTo("envoyee");
		assertThat(reponse).bodyJson().extractingPath("$.candidat.nom").isEqualTo("Kofi");
		assertThat(reponse).bodyJson().extractingPath("$.role.titre").isEqualTo("Développeur mobile");
		assertThat(reponse).bodyJson().extractingPath("$.projetId").isEqualTo(projet);
		assertThat(reponse).bodyJson().doesNotHavePath("$.email");
		assertThat(lire("/api/projets/" + projet + "/candidatures", porteur)).bodyJson().extractingPath("$.elements.length()").isEqualTo(1);
		assertThat(lire("/api/me/candidatures", kofi)).bodyJson().extractingPath("$.elements[0].role.id").isEqualTo(role);
		assertThat(lire("/api/projets/" + projet + "/candidatures", kofi)).as("seul le porteur voit les candidatures du projet")
			.hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void on_ne_candidate_ni_a_son_propre_projet_ni_deux_fois_ni_avec_un_message_trop_court() {
		UtilisateurConnecte porteur = utilisateur("Awa");
		UtilisateurConnecte kofi = utilisateur("Kofi");
		String projet = projet(porteur);
		String role = role(projet, porteur, "Développeur mobile", "flutter");
		String uri = "/api/projets/" + projet + "/roles/" + role + "/candidatures";

		MvcTestResult propre = ecrire(uri, porteur, MESSAGE);
		assertThat(propre).hasStatus(HttpStatus.CONFLICT);
		assertThat(propre).bodyJson().extractingPath("$.code").isEqualTo("propre_projet");
		candidater(projet, role, kofi);
		MvcTestResult doublon = ecrire(uri, kofi, MESSAGE);
		assertThat(doublon).hasStatus(HttpStatus.CONFLICT);
		assertThat(doublon).bodyJson().extractingPath("$.code").isEqualTo("candidature_existante");
		assertThat(ecrire(uri, utilisateur("Fatou"), "{\"message\":\"court\"}")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(ecrire("/api/projets/" + projet + "/roles/" + UUID.randomUUID() + "/candidatures", kofi, MESSAGE))
			.hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void vingt_et_une_candidatures_dans_la_journee_repondent_429() {
		UtilisateurConnecte porteur = utilisateur("Awa");
		UtilisateurConnecte kofi = utilisateur("Kofi");
		String projet = projet(porteur);
		for (int i = 0; i < Recrutement.CANDIDATURES_PAR_JOUR; i++) {
			candidater(projet, role(projet, porteur, "Rôle numéro " + i, "flutter"), kofi);
		}

		MvcTestResult refus = ecrire("/api/projets/" + projet + "/roles/" + role(projet, porteur, "Un dernier rôle", "dart") + "/candidatures",
				kofi, MESSAGE);

		assertThat(refus).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(refus).bodyJson().extractingPath("$.code").isEqualTo("trop_de_requetes");
		assertThat(Long.parseLong(refus.getResponse().getHeader("Retry-After"))).isBetween(1L, 86400L);
	}

	// ---------------------------------------------------------------- décider

	@Test
	void accepter_fait_entrer_le_candidat_pourvoit_le_role_et_refuse_les_autres_en_une_transaction() {
		UtilisateurConnecte porteur = utilisateur("Awa");
		UtilisateurConnecte kofi = utilisateur("Kofi");
		UtilisateurConnecte fatou = utilisateur("Fatou");
		String projet = projet(porteur);
		String role = role(projet, porteur, "Développeur mobile", "flutter");
		String pourKofi = candidater(projet, role, kofi);
		String pourFatou = candidater(projet, role, fatou);

		MvcTestResult reponse = ecrire("/api/projets/" + projet + "/candidatures/" + pourKofi + "/accepter", porteur, null);

		assertThat(reponse).hasStatusOk();
		assertThat(reponse).bodyJson().extractingPath("$.statut").isEqualTo("acceptee");
		assertThat(reponse).bodyJson().extractingPath("$.decideeLe").isNotNull();
		assertThat(reponse).bodyJson().extractingPath("$.role.statut").isEqualTo("pourvu");
		assertThat(statutCandidature(pourFatou)).isEqualTo("refusee");
		MvcTestResult projetLu = lire("/api/projets/" + projet, kofi);
		assertThat(projetLu).bodyJson().extractingPath("$.nombreMembres").isEqualTo(2);
		assertThat(projetLu).bodyJson().extractingPath("$.moi.estMembre").isEqualTo(true);
		assertThat(projetLu).bodyJson().extractingPath("$.equipe[1].role").isEqualTo("Développeur mobile");
		assertThat(projetLu).bodyJson().extractingPath("$.competencesRecherchees").isEqualTo(List.of());
	}

	@Test
	void une_decision_ne_se_prend_qu_une_fois_et_le_role_pourvu_ferme_la_porte() {
		UtilisateurConnecte porteur = utilisateur("Awa");
		String projet = projet(porteur);
		String role = role(projet, porteur, "Développeur mobile", "flutter");
		String premiere = candidater(projet, role, utilisateur("Kofi"));
		String seconde = candidater(projet, role, utilisateur("Fatou"));
		ecrire("/api/projets/" + projet + "/candidatures/" + premiere + "/accepter", porteur, null);

		MvcTestResult encore = ecrire("/api/projets/" + projet + "/candidatures/" + premiere + "/accepter", porteur, null);
		assertThat(encore).hasStatus(HttpStatus.CONFLICT);
		assertThat(encore).bodyJson().extractingPath("$.code").isEqualTo("transition_invalide");
		assertThat(ecrire("/api/projets/" + projet + "/candidatures/" + seconde + "/accepter", porteur, null))
			.as("déjà refusée à l'acceptation de la première").hasStatus(HttpStatus.CONFLICT);
	}

	@Test
	void refuser_et_retirer_changent_seulement_le_statut() {
		UtilisateurConnecte porteur = utilisateur("Awa");
		UtilisateurConnecte kofi = utilisateur("Kofi");
		UtilisateurConnecte fatou = utilisateur("Fatou");
		String projet = projet(porteur);
		String role = role(projet, porteur, "Développeur mobile", "flutter");
		String pourKofi = candidater(projet, role, kofi);
		String pourFatou = candidater(projet, role, fatou);

		assertThat(ecrire("/api/projets/" + projet + "/candidatures/" + pourKofi + "/refuser", porteur, null))
			.bodyJson().extractingPath("$.statut").isEqualTo("refusee");
		assertThat(ecrire("/api/projets/" + projet + "/candidatures/" + pourFatou + "/retirer", fatou, null))
			.bodyJson().extractingPath("$.statut").isEqualTo("retiree");
		assertThat(base.sql("SELECT statut FROM role_recherche WHERE id = :r").param("r", UUID.fromString(role)).query(String.class).single())
			.as("le rôle reste ouvert").isEqualTo("ouvert");
		assertThat(lire("/api/projets/" + projet + "/candidatures?statut=retiree", porteur))
			.bodyJson().extractingPath("$.elements.length()").isEqualTo(1);
		assertThat(ecrire("/api/projets/" + projet + "/candidatures/" + pourFatou + "/retirer", fatou, null))
			.hasStatus(HttpStatus.CONFLICT);
	}

	@Test
	void chaque_decision_est_reservee_a_la_bonne_personne() {
		UtilisateurConnecte porteur = utilisateur("Awa");
		UtilisateurConnecte kofi = utilisateur("Kofi");
		String projet = projet(porteur);
		String role = role(projet, porteur, "Développeur mobile", "flutter");
		String c = candidater(projet, role, kofi);
		String base = "/api/projets/" + projet + "/candidatures/" + c + "/";

		assertThat(ecrire(base + "accepter", kofi, null)).as("le candidat ne s'accepte pas lui-même").hasStatus(HttpStatus.FORBIDDEN);
		assertThat(ecrire(base + "refuser", utilisateur("Intrus"), null)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(ecrire(base + "retirer", porteur, null)).as("le porteur ne retire pas la candidature d'un autre").hasStatus(HttpStatus.FORBIDDEN);
		assertThat(ecrire(base + "retirer", utilisateur("Intrus"), null)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(ecrire(base + "supprimer", porteur, null)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(ecrire("/api/projets/" + projet(porteur) + "/candidatures/" + c + "/accepter", porteur, null))
			.as("une candidature d'un autre projet n'existe pas ici").hasStatus(HttpStatus.NOT_FOUND);
		assertThat(statutCandidature(c)).isEqualTo("envoyee");
	}

	@Test
	void un_ancien_membre_accepte_a_nouveau_retrouve_sa_place() {
		UtilisateurConnecte porteur = utilisateur("Awa");
		UtilisateurConnecte kofi = utilisateur("Kofi");
		String projet = projet(porteur);
		base.sql("INSERT INTO membre_equipe (projet_id, utilisateur_id, rejoint_le, parti_le) VALUES (:p, :u, now(), now())")
			.param("p", UUID.fromString(projet)).param("u", kofi.id()).update();
		String role = role(projet, porteur, "Développeur mobile", "flutter");
		String c = candidater(projet, role, kofi);

		assertThat(ecrire("/api/projets/" + projet + "/candidatures/" + c + "/accepter", porteur, null)).hasStatusOk();

		assertThat(lire("/api/projets/" + projet, kofi)).bodyJson().extractingPath("$.nombreMembres").isEqualTo(2);
		assertThat(base.sql("SELECT parti_le IS NULL FROM membre_equipe WHERE projet_id = :p AND utilisateur_id = :u")
			.param("p", UUID.fromString(projet)).param("u", kofi.id()).query(Boolean.class).single()).isTrue();
	}

	@Test
	void deux_acceptations_simultanees_sur_le_meme_role_une_seule_passe() throws Exception {
		UtilisateurConnecte porteur = utilisateur("Awa");
		String projet = projet(porteur);
		String role = role(projet, porteur, "Développeur mobile", "flutter");
		UUID premiere = UUID.fromString(candidater(projet, role, utilisateur("Kofi")));
		UUID seconde = UUID.fromString(candidater(projet, role, utilisateur("Fatou")));
		UUID projetId = UUID.fromString(projet);

		long reussites = enParallele(List.of(
				() -> tenter(() -> recrutement.decider(projetId, premiere, "accepter", porteur.id(), Instant.now())),
				() -> tenter(() -> recrutement.decider(projetId, seconde, "accepter", porteur.id(), Instant.now()))));

		assertThat(reussites).isEqualTo(1);
		assertThat(base.sql("SELECT count(*) FROM membre_equipe WHERE projet_id = :p AND role_recherche_id = :r AND parti_le IS NULL")
			.param("p", projetId).param("r", UUID.fromString(role)).query(Long.class).single()).isEqualTo(1);
	}

	// ---------------------------------------------------------------- listes

	@Test
	void mes_candidatures_et_pagination_par_curseur() throws Exception {
		UtilisateurConnecte porteur = utilisateur("Awa");
		UtilisateurConnecte kofi = utilisateur("Kofi");
		String projet = projet(porteur);
		List<String> creees = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			creees.add(candidater(projet, role(projet, porteur, "Rôle " + i + " ouvert", "flutter"), kofi));
		}
		candidater(projet, role(projet, porteur, "Rôle d'une autre personne", "dart"), utilisateur("Fatou"));

		MvcTestResult page1 = lire("/api/me/candidatures?limite=2", kofi);
		var premiere = json.readTree(page1.getResponse().getContentAsString());
		assertThat(premiere.get("elements")).hasSize(2);
		assertThat(premiere.get("elements").get(0).get("id").asString()).isEqualTo(creees.get(2));
		String curseur = premiere.get("curseurSuivant").asString();

		var seconde = json.readTree(lire("/api/me/candidatures?limite=2&curseur=" + curseur, kofi).getResponse().getContentAsString());
		assertThat(seconde.get("elements")).hasSize(1);
		assertThat(seconde.get("elements").get(0).get("id").asString()).isEqualTo(creees.get(0));
		assertThat(seconde.get("curseurSuivant").isNull()).isTrue();
	}

	@Test
	void parametres_de_liste_invalides_repondent_400() {
		UtilisateurConnecte porteur = utilisateur("Awa");
		String projet = projet(porteur);

		for (String uri : List.of("/api/me/candidatures?statut=inconnu", "/api/me/candidatures?limite=0",
				"/api/me/candidatures?limite=101", "/api/me/candidatures?curseur=nimporte",
				"/api/projets/" + projet + "/candidatures?statut=oups")) {
			MvcTestResult reponse = lire(uri, porteur);
			assertThat(reponse).as(uri).hasStatus(HttpStatus.BAD_REQUEST);
			assertThat(reponse).bodyJson().extractingPath("$.code").isEqualTo("validation");
		}
		assertThat(mvc.get().uri("/api/me/candidatures")).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void supprimer_le_compte_du_candidat_supprime_ses_candidatures() {
		UtilisateurConnecte porteur = utilisateur("Awa");
		UtilisateurConnecte kofi = utilisateur("Kofi");
		String projet = projet(porteur);
		String c = candidater(projet, role(projet, porteur, "Développeur mobile", "flutter"), kofi);

		base.sql("DELETE FROM utilisateur WHERE id = :u").param("u", kofi.id()).update();

		assertThat(base.sql("SELECT count(*) FROM candidature WHERE id = :i").param("i", UUID.fromString(c)).query(Long.class).single())
			.isZero();
	}

	// ---------------------------------------------------------------- concurrence

	private static boolean tenter(Runnable action) {
		try {
			action.run();
			return true;
		}
		catch (ErreurProjets refus) {
			return false;
		}
	}

	private static long enParallele(List<Callable<Boolean>> taches) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(taches.size());
		try {
			CountDownLatch depart = new CountDownLatch(1);
			List<Future<Boolean>> resultats = new ArrayList<>();
			for (Callable<Boolean> tache : taches) {
				resultats.add(pool.submit(() -> {
					depart.await();
					return tache.call();
				}));
			}
			depart.countDown();
			long reussites = 0;
			for (Future<Boolean> resultat : resultats) {
				if (resultat.get()) {
					reussites++;
				}
			}
			return reussites;
		}
		finally {
			pool.shutdownNow();
		}
	}

}
