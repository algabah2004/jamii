package dev.jamii.api.auth;

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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import dev.jamii.api.TestcontainersConfiguration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** GET /api/me/export et DELETE /api/me (contracts/socle.yaml), sur un vrai PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MesDonneesTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient base;

	@Autowired
	JsonMapper json;

	private Instant t0 = Instant.now().minus(1, ChronoUnit.HOURS);

	// ---------------------------------------------------------------- aides

	private UtilisateurConnecte utilisateur(String nom) {
		UUID id = UUID.randomUUID();
		Timestamp t = Timestamp.from(Instant.now());
		base.sql("INSERT INTO utilisateur (id, nom, email, cree_le, mis_a_jour_le) VALUES (:id, :nom, :email, :t, :t)")
			.param("id", id).param("nom", nom).param("email", nom + "." + id + "@secret.example").param("t", t).update();
		base.sql("INSERT INTO utilisateur_role (utilisateur_id, role) VALUES (:id, 'membre')").param("id", id).update();
		return new UtilisateurConnecte(id, Set.of(Role.MEMBRE));
	}

	/** Un instant plus tard à chaque appel : l'ordre d'arrivée des membres est connu. */
	private Timestamp ensuite() {
		t0 = t0.plusSeconds(1);
		return Timestamp.from(t0);
	}

	private UUID projet(UtilisateurConnecte porteur, String titre, UtilisateurConnecte... membres) {
		UUID id = UUID.randomUUID();
		Timestamp t = ensuite();
		base.sql("""
				INSERT INTO projet (id, titre, origine, porteur_id, derniere_activite_le, cree_le, mis_a_jour_le)
				VALUES (:id, :titre, 'idee', :p, :t, :t, :t)
				""").param("id", id).param("titre", titre).param("p", porteur.id()).param("t", t).update();
		membre(id, porteur);
		for (UtilisateurConnecte m : membres) {
			membre(id, m);
		}
		return id;
	}

	private void membre(UUID projet, UtilisateurConnecte qui) {
		base.sql("INSERT INTO membre_equipe (projet_id, utilisateur_id, rejoint_le) VALUES (:p, :u, :t)")
			.param("p", projet).param("u", qui.id()).param("t", ensuite()).update();
	}

	private UUID cercle(UtilisateurConnecte createur, UtilisateurConnecte... membres) {
		UUID id = UUID.randomUUID();
		base.sql("INSERT INTO cercle (id, nom, ville, taille, createur_id, cree_le) VALUES (:id, 'Cercle de Thiès', 'Thiès', 4, :c, :t)")
			.param("id", id).param("c", createur.id()).param("t", ensuite()).update();
		for (UtilisateurConnecte m : java.util.stream.Stream.concat(java.util.stream.Stream.of(createur),
				java.util.stream.Stream.of(membres)).toList()) {
			base.sql("""
					INSERT INTO cercle_membre (cercle_id, utilisateur_id, rejoint_le, idee_titre, idee_description)
					VALUES (:c, :u, :t, 'Une idée', 'Une application pour les marchés de Thiès')
					""").param("c", id).param("u", m.id()).param("t", ensuite()).update();
		}
		return id;
	}

	private void demande(UtilisateurConnecte auteur) {
		base.sql("""
				INSERT INTO demande (id, auteur_id, titre, description, duree_semaines, cree_le)
				VALUES (:id, :a, 'Site pour ma boutique', 'Je vends des tissus à Dakar et je veux vendre en ligne.', 4, :t)
				""").param("id", UUID.randomUUID()).param("a", auteur.id()).param("t", ensuite()).update();
	}

	private void session(UtilisateurConnecte qui) {
		long maintenant = System.currentTimeMillis();
		String id = UUID.randomUUID().toString();
		base.sql("""
				INSERT INTO SPRING_SESSION (PRIMARY_ID, SESSION_ID, CREATION_TIME, LAST_ACCESS_TIME, MAX_INACTIVE_INTERVAL,
				                            EXPIRY_TIME, PRINCIPAL_NAME)
				VALUES (:id, :sid, :m, :m, 1800, :fin, :nom)
				""").param("id", id).param("sid", UUID.randomUUID().toString()).param("m", maintenant)
			.param("fin", maintenant + 1_800_000).param("nom", qui.id().toString()).update();
	}

	private long compter(String sql, Object id) {
		return base.sql(sql).param("id", id).query(Long.class).single();
	}

	private MvcTestResult exporter(UtilisateurConnecte qui) {
		return mvc.get().uri("/api/me/export").with(oauth2Login().oauth2User(qui)).exchange();
	}

	private MvcTestResult supprimer(UtilisateurConnecte qui) {
		return mvc.delete().uri("/api/me").with(csrf()).with(oauth2Login().oauth2User(qui)).exchange();
	}

	private JsonNode corps(MvcTestResult r) throws Exception {
		return json.readTree(r.getResponse().getContentAsString());
	}

	// ---------------------------------------------------------------- export

	@Test
	void l_export_rassemble_le_compte_et_chaque_module_en_piece_jointe() throws Exception {
		UtilisateurConnecte awa = utilisateur("Awa");
		UtilisateurConnecte kofi = utilisateur("Kofi");
		projet(awa, "Le projet d'Awa", kofi);
		projet(kofi, "Le projet de Kofi", awa);
		cercle(kofi, awa);
		demande(awa);

		MvcTestResult reponse = exporter(awa);

		assertThat(reponse).hasStatusOk();
		assertThat(reponse.getResponse().getHeader("Content-Disposition")).isEqualTo("attachment; filename=\"jamii-export.json\"");
		assertThat(reponse.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
		JsonNode export = corps(reponse);
		assertThat(export.get("compte").get("email").asString()).startsWith("Awa.");
		assertThat(export.get("projets").get("equipes")).hasSize(2);
		assertThat(export.get("projets").get("equipes").get(0).get("porteur").asBoolean()).isTrue();
		assertThat(export.get("projets").get("equipes").get(1).get("porteur").asBoolean()).isFalse();
		assertThat(export.get("cercles").get(0).get("ideeDescription").asString()).contains("Thiès");
		assertThat(export.get("cercles").get(0).get("createur").asBoolean()).isFalse();
		assertThat(export.get("demandes").get(0).get("dureeSemaines").asInt()).isEqualTo(4);
		assertThat(reponse.getResponse().getContentAsString()).as("aucune donnée personnelle de Kofi")
			.doesNotContain("Kofi.");
	}

	@Test
	void au_dela_de_cinq_exports_par_jour_429_avec_le_delai() {
		UtilisateurConnecte awa = utilisateur("Awa");
		for (int i = 0; i < MesDonneesController.EXPORTS_PAR_JOUR; i++) {
			assertThat(exporter(awa)).hasStatusOk();
		}

		MvcTestResult reponse = exporter(awa);

		assertThat(reponse).hasStatus(HttpStatus.TOO_MANY_REQUESTS).hasContentType("application/problem+json");
		assertThat(reponse).bodyJson().extractingPath("$.code").isEqualTo("trop_de_requetes");
		assertThat(Long.parseLong(reponse.getResponse().getHeader("Retry-After"))).isPositive();
		assertThat(exporter(utilisateur("Kofi"))).as("la limite est par compte").hasStatusOk();
	}

	@Test
	void sans_connexion_401() {
		assertThat(mvc.get().uri("/api/me/export")).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.delete().uri("/api/me").with(csrf())).hasStatus(HttpStatus.UNAUTHORIZED);
	}

	// ---------------------------------------------------------------- suppression

	@Test
	void supprimer_sans_jeton_csrf_est_refuse() {
		UtilisateurConnecte awa = utilisateur("Awa");

		assertThat(mvc.delete().uri("/api/me").with(oauth2Login().oauth2User(awa))).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(compter("SELECT count(*) FROM utilisateur WHERE id = :id", awa.id())).isEqualTo(1);
	}

	@Test
	void la_suppression_efface_le_compte_ses_donnees_et_toutes_ses_sessions() {
		UtilisateurConnecte awa = utilisateur("Awa");
		UtilisateurConnecte kofi = utilisateur("Kofi");
		demande(awa);
		UUID projetDeKofi = projet(kofi, "Le projet de Kofi", awa);
		session(awa);
		session(kofi);

		assertThat(supprimer(awa)).hasStatus(HttpStatus.ACCEPTED);

		assertThat(compter("SELECT count(*) FROM utilisateur WHERE id = :id", awa.id())).isZero();
		assertThat(compter("SELECT count(*) FROM utilisateur_role WHERE utilisateur_id = :id", awa.id())).isZero();
		assertThat(compter("SELECT count(*) FROM demande WHERE auteur_id = :id", awa.id())).isZero();
		assertThat(compter("SELECT count(*) FROM membre_equipe WHERE utilisateur_id = :id", awa.id())).isZero();
		assertThat(compter("SELECT count(*) FROM SPRING_SESSION WHERE PRINCIPAL_NAME = :id", awa.id().toString())).isZero();
		assertThat(compter("SELECT count(*) FROM SPRING_SESSION WHERE PRINCIPAL_NAME = :id", kofi.id().toString()))
			.as("les sessions des autres restent").isEqualTo(1);
		assertThat(compter("SELECT count(*) FROM projet WHERE id = :id", projetDeKofi)).isEqualTo(1);
	}

	@Test
	void ses_projets_passent_au_membre_arrive_le_plus_tot_et_un_projet_sans_equipe_disparait() {
		UtilisateurConnecte awa = utilisateur("Awa");
		UtilisateurConnecte kofi = utilisateur("Kofi");
		UtilisateurConnecte zola = utilisateur("Zola");
		UUID avecEquipe = projet(awa, "Un projet avec une équipe", kofi, zola);
		UUID seule = projet(awa, "Un projet qu'Awa porte seule");
		UUID partiSeulement = projet(awa, "Un projet dont l'équipe est partie", zola);
		base.sql("UPDATE membre_equipe SET parti_le = now() WHERE projet_id = :p AND utilisateur_id = :u")
			.param("p", partiSeulement).param("u", zola.id()).update();

		assertThat(supprimer(awa)).hasStatus(HttpStatus.ACCEPTED);

		assertThat(base.sql("SELECT porteur_id FROM projet WHERE id = :id").param("id", avecEquipe).query(UUID.class).single())
			.isEqualTo(kofi.id());
		assertThat(compter("SELECT count(*) FROM projet WHERE id = :id", seule)).isZero();
		assertThat(compter("SELECT count(*) FROM projet WHERE id = :id", partiSeulement))
			.as("un membre parti ne reprend pas le projet").isZero();
	}

	@Test
	void ses_cercles_passent_au_membre_arrive_le_plus_tot_et_un_cercle_ou_il_etait_seul_disparait() {
		UtilisateurConnecte awa = utilisateur("Awa");
		UtilisateurConnecte kofi = utilisateur("Kofi");
		UtilisateurConnecte zola = utilisateur("Zola");
		UUID partage = cercle(awa, kofi, zola);
		UUID seul = cercle(awa);

		assertThat(supprimer(awa)).hasStatus(HttpStatus.ACCEPTED);

		assertThat(base.sql("SELECT createur_id FROM cercle WHERE id = :id").param("id", partage).query(UUID.class).single())
			.isEqualTo(kofi.id());
		assertThat(compter("SELECT count(*) FROM cercle_membre WHERE cercle_id = :id", partage)).isEqualTo(2);
		assertThat(compter("SELECT count(*) FROM cercle WHERE id = :id", seul)).isZero();
	}

	@Test
	void supprimer_deux_fois_ne_casse_rien() {
		UtilisateurConnecte awa = utilisateur("Awa");

		assertThat(supprimer(awa)).hasStatus(HttpStatus.ACCEPTED);
		assertThat(supprimer(awa)).hasStatus(HttpStatus.ACCEPTED);
	}

}
