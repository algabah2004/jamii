package dev.jamii.api.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import dev.jamii.api.TestcontainersConfiguration;
import dev.jamii.api.auth.Annuaire;
import dev.jamii.api.auth.Role;
import dev.jamii.api.auth.UtilisateurConnecte;

/** Un compte démo par visiteur (#157) : rempli, à lui seul, sans gonfler les profils des autres, effacé après 24 heures. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("demo")
@Import(TestcontainersConfiguration.class)
class VisiteursDemoTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient base;

	@Autowired
	VisiteursDemo visiteurs;

	/** Les autres classes de test comptent les comptes de la démo : on ne leur laisse aucun visiteur. */
	@AfterEach
	void effacerLesVisiteurs() {
		base.sql("SELECT id FROM utilisateur WHERE email LIKE :v").param("v", Annuaire.VISITEURS_DEMO).query(UUID.class).list()
			.forEach(new CompteDemo(base)::effacer);
	}

	private long compter(String sql, UUID moi) {
		return base.sql(sql).param("moi", moi).query(Long.class).single();
	}

	private long visiteursPresents() {
		return base.sql("SELECT count(*) FROM utilisateur WHERE email LIKE :v").param("v", Annuaire.VISITEURS_DEMO).query(Long.class).single();
	}

	private UUID nouveauVisiteur() {
		return visiteurs.creer(Instant.now()).orElseThrow();
	}

	private static RequestPostProcessor depuis(String ip) {
		return requete -> {
			requete.setRemoteAddr(ip);
			return requete;
		};
	}

	private static RequestPostProcessor connecte(UUID id) {
		return oauth2Login().oauth2User(new UtilisateurConnecte(id, Set.of(Role.MEMBRE)));
	}

	@Test
	void chaque_visiteur_entre_dans_son_propre_compte_demo_d_un_clic() {
		assertThat(mvc.get().uri("/api/auth/fournisseurs")).hasStatusOk().bodyJson().extractingPath("$.demo").isEqualTo(true);
		assertThat(mvc.post().uri("/api/auth/demo").with(depuis("10.0.0.1"))).hasStatus(HttpStatus.FORBIDDEN);

		var premier = mvc.post().uri("/api/auth/demo").with(csrf()).with(depuis("10.0.0.1")).exchange();
		assertThat(premier).hasStatus(HttpStatus.NO_CONTENT);
		// Le cookie s'appelle JAMII_SESSION en production, SESSION dans les tests.
		assertThat(premier.getResponse().getHeader("Set-Cookie")).contains("SESSION=");
		assertThat(mvc.post().uri("/api/auth/demo").with(csrf()).with(depuis("10.0.0.2"))).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(visiteursPresents()).as("deux visiteurs, deux comptes").isEqualTo(2);
	}

	@Test
	void un_visiteur_deja_dans_son_compte_y_reste() {
		UUID moi = nouveauVisiteur();
		assertThat(mvc.post().uri("/api/auth/demo").with(csrf()).with(connecte(moi)).with(depuis("10.0.0.3"))).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(visiteursPresents()).isEqualTo(1);
	}

	@Test
	void au_dela_de_cinq_comptes_par_heure_une_meme_adresse_attend() {
		for (int i = 0; i < ConnexionDemoController.PAR_IP; i++) {
			assertThat(mvc.post().uri("/api/auth/demo").with(csrf()).with(depuis("10.0.0.4"))).hasStatus(HttpStatus.NO_CONTENT);
		}
		var refus = mvc.post().uri("/api/auth/demo").with(csrf()).with(depuis("10.0.0.4")).exchange();
		assertThat(refus).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(refus.getResponse().getHeader("Retry-After")).isNotBlank();
	}

	@Test
	void le_visiteur_a_tout_le_parcours_a_lui_seul() {
		UUID moi = nouveauVisiteur();
		UUID autre = nouveauVisiteur();

		UUID cercle = base.sql("SELECT id FROM cercle WHERE createur_id = :moi").param("moi", moi).query(UUID.class).single();
		var detail = mvc.get().uri("/api/cercles/" + cercle).with(connecte(moi));
		assertThat(detail).hasStatusOk();
		assertThat(detail).bodyJson().extractingPath("$.nom").isEqualTo("Cercle des pionniers");
		assertThat(detail).bodyJson().extractingPath("$.tours[1].beneficiaire.id").isEqualTo(moi.toString());
		assertThat(compter("SELECT count(*) FROM cercle_membre WHERE cercle_id = '" + cercle + "' AND utilisateur_id = :moi", autre))
			.as("l'autre visiteur n'est pas dans ce cercle").isZero();

		assertThat(compter("SELECT count(*) FROM adhesion WHERE utilisateur_id = :moi", moi)).isEqualTo(2);
		assertThat(compter("SELECT count(*) FROM contact WHERE :moi IN (demandeur_id, destinataire_id)", moi)).isEqualTo(5);
		assertThat(compter("SELECT count(*) FROM message WHERE :moi IN (expediteur_id, destinataire_id)", moi)).isEqualTo(3);
		assertThat(compter("SELECT count(*) FROM inscription WHERE utilisateur_id = :moi AND equipe_id IS NULL", moi)).isEqualTo(1);
		assertThat(compter("""
				SELECT count(*) FROM proposition p JOIN mission m ON m.id = p.mission_id
				WHERE m.client_id = :moi AND m.visibilite = 'invitation'
				""", moi)).as("sa mission n'est pas dans la liste publique").isEqualTo(2);

		// Rien de lui dans les espaces partagés : sinon chaque visiteur y laisserait le même message.
		assertThat(compter("SELECT count(*) FROM publication WHERE auteur_id = :moi", moi)).isZero();
		assertThat(compter("SELECT count(*) FROM message_salon WHERE auteur_id = :moi", moi)).isZero();
		assertThat(compter("SELECT count(*) FROM profil WHERE utilisateur_id = :moi AND NOT publie", moi))
			.as("absent de la recherche de coéquipiers").isEqualTo(1);

		var profil = mvc.get().uri("/api/me/profil").with(connecte(moi));
		assertThat(profil).hasStatusOk();
		assertThat(profil).bodyJson().extractingPath("$.fiabilite[*].type").asArray().contains("avis_coequipier");
		assertThat(profil).bodyJson().extractingPath("$.projets.length()").isEqualTo(2);
	}

	@Test
	void les_parcours_des_visiteurs_ne_gonflent_pas_les_profils_des_personnes_de_la_demo() throws Exception {
		String nia = mvc.get().uri("/api/profils/" + DonneesDemo.NIA.id()).exchange().getResponse().getContentAsString();
		String kofi = mvc.get().uri("/api/profils/" + DonneesDemo.KOFI.id()).exchange().getResponse().getContentAsString();
		nouveauVisiteur();
		nouveauVisiteur();
		assertThat(mvc.get().uri("/api/profils/" + DonneesDemo.NIA.id()).exchange().getResponse().getContentAsString()).isEqualTo(nia);
		assertThat(mvc.get().uri("/api/profils/" + DonneesDemo.KOFI.id()).exchange().getResponse().getContentAsString()).isEqualTo(kofi);
	}

	@Test
	void apres_24_heures_le_visiteur_et_tout_ce_qu_il_a_fait_disparaissent() throws Exception {
		UUID moi = nouveauVisiteur();
		assertThat(mvc.post().uri("/api/cercles").contentType(MediaType.APPLICATION_JSON).content("{\"nom\":\"Le cercle d'un visiteur\",\"taille\":4}")
			.with(csrf()).with(connecte(moi))).hasStatus(HttpStatus.CREATED);
		assertThat(mvc.post().uri("/api/conversations/" + DonneesDemo.KOFI.id() + "/messages").contentType(MediaType.APPLICATION_JSON)
			.content("{\"texte\":\"Bonjour Kofi\"}").with(csrf()).with(connecte(moi))).hasStatus(HttpStatus.CREATED);
		UUID recent = nouveauVisiteur();

		assertThat(visiteurs.nettoyer(Instant.now())).as("rien n'a encore 24 heures").isZero();
		base.sql("UPDATE utilisateur SET cree_le = :t WHERE id = :moi").param("t", Timestamp.from(Instant.now().minus(Duration.ofHours(25))))
			.param("moi", moi).update();
		assertThat(visiteurs.nettoyer(Instant.now())).isEqualTo(1);

		assertThat(compter("SELECT count(*) FROM utilisateur WHERE id = :moi", moi)).isZero();
		assertThat(compter("SELECT count(*) FROM cercle WHERE createur_id = :moi", moi)).isZero();
		assertThat(compter("SELECT count(*) FROM mission WHERE :moi IN (client_id, developpeur_id)", moi)).isZero();
		assertThat(compter("SELECT count(*) FROM projet WHERE porteur_id = :moi", moi)).isZero();
		assertThat(compter("SELECT count(*) FROM message WHERE :moi IN (expediteur_id, destinataire_id)", moi)).isZero();
		assertThat(compter("SELECT count(*) FROM utilisateur WHERE id = :moi", recent)).as("l'autre visiteur reste").isEqualTo(1);
		assertThat(compter("SELECT count(*) FROM utilisateur WHERE id = :moi", DonneesDemo.KOFI.id())).isEqualTo(1);
	}

	@Test
	void un_visiteur_n_ecrit_qu_aux_personnes_de_la_demo_et_ne_se_supprime_pas() {
		UUID moi = nouveauVisiteur();
		UUID vrai = UUID.randomUUID();
		Timestamp t = Timestamp.from(Instant.now());
		base.sql("INSERT INTO utilisateur (id, nom, email, cree_le, mis_a_jour_le) VALUES (:id, 'Vraie personne', :e, :t, :t)")
			.param("id", vrai).param("e", vrai + "@exemple.test").param("t", t).update();
		try {
			assertThat(mvc.post().uri("/api/conversations/" + vrai + "/messages").contentType(MediaType.APPLICATION_JSON)
				.content("{\"texte\":\"Bonjour\"}").with(csrf()).with(connecte(moi))).hasStatus(HttpStatus.FORBIDDEN)
				.bodyJson().extractingPath("$.code").isEqualTo("compte_demo");
			assertThat(mvc.put().uri("/api/contacts/" + vrai).with(csrf()).with(connecte(moi))).hasStatus(HttpStatus.FORBIDDEN);
			assertThat(mvc.delete().uri("/api/me").with(csrf()).with(connecte(moi))).hasStatus(HttpStatus.FORBIDDEN);
			assertThat(compter("SELECT count(*) FROM utilisateur WHERE id = :moi", moi)).isEqualTo(1);
		}
		finally {
			base.sql("DELETE FROM utilisateur WHERE id = :id").param("id", vrai).update();
		}
	}

}
