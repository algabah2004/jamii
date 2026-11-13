package dev.jamii.api.demandes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import dev.jamii.api.TestcontainersConfiguration;
import dev.jamii.api.auth.Role;
import dev.jamii.api.auth.UtilisateurConnecte;
import dev.jamii.api.profils.ProfilsPublies;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Ce que Systalink reçoit : tendances anonymes, clients suivis avec accord, CSV et webhook signé. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "jamii.clients-systalink.acces=equipe-systalink, partenaire@systalink.example")
class InfosSystalinkTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient base;

	@Autowired
	JsonMapper json;

	@Autowired
	InfosSystalink infos;

	@Autowired
	ProfilsPublies profils;

	private UtilisateurConnecte utilisateur(String nom, String email, String login, Role... roles) {
		UUID id = UUID.randomUUID();
		Timestamp t = Timestamp.from(Instant.now());
		base.sql("""
				INSERT INTO utilisateur (id, nom, email, login_github, cree_le, mis_a_jour_le)
				VALUES (:id, :nom, :email, :login, :t, :t)
				""").param("id", id).param("nom", nom).param("email", email).param("login", login).param("t", t).update();
		return new UtilisateurConnecte(id, roles.length == 0 ? Set.of(Role.MEMBRE) : Set.of(roles));
	}

	private UUID demande(UtilisateurConnecte auteur, String secteur, boolean accord, String resume) {
		UUID id = UUID.randomUUID();
		var noeud = json.createObjectNode().put("resume", resume).put("secteur", secteur).put("developpeursNecessaires", true);
		noeud.putArray("services").add("orion").add("datacloud-stockage");
		noeud.putArray("competences").add("react");
		String orientation = json.writeValueAsString(noeud);
		base.sql("""
				INSERT INTO demande (id, auteur_id, titre, description, ville, pays, offres_systalink, orientation, cree_le)
				VALUES (:id, :a, 'Un besoin', 'Une description assez longue du besoin.', 'Dakar', 'SN', :accord,
				  CAST(:o AS jsonb), :t)
				""").param("id", id).param("a", auteur.id()).param("accord", accord).param("o", orientation)
			.param("t", Timestamp.from(Instant.now())).update();
		return id;
	}

	private JsonNode corps(MvcTestResult reponse) throws Exception {
		return json.readTree(reponse.getResponse().getContentAsString());
	}

	@Test
	void les_tendances_sont_publiques_et_regroupent_les_petits_secteurs() throws Exception {
		String frequent = "sante-" + UUID.randomUUID().toString().substring(0, 6);
		String rare = "rare-" + UUID.randomUUID().toString().substring(0, 6);
		UtilisateurConnecte auteur = utilisateur("Auteur", "auteur." + UUID.randomUUID() + "@ex.sn", null);
		for (int i = 0; i < InfosSystalink.SEUIL; i++) {
			demande(auteur, frequent, false, "Besoin " + i);
		}
		demande(auteur, rare, false, "Besoin rare");

		MvcTestResult reponse = mvc.get().uri("/api/systalink/tendances?periode=semaine").exchange();

		assertThat(reponse).hasStatusOk();
		String texte = reponse.getResponse().getContentAsString();
		assertThat(texte).contains(frequent).doesNotContain(rare).doesNotContain("Besoin rare")
			.as("aucune donnée personnelle").doesNotContain("@ex.sn");
		JsonNode tendances = corps(reponse);
		assertThat(tendances.get("services").toString()).contains("orion", "datacloud-stockage");
		assertThat(tendances.get("competencesManquantes").toString()).contains("react");
		assertThat(mvc.get().uri("/api/systalink/tendances?periode=annee").exchange()).hasStatus(HttpStatus.BAD_REQUEST);
	}

	@Test
	void seuls_systalink_et_les_admins_voient_les_clients_suivis_et_seulement_avec_accord() throws Exception {
		UtilisateurConnecte awa = utilisateur("Awa", "awa." + UUID.randomUUID() + "@ex.sn", null);
		UUID avecAccord = demande(awa, "juridique", true, "Un cabinet veut suivre ses dossiers");
		UUID sansAccord = demande(awa, "juridique", false, "Pas d'accord");
		UtilisateurConnecte curieux = utilisateur("Curieux", "c." + UUID.randomUUID() + "@ex.sn", "curieux");
		UtilisateurConnecte systalink = utilisateur("Systalink", "s." + UUID.randomUUID() + "@ex.sn", "equipe-systalink");
		UtilisateurConnecte admin = utilisateur("Admin", "a." + UUID.randomUUID() + "@ex.sn", null, Role.ADMIN);

		assertThat(mvc.get().uri("/api/systalink/clients-suivis").with(oauth2Login().oauth2User(curieux)).exchange())
			.hasStatus(HttpStatus.FORBIDDEN);
		assertThat(mvc.get().uri("/api/systalink/clients-suivis").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.get().uri("/api/systalink/clients-suivis").with(oauth2Login().oauth2User(admin)).exchange()).hasStatusOk();

		JsonNode page = corps(mvc.get().uri("/api/systalink/clients-suivis?limite=100")
			.with(oauth2Login().oauth2User(systalink)).exchange());
		String liste = page.get("elements").toString();
		assertThat(liste).contains(avecAccord.toString(), "Awa").doesNotContain(sansAccord.toString());
		JsonNode client = null;
		for (JsonNode element : page.get("elements")) {
			if (element.get("demandeId").asString().equals(avecAccord.toString())) {
				client = element;
			}
		}
		assertThat(client.at("/contact/email").asString()).startsWith("awa.");
		assertThat(client.get("statut").asString()).isEqualTo("nouveau");

		MvcTestResult suivi = mvc.patch().uri("/api/systalink/clients-suivis/{id}", avecAccord)
			.contentType(MediaType.APPLICATION_JSON).content("{\"statut\":\"contacte\"}").with(csrf())
			.with(oauth2Login().oauth2User(systalink)).exchange();
		assertThat(suivi).bodyJson().extractingPath("$.statut").isEqualTo("contacte");
		assertThat(mvc.patch().uri("/api/systalink/clients-suivis/{id}", sansAccord).contentType(MediaType.APPLICATION_JSON)
			.content("{\"statut\":\"contacte\"}").with(csrf()).with(oauth2Login().oauth2User(systalink)).exchange())
			.hasStatus(HttpStatus.NOT_FOUND);

		// Retirer l'accord fait disparaître la personne tout de suite.
		mvc.put().uri("/api/demandes/{id}/offres-systalink", avecAccord).contentType(MediaType.APPLICATION_JSON)
			.content("{\"accepte\":false}").with(csrf()).with(oauth2Login().oauth2User(awa)).exchange();
		assertThat(corps(mvc.get().uri("/api/systalink/clients-suivis?limite=100").with(oauth2Login().oauth2User(systalink))
			.exchange()).toString()).doesNotContain(avecAccord.toString());
	}

	@Test
	void l_acces_se_donne_aussi_par_e_mail() {
		UtilisateurConnecte partenaire = utilisateur("Partenaire", "Partenaire@Systalink.example", null);
		assertThat(infos.aAcces(partenaire)).isTrue();
	}

	@Test
	void le_csv_neutralise_les_formules_et_echappe_les_guillemets() {
		var client = new InfosSystalink.ClientSuivi(UUID.randomUUID(), Instant.parse("2026-11-13T10:00:00Z"),
				"=HYPERLINK(\"http://pirate\")", "juridique", "Dakar", "SN", List.of("orion"), true,
				new InfosSystalink.Contact("Awa \"la couturière\"", null), "nouveau");

		String csv = InfosSystalink.csv(List.of(client));

		assertThat(csv).startsWith("demandeId,creeLe,resume");
		assertThat(csv).contains("\"'=HYPERLINK(\"\"http://pirate\"\")\"").contains("\"Awa \"\"la couturière\"\"\"");
	}

	@Test
	void le_webhook_est_signe_et_part_une_seule_fois() throws Exception {
		List<String[]> recus = new CopyOnWriteArrayList<>();
		HttpServer serveur = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		serveur.createContext("/", echange -> {
			recus.add(new String[] { echange.getRequestHeaders().getFirst("X-Jamii-Horodatage"),
					echange.getRequestHeaders().getFirst("X-Jamii-Signature"),
					new String(echange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8) });
			echange.sendResponseHeaders(204, -1);
			echange.close();
		});
		serveur.start();
		try {
			String cleDeTest = UUID.randomUUID().toString();
			var horloge = new org.springframework.beans.factory.support.StaticListableBeanFactory();
			horloge.addBean("horloge", Clock.fixed(Instant.parse("2026-11-13T10:00:00Z"), ZoneOffset.UTC));
			var webhook = new WebhookSystalink(base, infos, json, horloge.getBeanProvider(Clock.class),
					"http://127.0.0.1:" + serveur.getAddress().getPort() + "/jamii", cleDeTest);
			UtilisateurConnecte awa = utilisateur("Awa", "awa." + UUID.randomUUID() + "@ex.sn", null);
			UUID id = demande(awa, "commerce", true, "Une boutique veut vendre en ligne");

			webhook.signalerSiPret(id);
			webhook.signalerSiPret(id);
			long limite = System.currentTimeMillis() + 5000;
			while (recus.isEmpty() && System.currentTimeMillis() < limite) {
				Thread.sleep(50);
			}
			Thread.sleep(200);

			assertThat(recus).as("une seule fois").hasSize(1);
			String[] recu = recus.getFirst();
			assertThat(recu[0]).isEqualTo("1794564000");
			assertThat(recu[1]).isEqualTo("sha256=" + webhook.signer(recu[0] + "." + recu[2]));
			assertThat(recu[2]).contains(id.toString(), "Une boutique veut vendre en ligne");
		}
		finally {
			serveur.stop(0);
		}
	}

}
