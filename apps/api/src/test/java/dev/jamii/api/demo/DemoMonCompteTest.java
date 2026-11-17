package dev.jamii.api.demo;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.transaction.annotation.Transactional;

import dev.jamii.api.TestcontainersConfiguration;
import dev.jamii.api.auth.Role;
import dev.jamii.api.auth.UtilisateurConnecte;
import dev.jamii.api.projets.Projets;

/** `JAMII_DEMO_COMPTE` : un vrai compte rejoint la démo, lisible par les vraies routes de l'API. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@Import(TestcontainersConfiguration.class)
@Transactional
class DemoMonCompteTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient base;

	@Autowired
	Projets projets;

	private UUID compte(String login) {
		UUID id = UUID.randomUUID();
		Timestamp t = Timestamp.from(Instant.now());
		base.sql("INSERT INTO utilisateur (id, nom, email, login_github, cree_le, mis_a_jour_le) VALUES (:id, 'Fadel', :e, :l, :t, :t)")
			.param("id", id).param("e", id + "@exemple.test").param("l", login).param("t", t).update();
		base.sql("INSERT INTO utilisateur_role (utilisateur_id, role) VALUES (:id, 'membre')").param("id", id).update();
		return id;
	}

	private long compter(String sql, UUID moi) {
		return base.sql(sql).param("moi", moi).query(Long.class).single();
	}

	@Test
	void le_compte_porte_le_tour_en_cours_de_son_cercle_avec_un_calendrier() {
		UUID moi = compte("Fadel-Demo");
		assertThat(new DemoMonCompte(base, projets, Instant.now(), "fadel-demo").charger()).isTrue();
		var connecte = oauth2Login().oauth2User(new UtilisateurConnecte(moi, Set.of(Role.MEMBRE)));

		var cercle = mvc.get().uri("/api/cercles/" + DemoMonCompte.CERCLE).with(connecte);
		assertThat(cercle).hasStatusOk();
		assertThat(cercle).bodyJson().extractingPath("$.statut").isEqualTo("en_cours");
		assertThat(cercle).bodyJson().extractingPath("$.tourEnCours").isEqualTo(2);
		assertThat(cercle).bodyJson().extractingPath("$.tours[0].livreLe").isNotNull();
		assertThat(cercle).bodyJson().extractingPath("$.tours[1].beneficiaire.id").isEqualTo(moi.toString());

		UUID jamii = base.sql("SELECT projet_id FROM cercle_membre WHERE cercle_id = :c AND rang = 2").param("c", DemoMonCompte.CERCLE)
			.query(UUID.class).single();
		assertThat(mvc.get().uri("/api/projets/" + jamii + "/jalons").with(connecte)).hasStatusOk().bodyJson()
			.extractingPath("$.length()").isEqualTo(3);
		var taches = mvc.get().uri("/api/projets/" + jamii + "/taches?limite=50").with(connecte);
		assertThat(taches).hasStatusOk();
		assertThat(taches).bodyJson().extractingPath("$.elements.length()").isEqualTo(11);

		assertThat(mvc.get().uri("/api/cercles/" + DemoMonCompte.CERCLE + "/salon?limite=100").with(connecte)).hasStatusOk();
		assertThat(compter("SELECT count(*) FROM message_cercle WHERE cercle_id = '" + DemoMonCompte.CERCLE + "' AND type = 'evenement' AND :moi IS NOT NULL", moi))
			.isGreaterThanOrEqualTo(5);

		assertThat(mvc.get().uri("/api/conversations/" + DonneesDemo.KOFI.id() + "/messages").with(connecte)).hasStatusOk();
		assertThat(compter("SELECT count(*) FROM contact WHERE destinataire_id = :moi AND NOT accepte", moi)).isEqualTo(1);
		assertThat(compter("SELECT count(*) FROM adhesion WHERE utilisateur_id = :moi", moi)).isEqualTo(2);
		assertThat(compter("SELECT count(*) FROM publication WHERE auteur_id = :moi AND projet_id IS NOT NULL", moi)).isEqualTo(1);
		assertThat(compter("SELECT count(*) FROM inscription WHERE utilisateur_id = :moi AND equipe_id IS NOT NULL", moi)).isEqualTo(1);

		var mission = mvc.get().uri("/api/missions/" + DemoMonCompte.MISSION).with(connecte);
		assertThat(mission).hasStatusOk();
		assertThat(compter("SELECT count(*) FROM proposition WHERE mission_id = '" + DemoMonCompte.MISSION + "' AND :moi IS NOT NULL", moi))
			.isEqualTo(2);
	}

	@Test
	void rejouer_ne_cree_pas_de_doublon() {
		UUID moi = compte("fadel-rejoue");
		new DemoMonCompte(base, projets, Instant.now(), "fadel-rejoue").charger();
		new DemoMonCompte(base, projets, Instant.now(), "fadel-rejoue").charger();

		assertThat(compter("SELECT count(*) FROM cercle_membre WHERE utilisateur_id = :moi", moi)).isEqualTo(1);
		assertThat(compter("SELECT count(*) FROM message WHERE :moi IN (expediteur_id, destinataire_id)", moi)).isEqualTo(3);
		assertThat(compter("SELECT count(*) FROM contact WHERE :moi IN (demandeur_id, destinataire_id)", moi)).isEqualTo(5);
	}

	@Test
	void sans_login_ou_sans_compte_rien_ne_change() {
		assertThat(new DemoMonCompte(base, projets, Instant.now(), "").charger()).isFalse();
		assertThat(new DemoMonCompte(base, projets, Instant.now(), "personne-inconnue").charger()).isFalse();
		assertThat(base.sql("SELECT count(*) FROM cercle WHERE id = :c").param("c", DemoMonCompte.CERCLE).query(Long.class).single())
			.isZero();
	}

}
