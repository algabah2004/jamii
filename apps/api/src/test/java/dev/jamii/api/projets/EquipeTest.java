package dev.jamii.api.projets;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import dev.jamii.api.TestcontainersConfiguration;
import dev.jamii.api.auth.Role;
import dev.jamii.api.auth.UtilisateurConnecte;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** GET /api/projets/{id}/jalons et /taches (contracts/projets.yaml, tag « equipe »), sur un vrai PostgreSQL. */
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

}
