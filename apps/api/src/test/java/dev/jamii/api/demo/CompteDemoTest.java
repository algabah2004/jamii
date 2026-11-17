package dev.jamii.api.demo;

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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import dev.jamii.api.TestcontainersConfiguration;
import dev.jamii.api.auth.Role;
import dev.jamii.api.auth.UtilisateurConnecte;
import dev.jamii.api.projets.Projets;

/** Le compte démo (#157) : ouvert à tout visiteur, rempli au démarrage, remis à zéro, et sans danger pour les autres. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@Import(TestcontainersConfiguration.class)
class CompteDemoTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient base;

	@Autowired
	Projets projets;

	private final UtilisateurConnecte demo = new UtilisateurConnecte(CompteDemo.ID, Set.of(Role.MEMBRE));

	private long compter(String sql) {
		return base.sql(sql).param("moi", CompteDemo.ID).query(Long.class).single();
	}

	/** Ce que fait le démarrage de l'API avec le profil demo. */
	private void redemarrer() {
		var compte = new CompteDemo(base, Instant.now());
		compte.reinitialiser();
		compte.creer();
		new DemoProfils(base, Instant.now()).charger();
		DemoMonCompte.pourCompteDemo(base, projets, Instant.now()).completer();
	}

	@Test
	void tout_visiteur_entre_dans_le_compte_demo_d_un_clic() {
		assertThat(mvc.get().uri("/api/auth/fournisseurs")).hasStatusOk().bodyJson().extractingPath("$.demo").isEqualTo(true);
		assertThat(mvc.post().uri("/api/auth/demo")).hasStatus(HttpStatus.FORBIDDEN);

		var entree = mvc.post().uri("/api/auth/demo").with(csrf()).exchange();
		assertThat(entree).hasStatus(HttpStatus.NO_CONTENT);
		// Le cookie s'appelle JAMII_SESSION en production, SESSION dans les tests.
		assertThat(entree.getResponse().getHeader("Set-Cookie")).contains("SESSION=");
		assertThat(base.sql("SELECT count(*) FROM SPRING_SESSION WHERE PRINCIPAL_NAME = :p").param("p", CompteDemo.ID.toString())
			.query(Long.class).single()).isPositive();
	}

	@Test
	void le_compte_demo_a_tout_le_parcours_sans_toucher_a_celui_du_vrai_compte() {
		var cercle = mvc.get().uri("/api/cercles/" + DemoMonCompte.CERCLE_DEMO).with(oauth2Login().oauth2User(demo));
		assertThat(cercle).hasStatusOk();
		assertThat(cercle).bodyJson().extractingPath("$.nom").isEqualTo("Cercle des pionniers");
		assertThat(cercle).bodyJson().extractingPath("$.tours[1].beneficiaire.id").isEqualTo(CompteDemo.ID.toString());
		assertThat(compter("SELECT count(*) FROM profil WHERE utilisateur_id = :moi AND publie")).isEqualTo(1);
		assertThat(compter("SELECT count(*) FROM adhesion WHERE utilisateur_id = :moi")).isEqualTo(2);
		assertThat(compter("SELECT count(*) FROM contact WHERE :moi IN (demandeur_id, destinataire_id)")).isEqualTo(5);
		assertThat(compter("SELECT count(*) FROM inscription WHERE utilisateur_id = :moi AND equipe_id IS NOT NULL")).isEqualTo(1);
		assertThat(compter("SELECT count(*) FROM proposition WHERE mission_id = '" + DemoMonCompte.MISSION_DEMO + "' AND :moi IS NOT NULL"))
			.isEqualTo(2);
		// Le cercle du vrai compte (JAMII_DEMO_COMPTE) n'est pas créé par le compte démo.
		assertThat(base.sql("SELECT count(*) FROM cercle WHERE id = :c").param("c", DemoMonCompte.CERCLE).query(Long.class).single()).isZero();
	}

	@Test
	void au_redemarrage_ce_qu_un_visiteur_a_fait_disparait_et_le_parcours_revient() {
		var visiteur = oauth2Login().oauth2User(demo);
		assertThat(mvc.post().uri("/api/cercles").contentType(MediaType.APPLICATION_JSON).content("{\"nom\":\"Le cercle d'un visiteur\",\"taille\":4}")
			.with(csrf()).with(visiteur)).hasStatus(HttpStatus.CREATED);
		assertThat(mvc.post().uri("/api/conversations/" + DonneesDemo.KOFI.id() + "/messages").contentType(MediaType.APPLICATION_JSON)
			.content("{\"texte\":\"Bonjour Kofi\"}").with(csrf()).with(visiteur)).hasStatus(HttpStatus.CREATED);

		redemarrer();

		assertThat(compter("SELECT count(*) FROM cercle WHERE createur_id = :moi")).isEqualTo(1);
		assertThat(compter("SELECT count(*) FROM cercle WHERE nom = 'Le cercle d''un visiteur' AND :moi IS NOT NULL")).isZero();
		assertThat(compter("SELECT count(*) FROM message WHERE :moi IN (expediteur_id, destinataire_id)")).isEqualTo(3);
		assertThat(compter("SELECT count(*) FROM cercle_membre WHERE utilisateur_id = :moi")).isEqualTo(1);
		assertThat(compter("SELECT count(*) FROM inscription WHERE utilisateur_id = :moi AND equipe_id IS NOT NULL")).isEqualTo(1);
		// Les projets des tours de l'ancien cercle ne restent pas orphelins dans les profils.
		assertThat(compter("SELECT count(*) FROM projet WHERE titre = 'Le prochain matatu' AND porteur_id = '" + DonneesDemo.NIA.id()
				+ "' AND id NOT IN (SELECT projet_id FROM cercle_membre WHERE projet_id IS NOT NULL) AND :moi IS NOT NULL")).isZero();
	}

	@Test
	void le_compte_demo_n_ecrit_qu_aux_personnes_de_la_demo_et_ne_se_supprime_pas() {
		var visiteur = oauth2Login().oauth2User(demo);
		UUID vrai = UUID.randomUUID();
		Timestamp t = Timestamp.from(Instant.now());
		base.sql("INSERT INTO utilisateur (id, nom, email, cree_le, mis_a_jour_le) VALUES (:id, 'Vraie personne', :e, :t, :t)")
			.param("id", vrai).param("e", vrai + "@exemple.test").param("t", t).update();

		assertThat(mvc.post().uri("/api/conversations/" + vrai + "/messages").contentType(MediaType.APPLICATION_JSON)
			.content("{\"texte\":\"Bonjour\"}").with(csrf()).with(visiteur)).hasStatus(HttpStatus.FORBIDDEN)
			.bodyJson().extractingPath("$.code").isEqualTo("compte_demo");
		assertThat(mvc.put().uri("/api/contacts/" + vrai).with(csrf()).with(visiteur)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(mvc.delete().uri("/api/me").with(csrf()).with(visiteur)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(compter("SELECT count(*) FROM utilisateur WHERE id = :moi")).isEqualTo(1);
		// Un vrai compte, lui, écrit à qui il veut.
		assertThat(mvc.post().uri("/api/conversations/" + DonneesDemo.KOFI.id() + "/messages").contentType(MediaType.APPLICATION_JSON)
			.content("{\"texte\":\"Bonjour Kofi\"}").with(csrf()).with(oauth2Login().oauth2User(new UtilisateurConnecte(vrai, Set.of(Role.MEMBRE)))))
			.hasStatus(HttpStatus.CREATED);
	}

}
