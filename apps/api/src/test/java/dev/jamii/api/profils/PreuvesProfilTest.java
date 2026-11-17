package dev.jamii.api.profils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
import org.springframework.transaction.annotation.Transactional;

import dev.jamii.api.TestcontainersConfiguration;
import dev.jamii.api.auth.Role;
import dev.jamii.api.auth.UtilisateurConnecte;

/** Les projets et les preuves d'un profil (#157), calculés par les modules à partir de ce qui s'est passé sur Jamii. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
class PreuvesProfilTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient base;

	private final Instant maintenant = Instant.now();

	private final Timestamp t = Timestamp.from(maintenant);

	private UUID personne(String nom, String pays) {
		UUID id = UUID.randomUUID();
		base.sql("INSERT INTO utilisateur (id, nom, email, cree_le, mis_a_jour_le) VALUES (:id, :nom, :e, :t, :t)")
			.param("id", id).param("nom", nom).param("e", id + "@exemple.test").param("t", t).update();
		base.sql("""
				INSERT INTO profil (utilisateur_id, pays, disponibilite, publie, generation_statut, mis_a_jour_le)
				VALUES (:id, :pays, 'disponible', TRUE, 'terminee', :t)
				""").param("id", id).param("pays", pays).param("t", t).update();
		return id;
	}

	private UUID projet(String titre, UUID porteur, String statut) {
		UUID id = UUID.randomUUID();
		base.sql("""
				INSERT INTO projet (id, titre, origine, porteur_id, statut, derniere_activite_le, cree_le, mis_a_jour_le)
				VALUES (:id, :titre, 'idee', :p, :statut, :t, :t, :t)
				""").param("id", id).param("titre", titre).param("p", porteur).param("statut", statut).param("t", t).update();
		return id;
	}

	private void membre(UUID projet, UUID qui) {
		base.sql("INSERT INTO membre_equipe (projet_id, utilisateur_id, rejoint_le) VALUES (:p, :u, :t)")
			.param("p", projet).param("u", qui).param("t", t).update();
	}

	/** Une tâche livrée maintenant ; `echeanceDansJours` null : sans échéance. */
	private void tacheLivree(UUID projet, UUID qui, String titre, Integer echeanceDansJours) {
		base.sql("""
				INSERT INTO tache (id, projet_id, titre, assignee_id, statut, livree_le, echeance, cree_le)
				VALUES (:id, :p, :titre, :u, 'livree', :t, :e, :t)
				""").param("id", UUID.randomUUID()).param("p", projet).param("titre", titre).param("u", qui).param("t", t)
			.param("e", echeanceDansJours == null ? null : Date.valueOf(LocalDate.now(ZoneOffset.UTC).plusDays(echeanceDansJours))).update();
	}

	private void reponseAcceptee(UUID communaute, UUID question, UUID reponse) {
		UUID publication = UUID.randomUUID();
		UUID r = UUID.randomUUID();
		base.sql("INSERT INTO publication (id, communaute_id, auteur_id, type, titre, contenu, cree_le) VALUES (:id, :c, :a, 'question', 'Comment payer par mobile ?', 'Une question', :t)")
			.param("id", publication).param("c", communaute).param("a", question).param("t", t).update();
		base.sql("INSERT INTO reponse (id, publication_id, auteur_id, contenu, cree_le) VALUES (:id, :p, :a, 'Une réponse', :t)")
			.param("id", r).param("p", publication).param("a", reponse).param("t", t).update();
		base.sql("UPDATE publication SET reponse_acceptee_id = :r, acceptee_le = :t WHERE id = :p").param("r", r).param("t", t).param("p", publication).update();
	}

	private void missionTerminee(UUID client, UUID developpeur, int note, String avis) {
		UUID id = UUID.randomUUID();
		base.sql("""
				INSERT INTO mission (id, client_id, titre, besoin, competences, visibilite, statut, cree_le, publiee_le, developpeur_id)
				VALUES (:id, :c, 'La boutique en ligne', 'Un besoin', ARRAY['vue'], 'publique', 'terminee', :t, :t, :d)
				""").param("id", id).param("c", client).param("t", t).param("d", developpeur).update();
		base.sql("INSERT INTO avis_mission (mission_id, auteur, auteur_id, note, texte, donne_le) VALUES (:m, 'client', :c, :n, :texte, :t)")
			.param("m", id).param("c", client).param("n", note).param("texte", avis).param("t", Timestamp.from(maintenant.minus(Duration.ofHours(1)))).update();
	}

	@Test
	void le_profil_montre_ses_projets_et_ses_preuves_calcules_par_les_modules() {
		UUID awa = personne("Awa", "SN");
		UUID kofi = personne("Kofi", "GH");
		UUID client = personne("Une PME", "CI");

		UUID boutique = projet("La boutique des tisserandes", awa, "livre");
		membre(boutique, kofi);
		UUID cacao = projet("Le carnet du cacao", kofi, "en_cours");
		membre(cacao, awa);
		projet("Une idée qui recrute", awa, "recrutement");

		tacheLivree(boutique, awa, "Le panier", 1);
		tacheLivree(boutique, awa, "Le paiement mobile", -3);
		tacheLivree(boutique, awa, "Sans échéance", null);

		UUID communaute = UUID.randomUUID();
		base.sql("INSERT INTO communaute (id, slug, nom, description, theme, cree_le) VALUES (:id, :slug, 'Web', 'Le web', 'web', :t)")
			.param("id", communaute).param("slug", "web-" + communaute.toString().substring(0, 8)).param("t", t).update();
		reponseAcceptee(communaute, kofi, awa);
		// Accepter sa propre réponse ne prouve rien.
		reponseAcceptee(communaute, awa, awa);

		missionTerminee(client, awa, 5, "Travail soigné, livré avant la date.");
		missionTerminee(client, awa, 2, "Pas terrible.");

		var profil = mvc.get().uri("/api/profils/" + awa).exchange();
		assertThat(profil).hasStatusOk();
		assertThat(profil).bodyJson().extractingPath("$.projets[*].titre").asArray()
			.containsExactlyInAnyOrder("La boutique des tisserandes", "Le carnet du cacao");
		assertThat(profil).bodyJson().extractingPath("$.projets[?(@.titre == 'La boutique des tisserandes')].role").asArray().containsExactly("porteur");
		assertThat(profil).bodyJson().extractingPath("$.projets[?(@.titre == 'Le carnet du cacao')].role").asArray().containsExactly("membre");
		assertThat(profil).bodyJson().extractingPath("$.projets[?(@.titre == 'La boutique des tisserandes')].paysEquipe[*]").asArray()
			.containsExactly("SN", "GH");
		assertThat(profil).bodyJson().extractingPath("$.fiabilite[*].type").asArray()
			.containsExactly("tache_livree_a_temps", "avis_coequipier", "reponse_acceptee");
		assertThat(profil).bodyJson().extractingPath("$.fiabilite[0].nombre").isEqualTo(1);
		assertThat(profil).bodyJson().extractingPath("$.fiabilite[0].sur").isEqualTo(2);
		assertThat(profil).bodyJson().extractingPath("$.fiabilite[1].nombre").isEqualTo(1);
		assertThat(profil).bodyJson().extractingPath("$.fiabilite[2].nombre").isEqualTo(1);

		var connecte = oauth2Login().oauth2User(new UtilisateurConnecte(kofi, Set.of(Role.MEMBRE)));
		var avis = mvc.get().uri("/api/profils/" + awa + "/preuves?type=avis_coequipier&limite=3").with(connecte).exchange();
		assertThat(avis).hasStatusOk();
		assertThat(avis).bodyJson().extractingPath("$.elements.length()").isEqualTo(1);
		assertThat(avis).bodyJson().extractingPath("$.elements[0].detail").isEqualTo("Travail soigné, livré avant la date.");
		assertThat(avis).bodyJson().extractingPath("$.elements[0].auteur.nom").isEqualTo("Une PME");
		assertThat(avis).bodyJson().extractingPath("$.elements[0].source.module").isEqualTo("missions");

		var taches = mvc.get().uri("/api/profils/" + awa + "/preuves?type=tache_livree_a_temps").with(connecte).exchange();
		assertThat(taches).bodyJson().extractingPath("$.elements[0].source.titre").isEqualTo("Le panier");
		assertThat(taches).bodyJson().extractingPath("$.curseurSuivant").isNull();
	}

	@Test
	void sans_travail_sur_jamii_le_profil_n_a_ni_projet_ni_preuve() {
		UUID nouveau = personne("Nouveau", "ML");
		var profil = mvc.get().uri("/api/profils/" + nouveau).exchange();
		assertThat(profil).hasStatusOk();
		assertThat(profil).bodyJson().extractingPath("$.projets.length()").isEqualTo(0);
		assertThat(profil).bodyJson().extractingPath("$.fiabilite.length()").isEqualTo(0);
	}

	@Test
	void le_detail_des_preuves_demande_un_compte_et_un_type_connu() {
		UUID awa = personne("Awa", "SN");
		assertThat(mvc.get().uri("/api/profils/" + awa + "/preuves").exchange()).hasStatus(HttpStatus.UNAUTHORIZED);
		var connecte = oauth2Login().oauth2User(new UtilisateurConnecte(awa, Set.of(Role.MEMBRE)));
		assertThat(mvc.get().uri("/api/profils/" + awa + "/preuves?type=inconnu").with(connecte).exchange()).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(mvc.get().uri("/api/profils/" + awa + "/preuves?curseur=abc").with(connecte).exchange()).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(mvc.get().uri("/api/profils/" + UUID.randomUUID() + "/preuves").with(connecte).exchange()).hasStatus(HttpStatus.NOT_FOUND);
	}

}
