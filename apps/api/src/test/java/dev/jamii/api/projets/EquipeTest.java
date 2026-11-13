package dev.jamii.api.projets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
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

/** Les jalons et les tâches d'un projet (contracts/projets.yaml, tag « equipe »), sur un vrai PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class EquipeTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient base;

	@Autowired
	JsonMapper json;

	private Instant horloge = Instant.now().minus(2, ChronoUnit.HOURS);

	// ---------------------------------------------------------------- aides

	private Timestamp ensuite() {
		horloge = horloge.plusSeconds(1);
		return Timestamp.from(horloge);
	}

	private UtilisateurConnecte utilisateur(String nom) {
		UUID id = UUID.randomUUID();
		Timestamp t = Timestamp.from(Instant.now());
		base.sql("INSERT INTO utilisateur (id, nom, email, cree_le, mis_a_jour_le) VALUES (:id, :nom, :email, :t, :t)")
			.param("id", id).param("nom", nom).param("email", nom + "." + id + "@secret.example").param("t", t).update();
		return new UtilisateurConnecte(id, Set.of(Role.MEMBRE));
	}

	private UUID projet(UtilisateurConnecte porteur, UtilisateurConnecte... membres) {
		UUID id = UUID.randomUUID();
		Timestamp t = ensuite();
		base.sql("""
				INSERT INTO projet (id, titre, origine, porteur_id, derniere_activite_le, cree_le, mis_a_jour_le)
				VALUES (:id, 'Le carnet des coopératives', 'idee', :p, :t, :t, :t)
				""").param("id", id).param("p", porteur.id()).param("t", t).update();
		List<UtilisateurConnecte> equipe = new ArrayList<>(List.of(membres));
		equipe.addFirst(porteur);
		for (UtilisateurConnecte m : equipe) {
			base.sql("INSERT INTO membre_equipe (projet_id, utilisateur_id, rejoint_le) VALUES (:p, :u, :t)")
				.param("p", id).param("u", m.id()).param("t", t).update();
		}
		return id;
	}

	private UUID jalon(UUID projet, String titre, int ordre, String statut) {
		UUID id = UUID.randomUUID();
		base.sql("INSERT INTO jalon (id, projet_id, titre, echeance, statut, ordre, cree_le) VALUES (:id, :p, :titre, :e, :s, :o, :t)")
			.param("id", id).param("p", projet).param("titre", titre).param("e", Date.valueOf(LocalDate.of(2026, 11, 20)))
			.param("s", statut).param("o", ordre).param("t", ensuite()).update();
		return id;
	}

	private UUID tache(UUID projet, String titre, UUID jalon, UtilisateurConnecte assignee, String statut) {
		UUID id = UUID.randomUUID();
		Timestamp t = ensuite();
		base.sql("""
				INSERT INTO tache (id, projet_id, jalon_id, titre, assignee_id, statut, livree_le, cree_le)
				VALUES (:id, :p, :j, :titre, :a, :s, :l, :t)
				""").param("id", id).param("p", projet).param("j", jalon).param("titre", titre)
			.param("a", assignee == null ? null : assignee.id()).param("s", statut)
			.param("l", "livree".equals(statut) ? t : null).param("t", t).update();
		return id;
	}

	private MvcTestResult lire(String uri, UtilisateurConnecte qui) {
		return mvc.get().uri(uri).with(oauth2Login().oauth2User(qui)).exchange();
	}

	private static final MediaType FUSION = new MediaType("application", "merge-patch+json");

	private MvcTestResult modifier(UUID projet, UUID tache, String corps, UtilisateurConnecte qui) {
		return mvc.patch().uri("/api/projets/" + projet + "/taches/" + tache).contentType(FUSION).content(corps)
			.with(csrf()).with(oauth2Login().oauth2User(qui)).exchange();
	}

	private JsonNode corps(MvcTestResult r) throws Exception {
		return json.readTree(r.getResponse().getContentAsString());
	}

	private List<String> ids(MvcTestResult r) throws Exception {
		List<String> ids = new ArrayList<>();
		corps(r).get("elements").forEach(e -> ids.add(e.get("id").asString()));
		return ids;
	}

	// ---------------------------------------------------------------- jalons

	@Test
	void les_jalons_dans_l_ordre_de_l_equipe() throws Exception {
		UtilisateurConnecte kofi = utilisateur("Kofi");
		UUID projet = projet(kofi);
		UUID second = jalon(projet, "Les pesées hors ligne", 1, "prevu");
		UUID premier = jalon(projet, "La maquette validée", 0, "atteint");

		MvcTestResult reponse = lire("/api/projets/" + projet + "/jalons", kofi);

		assertThat(reponse).hasStatusOk();
		JsonNode jalons = corps(reponse);
		assertThat(jalons).hasSize(2);
		assertThat(jalons.get(0).get("id").asString()).isEqualTo(premier.toString());
		assertThat(jalons.get(0).get("statut").asString()).isEqualTo("atteint");
		assertThat(jalons.get(0).get("echeance").asString()).isEqualTo("2026-11-20");
		assertThat(jalons.get(1).get("id").asString()).isEqualTo(second.toString());
	}

	// ---------------------------------------------------------------- tâches

	@Test
	void les_taches_avec_leur_assignee_sans_jamais_d_email() throws Exception {
		UtilisateurConnecte kofi = utilisateur("Kofi");
		UtilisateurConnecte awa = utilisateur("Awa");
		UUID projet = projet(kofi, awa);
		UUID j = jalon(projet, "La maquette validée", 0, "prevu");
		UUID faite = tache(projet, "Écrire l'écran des pesées", j, awa, "livree");
		UUID libre = tache(projet, "Choisir la base locale", null, null, "a_faire");

		MvcTestResult reponse = lire("/api/projets/" + projet + "/taches?limite=100", awa);

		assertThat(reponse).hasStatusOk();
		assertThat(ids(reponse)).containsExactly(faite.toString(), libre.toString());
		JsonNode premiere = corps(reponse).get("elements").get(0);
		assertThat(premiere.get("titre").asString()).isEqualTo("Écrire l'écran des pesées");
		assertThat(premiere.get("jalonId").asString()).isEqualTo(j.toString());
		assertThat(premiere.get("assignee").get("nom").asString()).isEqualTo("Awa");
		assertThat(premiere.get("statut").asString()).isEqualTo("livree");
		assertThat(premiere.get("livreeLe").asString()).isNotBlank();
		assertThat(premiere.get("proposeeParIa").asBoolean()).isFalse();
		assertThat(premiere.get("version").asInt()).isZero();
		assertThat(corps(reponse).get("elements").get(1).get("assignee").isNull()).isTrue();
		assertThat(reponse.getResponse().getContentAsString()).doesNotContain("secret.example");
	}

	@Test
	void les_filtres_par_statut_par_jalon_et_mes_taches() throws Exception {
		UtilisateurConnecte kofi = utilisateur("Kofi");
		UtilisateurConnecte awa = utilisateur("Awa");
		UUID projet = projet(kofi, awa);
		UUID j = jalon(projet, "La maquette validée", 0, "prevu");
		UUID aMoi = tache(projet, "Une tâche de Kofi", j, kofi, "en_cours");
		UUID aAwa = tache(projet, "Une tâche d'Awa", null, awa, "livree");

		String base = "/api/projets/" + projet + "/taches";
		assertThat(ids(lire(base + "?statut=livree", kofi))).containsExactly(aAwa.toString());
		assertThat(ids(lire(base + "?jalonId=" + j, kofi))).containsExactly(aMoi.toString());
		assertThat(ids(lire(base + "?assignee=moi", kofi))).containsExactly(aMoi.toString());
	}

	@Test
	void la_pagination_par_curseur_ne_perd_ni_ne_repete_aucune_tache() throws Exception {
		UtilisateurConnecte kofi = utilisateur("Kofi");
		UUID projet = projet(kofi);
		List<String> creees = new ArrayList<>();
		for (int i = 0; i < 5; i++) {
			creees.add(tache(projet, "Tâche numéro " + i, null, null, "a_faire").toString());
		}

		String base = "/api/projets/" + projet + "/taches?limite=2";
		MvcTestResult page1 = lire(base, kofi);
		MvcTestResult page2 = lire(base + "&curseur=" + corps(page1).get("curseurSuivant").asString(), kofi);
		MvcTestResult page3 = lire(base + "&curseur=" + corps(page2).get("curseurSuivant").asString(), kofi);

		assertThat(ids(page1)).containsExactlyElementsOf(creees.subList(0, 2));
		assertThat(ids(page2)).containsExactlyElementsOf(creees.subList(2, 4));
		assertThat(ids(page3)).containsExactly(creees.get(4));
		assertThat(corps(page3).get("curseurSuivant").isNull()).isTrue();
	}

	// ---------------------------------------------------------------- accès

	@Test
	void reserve_a_l_equipe_actuelle() {
		UtilisateurConnecte kofi = utilisateur("Kofi");
		UtilisateurConnecte parti = utilisateur("Parti");
		UUID projet = projet(kofi, parti);
		base.sql("UPDATE membre_equipe SET parti_le = now() WHERE projet_id = :p AND utilisateur_id = :u")
			.param("p", projet).param("u", parti.id()).update();
		tache(projet, "Une tâche de l'équipe", null, kofi, "a_faire");

		for (String chemin : List.of("/taches", "/jalons")) {
			String uri = "/api/projets/" + projet + chemin;
			assertThat(lire(uri, utilisateur("Curieux"))).as(chemin).hasStatus(HttpStatus.FORBIDDEN);
			assertThat(lire(uri, parti)).as("un membre parti " + chemin).hasStatus(HttpStatus.FORBIDDEN);
			assertThat(lire("/api/projets/" + UUID.randomUUID() + chemin, kofi)).as("projet inconnu " + chemin)
				.hasStatus(HttpStatus.NOT_FOUND);
			assertThat(mvc.get().uri(uri)).as("sans connexion " + chemin).hasStatus(HttpStatus.UNAUTHORIZED);
		}
	}

	@Test
	void parametres_invalides_repondent_400_au_format_du_contrat() {
		UtilisateurConnecte kofi = utilisateur("Kofi");
		UUID projet = projet(kofi);
		for (String requete : List.of("statut=fini", "assignee=awa", "limite=0", "limite=101", "jalonId=pas-un-id", "curseur=nimporte")) {
			MvcTestResult reponse = lire("/api/projets/" + projet + "/taches?" + requete, kofi);
			assertThat(reponse).as(requete).hasStatus(HttpStatus.BAD_REQUEST).hasContentType("application/problem+json");
			assertThat(reponse).bodyJson().extractingPath("$.code").isEqualTo("validation");
		}
	}

	// ---------------------------------------------------------------- base

	@Test
	void une_tache_ne_vise_qu_un_jalon_de_son_projet_et_supprimer_un_jalon_la_detache() {
		UtilisateurConnecte kofi = utilisateur("Kofi");
		UUID projet = projet(kofi);
		UUID autre = projet(kofi);
		UUID jalonAutre = jalon(autre, "Un jalon d'un autre projet", 0, "prevu");
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> tache(projet, "Mauvais jalon", jalonAutre, null, "a_faire"))
			.as("clé étrangère composée").isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);

		UUID j = jalon(projet, "Un jalon", 0, "prevu");
		UUID t = tache(projet, "Une tâche du jalon", j, null, "a_faire");
		base.sql("DELETE FROM jalon WHERE id = :j").param("j", j).update();

		assertThat(base.sql("SELECT projet_id FROM tache WHERE id = :t").param("t", t).query(UUID.class).single()).isEqualTo(projet);
		assertThat(base.sql("SELECT jalon_id FROM tache WHERE id = :t").param("t", t).query(UUID.class).optional()).isEmpty();
	}

	// ---------------------------------------------------------------- modifier une tâche

	@Test
	void prendre_une_tache_la_livrer_puis_revenir_en_arriere() throws Exception {
		UtilisateurConnecte kofi = utilisateur("Kofi");
		UtilisateurConnecte awa = utilisateur("Awa");
		UUID projet = projet(kofi, awa);
		UUID tache = tache(projet, "Écrire l'écran des pesées", null, null, "a_faire");
		base.sql("UPDATE projet SET derniere_activite_le = '2026-01-01T00:00:00Z' WHERE id = :p").param("p", projet).update();

		MvcTestResult prise = modifier(projet, tache, "{\"assigneeId\":\"" + awa.id() + "\",\"statut\":\"en_cours\",\"version\":0}", awa);
		assertThat(prise).hasStatusOk();
		assertThat(corps(prise).get("assignee").get("nom").asString()).isEqualTo("Awa");
		assertThat(corps(prise).get("statut").asString()).isEqualTo("en_cours");
		assertThat(corps(prise).get("version").asInt()).isEqualTo(1);
		assertThat(corps(prise).get("livreeLe").isNull()).isTrue();

		MvcTestResult livree = modifier(projet, tache, "{\"statut\":\"livree\",\"version\":1}", kofi);
		assertThat(livree).hasStatusOk();
		assertThat(corps(livree).get("livreeLe").asString()).isNotBlank();
		assertThat(corps(livree).get("assignee").get("nom").asString()).as("absent, l'assignee ne change pas").isEqualTo("Awa");

		MvcTestResult renommee = modifier(projet, tache, "{\"titre\":\"Écrire l'écran des pesées, hors ligne\",\"version\":2}", kofi);
		assertThat(corps(renommee).get("livreeLe").asString()).as("la date de livraison ne bouge plus")
			.isEqualTo(corps(livree).get("livreeLe").asString());

		MvcTestResult rouverte = modifier(projet, tache, "{\"statut\":\"en_cours\",\"version\":3}", kofi);
		assertThat(corps(rouverte).get("livreeLe").isNull()).isTrue();
		assertThat(base.sql("SELECT derniere_activite_le > '2026-06-01' FROM projet WHERE id = :p").param("p", projet)
			.query(Boolean.class).single()).as("l'activité du projet avance").isTrue();
	}

	@Test
	void deux_modifications_de_la_meme_version_la_seconde_recoit_409() {
		UtilisateurConnecte kofi = utilisateur("Kofi");
		UtilisateurConnecte awa = utilisateur("Awa");
		UUID projet = projet(kofi, awa);
		UUID tache = tache(projet, "Choisir la base locale", null, null, "a_faire");

		assertThat(modifier(projet, tache, "{\"assigneeId\":\"" + kofi.id() + "\",\"version\":0}", kofi)).hasStatusOk();
		MvcTestResult tardive = modifier(projet, tache, "{\"assigneeId\":\"" + awa.id() + "\",\"version\":0}", awa);

		assertThat(tardive).hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.code").isEqualTo("modification_concurrente");
		assertThat(base.sql("SELECT assignee_id FROM tache WHERE id = :t").param("t", tache).query(UUID.class).single())
			.isEqualTo(kofi.id());
	}

	@Test
	void null_vide_le_champ_et_absent_le_garde() throws Exception {
		UtilisateurConnecte kofi = utilisateur("Kofi");
		UUID projet = projet(kofi);
		UUID j = jalon(projet, "La maquette validée", 0, "prevu");
		UUID tache = tache(projet, "Une tâche complète", j, kofi, "a_faire");
		assertThat(modifier(projet, tache, "{\"echeance\":\"2026-11-30\",\"version\":0}", kofi)).hasStatusOk();

		MvcTestResult videe = modifier(projet, tache, "{\"jalonId\":null,\"assigneeId\":null,\"version\":1}", kofi);

		assertThat(videe).hasStatusOk();
		assertThat(corps(videe).get("jalonId").isNull()).isTrue();
		assertThat(corps(videe).get("assignee").isNull()).isTrue();
		assertThat(corps(videe).get("echeance").asString()).isEqualTo("2026-11-30");
		assertThat(corps(modifier(projet, tache, "{\"echeance\":null,\"version\":2}", kofi)).get("echeance").isNull()).isTrue();
	}

	@Test
	void on_n_assigne_qu_un_membre_et_on_ne_vise_qu_un_jalon_du_projet() {
		UtilisateurConnecte kofi = utilisateur("Kofi");
		UtilisateurConnecte dehors = utilisateur("Dehors");
		UUID projet = projet(kofi);
		UUID autre = projet(dehors);
		UUID jalonAutre = jalon(autre, "Un jalon d'un autre projet", 0, "prevu");
		UUID tache = tache(projet, "Une tâche", null, null, "a_faire");

		assertThat(modifier(projet, tache, "{\"assigneeId\":\"" + dehors.id() + "\",\"version\":0}", kofi))
			.hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.code").isEqualTo("pas_membre");
		MvcTestResult mauvaisJalon = modifier(projet, tache, "{\"jalonId\":\"" + jalonAutre + "\",\"version\":0}", kofi);
		assertThat(mauvaisJalon).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(mauvaisJalon).bodyJson().extractingPath("$.erreurs[0].champ").isEqualTo("jalonId");
		assertThat(base.sql("SELECT version FROM tache WHERE id = :t").param("t", tache).query(Integer.class).single()).isZero();
	}

	@Test
	void modifier_est_reserve_a_l_equipe() {
		UtilisateurConnecte kofi = utilisateur("Kofi");
		UtilisateurConnecte curieux = utilisateur("Curieux");
		UUID projet = projet(kofi);
		UUID autre = projet(curieux);
		UUID tache = tache(projet, "Une tâche de l'équipe", null, null, "a_faire");
		String prendre = "{\"statut\":\"en_cours\",\"version\":0}";

		assertThat(modifier(projet, tache, prendre, curieux)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(modifier(autre, tache, prendre, curieux)).as("la tâche d'un autre projet").hasStatus(HttpStatus.NOT_FOUND);
		assertThat(modifier(projet, UUID.randomUUID(), prendre, kofi)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(mvc.patch().uri("/api/projets/" + projet + "/taches/" + tache).contentType(FUSION).content(prendre)
			.with(oauth2Login().oauth2User(kofi))).as("sans jeton CSRF").hasStatus(HttpStatus.FORBIDDEN);
		assertThat(base.sql("SELECT statut FROM tache WHERE id = :t").param("t", tache).query(String.class).single()).isEqualTo("a_faire");
	}

	@Test
	void une_modification_invalide_repond_400_et_ne_change_rien() {
		UtilisateurConnecte kofi = utilisateur("Kofi");
		UUID projet = projet(kofi);
		UUID tache = tache(projet, "Une tâche", null, null, "a_faire");
		for (String corps : List.of("{\"statut\":\"en_cours\"}", "{\"version\":\"0\"}", "{\"version\":0,\"auteur\":\"moi\"}",
				"{\"statut\":\"fini\",\"version\":0}", "{\"titre\":\"ab\",\"version\":0}", "{\"echeance\":\"demain\",\"version\":0}",
				"{\"assigneeId\":\"pas-un-id\",\"version\":0}", "{\"titre\":null,\"version\":0}", "{\"statut\":null,\"version\":0}")) {
			MvcTestResult reponse = modifier(projet, tache, corps, kofi);
			assertThat(reponse).as(corps).hasStatus(HttpStatus.BAD_REQUEST).hasContentType("application/problem+json");
			assertThat(reponse).as(corps).bodyJson().extractingPath("$.code").isEqualTo("validation");
		}
		assertThat(base.sql("SELECT version FROM tache WHERE id = :t").param("t", tache).query(Integer.class).single()).isZero();
	}

	@Test
	void mes_taches_sont_dans_l_export_de_mes_donnees() throws Exception {
		UtilisateurConnecte awa = utilisateur("Awa");
		UUID projet = projet(awa);
		UUID tache = tache(projet, "Écrire l'écran des pesées", null, awa, "livree");
		tache(projet, "Une tâche de personne", null, null, "a_faire");

		JsonNode taches = corps(mvc.get().uri("/api/me/export").with(oauth2Login().oauth2User(awa)).exchange()).get("projets").get("taches");

		assertThat(taches).hasSize(1);
		assertThat(taches.get(0).get("id").asString()).isEqualTo(tache.toString());
		assertThat(taches.get(0).get("statut").asString()).isEqualTo("livree");
	}

}
