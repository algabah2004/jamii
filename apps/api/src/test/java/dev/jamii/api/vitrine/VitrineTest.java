package dev.jamii.api.vitrine;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** La vitrine (contracts/vitrine.yaml) : les projets vraiment en ligne, lisibles sans connexion, sur un vrai PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class VitrineTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient base;

	@Autowired
	JsonMapper json;

	// ---------------------------------------------------------------- aides

	private UUID utilisateur(String nom, String pays, boolean profilPublie) {
		UUID id = UUID.randomUUID();
		Timestamp t = Timestamp.from(Instant.now());
		base.sql("INSERT INTO utilisateur (id, nom, email, cree_le, mis_a_jour_le) VALUES (:id, :nom, :email, :t, :t)")
			.param("id", id).param("nom", nom).param("email", nom + "." + id + "@secret.example").param("t", t).update();
		if (pays != null) {
			base.sql("""
					INSERT INTO profil (utilisateur_id, pays, publie, generation_statut, mis_a_jour_le)
					VALUES (:id, :pays, :publie, 'terminee', :t)
					""").param("id", id).param("pays", pays).param("publie", profilPublie).param("t", t).update();
		}
		return id;
	}

	private UUID projet(String titre, String description, String origine, UUID porteur, UUID... autres) {
		UUID id = UUID.randomUUID();
		Timestamp t = Timestamp.from(Instant.now());
		base.sql("""
				INSERT INTO projet (id, titre, description, origine, porteur_id, derniere_activite_le, cree_le, mis_a_jour_le)
				VALUES (:id, :titre, :d, :o, :p, :t, :t, :t)
				""").param("id", id).param("titre", titre).param("d", description).param("o", origine).param("p", porteur).param("t", t).update();
		List<UUID> equipe = new ArrayList<>(List.of(autres));
		equipe.addFirst(porteur);
		for (UUID m : equipe) {
			base.sql("INSERT INTO membre_equipe (projet_id, utilisateur_id, rejoint_le) VALUES (:p, :u, :t)")
				.param("p", id).param("u", m).param("t", t).update();
		}
		return id;
	}

	/** Un déploiement : `verifieIlYa` heures pour la dernière réponse, `enLigneIlYa` heures pour le premier passage en ligne. */
	private void deploiement(UUID projet, String hebergeur, String statut, long verifieIlYaHeures, long enLigneIlYaHeures) {
		Instant maintenant = Instant.now();
		base.sql("""
				INSERT INTO deploiement (id, projet_id, url, hebergeur, statut, verifie_le, mis_en_ligne_le, cree_le)
				VALUES (:id, :p, :url, :h, :s, :v, :m, :c)
				""")
			.param("id", UUID.randomUUID()).param("p", projet).param("url", "https://" + projet + ".example.com")
			.param("h", hebergeur).param("s", statut)
			.param("v", Timestamp.from(maintenant.minus(verifieIlYaHeures, ChronoUnit.HOURS)))
			.param("m", Timestamp.from(maintenant.minus(enLigneIlYaHeures, ChronoUnit.HOURS)))
			.param("c", Timestamp.from(maintenant.minus(enLigneIlYaHeures + 1, ChronoUnit.HOURS)))
			.update();
	}

	private MvcTestResult lire(String uri) {
		return mvc.get().uri(uri).exchange();
	}

	private JsonNode corps(MvcTestResult r) throws Exception {
		return json.readTree(r.getResponse().getContentAsString());
	}

	private JsonNode element(String uri, UUID projet) throws Exception {
		for (JsonNode e : corps(lire(uri)).get("elements")) {
			if (e.get("projetId").asString().equals(projet.toString())) {
				return e;
			}
		}
		return null;
	}

	/** Tous les identifiants de la vitrine, page après page, avec une page de 2 pour forcer le curseur. */
	private List<String> toutParcourir(String filtre) throws Exception {
		List<String> ids = new ArrayList<>();
		String curseur = null;
		do {
			JsonNode page = corps(lire("/api/vitrine?limite=2" + filtre + (curseur == null ? "" : "&curseur=" + curseur)));
			page.get("elements").forEach(e -> ids.add(e.get("projetId").asString()));
			curseur = page.get("curseurSuivant").isNull() ? null : page.get("curseurSuivant").asString();
		}
		while (curseur != null);
		return ids;
	}

	// ---------------------------------------------------------------- ce qui apparaît

	@Test
	void un_projet_en_ligne_apparait_sans_connexion_avec_son_equipe_et_son_label() throws Exception {
		UUID awa = utilisateur("awa", "SN", true);
		UUID kofi = utilisateur("kofi", "KE", true);
		UUID projet = projet("Boutique de couture", "Un catalogue et des commandes par WhatsApp.", "cercle", awa, kofi);
		deploiement(projet, "datacloud", "en_ligne", 1, 30);

		MvcTestResult reponse = lire("/api/vitrine");
		assertThat(reponse).hasStatus(HttpStatus.OK);
		assertThat(reponse.getResponse().getHeader("Cache-Control")).isEqualTo("public, max-age=60");

		JsonNode e = element("/api/vitrine?limite=100", projet);
		assertThat(e).isNotNull();
		assertThat(e.get("titre").asString()).isEqualTo("Boutique de couture");
		assertThat(e.get("accroche").asString()).isEqualTo("Un catalogue et des commandes par WhatsApp.");
		assertThat(e.get("origine").asString()).isEqualTo("cercle");
		assertThat(e.get("url").asString()).startsWith("https://");
		assertThat(e.get("hebergeur").asString()).isEqualTo("datacloud");
		assertThat(e.get("equipe")).hasSize(2);
		assertThat(e.get("equipe").get(0).get("nom").asString()).isEqualTo("awa");
		assertThat(e.get("equipe").get(1).get("nom").asString()).isEqualTo("kofi");
		assertThat(e.get("paysEquipe").toString()).contains("SN", "KE");
		assertThat(e.toString()).doesNotContain("secret.example").doesNotContain("@");
	}

	@Test
	void un_projet_dont_l_adresse_ne_repond_plus_depuis_24_h_sort_de_la_vitrine() throws Exception {
		UUID awa = utilisateur("awa", null, false);
		UUID frais = projet("Frais", "Répond.", "idee", awa);
		UUID perime = projet("Périmé", "Ne répond plus.", "idee", awa);
		deploiement(frais, "autre", "en_ligne", 23, 50);
		deploiement(perime, "autre", "en_ligne", 25, 50);

		assertThat(element("/api/vitrine?limite=100", frais)).isNotNull();
		assertThat(element("/api/vitrine?limite=100", perime)).isNull();
	}

	@Test
	void un_projet_sans_deploiement_en_ligne_n_apparait_pas() throws Exception {
		UUID awa = utilisateur("awa", null, false);
		UUID encours = projet("En cours", "Pas encore.", "idee", awa);
		UUID echec = projet("Échec", "Jamais répondu.", "idee", awa);
		UUID sans = projet("Sans adresse", "Aucune adresse.", "idee", awa);
		deploiement(encours, "autre", "en_cours", 1, 5);
		deploiement(echec, "autre", "echec", 1, 5);

		for (UUID p : List.of(encours, echec, sans)) {
			assertThat(element("/api/vitrine?limite=100", p)).isNull();
		}
	}

	@Test
	void les_plus_recemment_en_ligne_d_abord_et_le_curseur_ne_repete_rien() throws Exception {
		UUID awa = utilisateur("awa", null, false);
		UUID ancien = projet("Ancien", "A.", "idee", awa);
		UUID milieu = projet("Milieu", "B.", "idee", awa);
		UUID recent = projet("Récent", "C.", "idee", awa);
		deploiement(ancien, "autre", "en_ligne", 1, 300);
		deploiement(milieu, "autre", "en_ligne", 1, 200);
		deploiement(recent, "autre", "en_ligne", 1, 100);

		List<String> ids = toutParcourir("");
		assertThat(ids).doesNotHaveDuplicates();
		assertThat(ids).containsSubsequence(recent.toString(), milieu.toString(), ancien.toString());
	}

	// ---------------------------------------------------------------- filtres

	@Test
	void le_filtre_hebergeur_garde_seulement_datacloud() throws Exception {
		UUID awa = utilisateur("awa", null, false);
		UUID chezSystalink = projet("Chez Systalink", "A.", "idee", awa);
		UUID ailleurs = projet("Ailleurs", "B.", "idee", awa);
		deploiement(chezSystalink, "datacloud", "en_ligne", 1, 10);
		deploiement(ailleurs, "autre", "en_ligne", 1, 10);

		List<String> ids = toutParcourir("&hebergeur=datacloud");
		assertThat(ids).contains(chezSystalink.toString()).doesNotContain(ailleurs.toString());
	}

	@Test
	void le_filtre_pays_garde_les_equipes_dont_un_membre_vient_de_ce_pays() throws Exception {
		UUID amina = utilisateur("amina", "GH", true);
		UUID kofi = utilisateur("kofi", "KE", true);
		UUID ghana = projet("Au Ghana", "A.", "idee", amina);
		UUID kenya = projet("Au Kenya", "B.", "idee", kofi);
		deploiement(ghana, "autre", "en_ligne", 1, 10);
		deploiement(kenya, "autre", "en_ligne", 1, 10);

		List<String> ids = toutParcourir("&pays=GH");
		assertThat(ids).contains(ghana.toString()).doesNotContain(kenya.toString());
	}

	@Test
	void le_pays_d_un_profil_prive_n_est_pas_revele() throws Exception {
		UUID discret = utilisateur("discret", "ML", false);
		UUID projet = projet("Discret", "A.", "idee", discret);
		deploiement(projet, "autre", "en_ligne", 1, 10);

		assertThat(element("/api/vitrine?limite=100", projet).get("paysEquipe")).isEmpty();
		assertThat(toutParcourir("&pays=ML")).doesNotContain(projet.toString());
	}

	@Test
	void des_parametres_invalides_repondent_400() throws Exception {
		assertThat(lire("/api/vitrine?hebergeur=inconnu")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(lire("/api/vitrine?pays=senegal")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(lire("/api/vitrine?limite=0")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(lire("/api/vitrine?limite=101")).hasStatus(HttpStatus.BAD_REQUEST);
		MvcTestResult curseur = lire("/api/vitrine?curseur=n-importe-quoi");
		assertThat(curseur).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(corps(curseur).get("code").asString()).isEqualTo("validation");
	}

}
