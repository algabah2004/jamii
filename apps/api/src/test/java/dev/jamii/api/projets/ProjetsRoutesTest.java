package dev.jamii.api.projets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;

import java.sql.Timestamp;
import java.time.Instant;
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

/** POST /api/projets et GET /api/projets/{projetId}, sur un vrai PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ProjetsRoutesTest {

	private static final String VALIDE = """
			{"titre":"Application de caisse hors ligne","description":"Une caisse qui marche sans internet pour les marchés.","origine":"idee"}""";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient base;

	private UtilisateurConnecte utilisateur(String nom, String email) {
		UUID id = UUID.randomUUID();
		Timestamp t = Timestamp.from(Instant.now());
		base.sql("INSERT INTO utilisateur (id, nom, email, avatar_url, cree_le, mis_a_jour_le) VALUES (:id, :nom, :email, 'https://avatars.example/x', :t, :t)")
			.param("id", id).param("nom", nom).param("email", email).param("t", t).update();
		return new UtilisateurConnecte(id, Set.of(Role.MEMBRE));
	}

	private MvcTestResult creer(UtilisateurConnecte qui, String corps) {
		return mvc.post().uri("/api/projets").contentType(MediaType.APPLICATION_JSON).content(corps).with(csrf())
			.with(oauth2Login().oauth2User(qui)).exchange();
	}

	private MvcTestResult lire(UtilisateurConnecte qui, Object id) {
		return mvc.get().uri("/api/projets/{id}", id).with(oauth2Login().oauth2User(qui)).exchange();
	}

	@Test
	void creer_un_projet_fait_de_moi_le_porteur_et_le_premier_membre() {
		UtilisateurConnecte awa = utilisateur("Awa Diallo", "awa.secret." + UUID.randomUUID() + "@example.org");

		MvcTestResult reponse = creer(awa, VALIDE);

		assertThat(reponse).hasStatus(HttpStatus.CREATED);
		assertThat(reponse).bodyJson().extractingPath("$.statut").isEqualTo("recrutement");
		assertThat(reponse).bodyJson().extractingPath("$.origine").isEqualTo("idee");
		assertThat(reponse).bodyJson().extractingPath("$.porteur.nom").isEqualTo("Awa Diallo");
		assertThat(reponse).bodyJson().extractingPath("$.nombreMembres").isEqualTo(1);
		assertThat(reponse).bodyJson().extractingPath("$.equipe[0].estPorteur").isEqualTo(true);
		assertThat(reponse).bodyJson().extractingPath("$.moi.estPorteur").isEqualTo(true);
		assertThat(reponse).bodyJson().extractingPath("$.moi.estMembre").isEqualTo(true);
		assertThat(reponse).bodyJson().extractingPath("$.version").isEqualTo(0);
		assertThat(reponse).bodyJson().extractingPath("$.roles").isEqualTo(java.util.List.of());
		String id = (String) reponse.getResponse().getHeader("Location").substring("/api/projets/".length());
		assertThat(UUID.fromString(id)).isNotNull();
		assertThat(base.sql("SELECT count(*) FROM membre_equipe WHERE projet_id = :p AND utilisateur_id = :u AND parti_le IS NULL")
			.param("p", UUID.fromString(id)).param("u", awa.id()).query(Long.class).single()).isEqualTo(1);
	}

	@Test
	void la_reponse_ne_montre_jamais_l_email() throws Exception {
		UtilisateurConnecte awa = utilisateur("Awa", "awa.secret." + UUID.randomUUID() + "@example.org");

		MvcTestResult reponse = creer(awa, VALIDE);

		assertThat(reponse.getResponse().getContentAsString()).doesNotContain("awa.secret").doesNotContain("@example.org");
	}

	@Test
	void un_autre_utilisateur_connecte_lit_le_projet_sans_etre_membre() {
		UtilisateurConnecte awa = utilisateur("Awa", null);
		UtilisateurConnecte kofi = utilisateur("Kofi", null);
		String id = idCree(creer(awa, VALIDE));

		MvcTestResult reponse = lire(kofi, id);

		assertThat(reponse).hasStatusOk();
		assertThat(reponse).bodyJson().extractingPath("$.moi.estMembre").isEqualTo(false);
		assertThat(reponse).bodyJson().extractingPath("$.moi.estPorteur").isEqualTo(false);
		assertThat(reponse).bodyJson().extractingPath("$.titre").isEqualTo("Application de caisse hors ligne");
	}

	@Test
	void un_membre_parti_n_apparait_plus_dans_l_equipe() {
		UtilisateurConnecte awa = utilisateur("Awa", null);
		UtilisateurConnecte kofi = utilisateur("Kofi", null);
		String id = idCree(creer(awa, VALIDE));
		base.sql("INSERT INTO membre_equipe (projet_id, utilisateur_id, rejoint_le, parti_le) VALUES (:p, :u, now(), now())")
			.param("p", UUID.fromString(id)).param("u", kofi.id()).update();

		MvcTestResult reponse = lire(awa, id);

		assertThat(reponse).bodyJson().extractingPath("$.nombreMembres").isEqualTo(1);
		assertThat(reponse).bodyJson().extractingPath("$.equipe.length()").isEqualTo(1);
	}

	@Test
	void projet_inconnu_repond_404_au_format_du_contrat() {
		MvcTestResult reponse = lire(utilisateur("Awa", null), UUID.randomUUID());

		assertThat(reponse).hasStatus(HttpStatus.NOT_FOUND).hasContentType("application/problem+json");
		assertThat(reponse).bodyJson().extractingPath("$.code").isEqualTo("introuvable");
	}

	@Test
	void sans_connexion_les_deux_routes_repondent_401() {
		assertThat(mvc.get().uri("/api/projets/{id}", UUID.randomUUID())).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.post().uri("/api/projets").contentType(MediaType.APPLICATION_JSON).content(VALIDE).with(csrf()))
			.hasStatus(HttpStatus.UNAUTHORIZED);
	}

	@Test
	void sans_jeton_csrf_la_creation_est_refusee() {
		assertThat(mvc.post().uri("/api/projets").contentType(MediaType.APPLICATION_JSON).content(VALIDE)
			.with(oauth2Login().oauth2User(utilisateur("Awa", null)))).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void champs_invalides_repondent_400_avec_le_detail() {
		UtilisateurConnecte awa = utilisateur("Awa", null);
		String[][] cas = {
				{ "{\"titre\":\"ab\",\"description\":\"Une description assez longue\",\"origine\":\"idee\"}", "titre" },
				{ "{\"titre\":\"Un titre\",\"description\":\"court\",\"origine\":\"idee\"}", "description" },
				{ "{\"titre\":\"Un titre\",\"description\":\"Une description assez longue\",\"origine\":\"autre\"}", "origine" },
				{ "{\"titre\":\"Un titre\",\"description\":\"Une description assez longue\",\"origine\":\"idee\",\"demandeId\":\""
						+ UUID.randomUUID() + "\"}", "demandeId" },
				{ "{\"titre\":\"Un titre\",\"description\":\"Une description assez longue\",\"origine\":\"demande\"}", "demandeId" },
				{ "{\"titre\":\"" + "x".repeat(121) + "\",\"description\":\"Une description assez longue\",\"origine\":\"idee\"}", "titre" } };
		for (String[] c : cas) {
			MvcTestResult reponse = creer(awa, c[0]);
			assertThat(reponse).as(c[1]).hasStatus(HttpStatus.BAD_REQUEST);
			assertThat(reponse).bodyJson().extractingPath("$.code").isEqualTo("validation");
			assertThat(reponse).bodyJson().extractingPath("$.erreurs[0].champ").isEqualTo(c[1]);
		}
	}

	@Test
	void une_demande_inconnue_repond_404() {
		String corps = "{\"titre\":\"Un titre\",\"description\":\"Une description assez longue\",\"origine\":\"demande\",\"demandeId\":\""
				+ UUID.randomUUID() + "\"}";

		assertThat(creer(utilisateur("Awa", null), corps)).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void le_projet_cree_donne_acces_membre_au_porteur_et_lecteur_aux_autres() {
		UtilisateurConnecte awa = utilisateur("Awa", null);
		UtilisateurConnecte kofi = utilisateur("Kofi", null);
		UUID id = UUID.fromString(idCree(creer(awa, VALIDE)));

		assertThat(base.sql("SELECT count(*) FROM projet WHERE id = :id AND porteur_id = :p").param("id", id)
			.param("p", awa.id()).query(Long.class).single()).isEqualTo(1);
		assertThat(lire(kofi, id)).hasStatusOk();
	}

	private static String idCree(MvcTestResult reponse) {
		assertThat(reponse).hasStatus(HttpStatus.CREATED);
		return reponse.getResponse().getHeader("Location").substring("/api/projets/".length());
	}

}
