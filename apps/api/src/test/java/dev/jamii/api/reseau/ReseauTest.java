package dev.jamii.api.reseau;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;

import java.sql.Timestamp;
import java.time.Duration;
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

/** Les routes de contracts/reseau.yaml, sur un vrai PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ReseauTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient base;

	// ---------------------------------------------------------------- aides

	private UtilisateurConnecte utilisateur(String nom) {
		UUID id = UUID.randomUUID();
		Timestamp t = Timestamp.from(Instant.now());
		base.sql("INSERT INTO utilisateur (id, nom, login_github, avatar_url, cree_le, mis_a_jour_le) VALUES (:id, :nom, :login, NULL, :t, :t)")
			.param("id", id).param("nom", nom).param("login", "gh-" + id.toString().substring(0, 8)).param("t", t).update();
		return new UtilisateurConnecte(id, Set.of(Role.MEMBRE));
	}

	/** Un profil publié, avec sa ville, sa disponibilité et ses compétences déclarées. */
	private void profil(UtilisateurConnecte qui, String ville, String disponibilite, String... competences) {
		base.sql("""
				INSERT INTO profil (utilisateur_id, ville, disponibilite, publie, generation_statut, mis_a_jour_le)
				VALUES (:id, :ville, :dispo, TRUE, 'terminee', now())""")
			.param("id", qui.id()).param("ville", ville).param("dispo", disponibilite).update();
		for (String c : competences) {
			base.sql("INSERT INTO competence_profil (utilisateur_id, competence, source) VALUES (:id, :c, 'declaree')")
				.param("id", qui.id()).param("c", c).update();
		}
	}

	private MvcTestResult poster(String uri, UtilisateurConnecte qui, String corps) {
		var requete = mvc.post().uri(uri).with(csrf()).with(oauth2Login().oauth2User(qui));
		if (corps != null) {
			requete = requete.contentType(MediaType.APPLICATION_JSON).content(corps);
		}
		return requete.exchange();
	}

	private MvcTestResult lire(String uri, UtilisateurConnecte qui) {
		return mvc.get().uri(uri).with(oauth2Login().oauth2User(qui)).exchange();
	}

	/** Un cercle créé par l'API des cercles, dans une ville unique pour ne pas croiser les autres tests. */
	private String cercle(UtilisateurConnecte createur, String ville, int taille) {
		MvcTestResult r = poster("/api/cercles", createur, "{\"nom\":\"Cercle de " + ville + "\",\"ville\":\"" + ville
				+ "\",\"taille\":" + taille + "}");
		assertThat(r).hasStatus(HttpStatus.CREATED);
		return r.getResponse().getHeader("Location").substring("/api/cercles/".length());
	}

	private static String message(String texte, String cercleId) {
		return cercleId == null ? "{\"texte\":\"" + texte + "\"}" : "{\"texte\":\"" + texte + "\",\"cercleId\":\"" + cercleId + "\"}";
	}

	// ---------------------------------------------------------------- recommandations

	@Test
	void recommande_a_un_developpeur_le_cercle_ouvert_a_qui_manque_sa_competence() {
		String ville = "Ville-" + UUID.randomUUID();
		UtilisateurConnecte kofi = utilisateur("Kofi");
		profil(kofi, ville, "occupe", "react");
		String id = cercle(kofi, ville, 4);
		UtilisateurConnecte awa = utilisateur("Awa");
		profil(awa, ville, "disponible", "flutter", "react");

		assertThat(lire("/api/recommandations/cercles?limite=20", awa)).hasStatusOk()
			.bodyJson()
			.extractingPath("$.elements[?(@.cercle.id == '" + id + "')]")
			.asArray()
			.singleElement()
			.satisfies(r -> assertThat(r.toString()).contains("competence_manquante", "meme_ville", "flutter")
				.doesNotContain("react"));
	}

	@Test
	void ne_recommande_rien_sans_profil_publie() {
		UtilisateurConnecte sansProfil = utilisateur("Sans profil");
		assertThat(lire("/api/recommandations/cercles", sansProfil)).hasStatusOk().bodyJson().extractingPath("$.elements").asArray().isEmpty();
	}

	@Test
	void propose_a_un_membre_les_profils_disponibles_qui_manquent_au_cercle() {
		String ville = "Ville-" + UUID.randomUUID();
		UtilisateurConnecte awa = utilisateur("Awa");
		profil(awa, ville, "disponible", "flutter");
		String id = cercle(awa, ville, 4);
		UtilisateurConnecte ibrahima = utilisateur("Ibrahima");
		profil(ibrahima, ville, "disponible", "spring-boot");
		UtilisateurConnecte occupe = utilisateur("Zawadi");
		profil(occupe, ville, "occupe", "postgresql");

		MvcTestResult r = lire("/api/recommandations/profils?cercleId=" + id + "&limite=20", awa);
		assertThat(r).hasStatusOk().bodyJson().extractingPath("$.elements[0].profil.nom").isEqualTo("Ibrahima");
		assertThat(r).bodyJson().extractingPath("$.elements[*].profil.nom").asArray().doesNotContain("Zawadi", "Awa");
		assertThat(r).bodyJson().extractingPath("$.elements[0].invite").isEqualTo(false);

		// Un non-membre n'y a pas accès ; un identifiant inconnu est introuvable.
		assertThat(lire("/api/recommandations/profils?cercleId=" + id, ibrahima)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(lire("/api/recommandations/profils?cercleId=" + UUID.randomUUID(), awa)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(lire("/api/recommandations/profils?cercleId=pas-un-uuid", awa)).hasStatus(HttpStatus.BAD_REQUEST);
	}

	// ---------------------------------------------------------------- messages et invitations

	@Test
	void une_invitation_arrive_avec_le_cercle_et_le_profil_passe_a_invite() {
		String ville = "Ville-" + UUID.randomUUID();
		UtilisateurConnecte awa = utilisateur("Awa");
		String id = cercle(awa, ville, 4);
		UtilisateurConnecte kwame = utilisateur("Kwame");
		profil(kwame, ville, "disponible", "postgresql");

		MvcTestResult envoi = poster("/api/conversations/" + kwame.id() + "/messages", awa, message("Tu nous rejoins ?", id));
		assertThat(envoi).hasStatus(HttpStatus.CREATED).bodyJson().extractingPath("$.invitation.cercle.id").isEqualTo(id);

		assertThat(lire("/api/me/activite", kwame)).bodyJson().extractingPath("$.messagesNonLus").isEqualTo(1);
		MvcTestResult conversations = lire("/api/conversations", kwame);
		assertThat(conversations).bodyJson().extractingPath("$.elements[0].personne.nom").isEqualTo("Awa");
		assertThat(conversations).bodyJson().extractingPath("$.elements[0].nonLus").isEqualTo(1);
		assertThat(conversations).bodyJson().extractingPath("$.elements[0].dernierMessage.deMoi").isEqualTo(false);

		// Lire la conversation la marque comme lue.
		assertThat(lire("/api/conversations/" + awa.id() + "/messages", kwame)).hasStatusOk()
			.bodyJson().extractingPath("$.elements[0].invitation.cercle.nombreMembres").isEqualTo(1);
		assertThat(lire("/api/me/activite", kwame)).bodyJson().extractingPath("$.messagesNonLus").isEqualTo(0);

		assertThat(lire("/api/recommandations/profils?cercleId=" + id, awa)).bodyJson()
			.extractingPath("$.elements[0].invite").isEqualTo(true);
	}

	@Test
	void refuse_une_invitation_a_un_cercle_dont_on_n_est_pas_membre_ou_qui_est_complet() {
		UtilisateurConnecte awa = utilisateur("Awa");
		UtilisateurConnecte kofi = utilisateur("Kofi");
		String autre = cercle(kofi, "Ville-" + UUID.randomUUID(), 4);
		assertThat(poster("/api/conversations/" + kofi.id() + "/messages", awa, message("Viens", autre)))
			.hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.code").isEqualTo("pas_membre_du_cercle");

		String complet = cercle(awa, "Ville-" + UUID.randomUUID(), 4);
		for (int i = 0; i < 3; i++) {
			assertThat(poster("/api/cercles/" + complet + "/membres", utilisateur("Membre " + i), null)).hasStatusOk();
		}
		assertThat(poster("/api/conversations/" + kofi.id() + "/messages", awa, message("Viens", complet)))
			.hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.code").isEqualTo("cercle_ferme");
	}

	@Test
	void valide_le_message_et_refuse_de_s_ecrire_a_soi_meme() {
		UtilisateurConnecte awa = utilisateur("Awa");
		UtilisateurConnecte kofi = utilisateur("Kofi");
		assertThat(poster("/api/conversations/" + kofi.id() + "/messages", awa, message("   ", null)))
			.hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.erreurs[0].champ").isEqualTo("texte");
		assertThat(poster("/api/conversations/" + kofi.id() + "/messages", awa, message("x".repeat(2001), null)))
			.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(poster("/api/conversations/" + awa.id() + "/messages", awa, message("Moi", null)))
			.hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.code").isEqualTo("soi_meme");
		assertThat(poster("/api/conversations/" + UUID.randomUUID() + "/messages", awa, message("Allô", null)))
			.hasStatus(HttpStatus.NOT_FOUND);
		assertThat(lire("/api/conversations/pas-un-uuid/messages", awa)).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void personne_ne_lit_la_conversation_des_autres() {
		UtilisateurConnecte awa = utilisateur("Awa");
		UtilisateurConnecte kofi = utilisateur("Kofi");
		UtilisateurConnecte curieux = utilisateur("Curieux");
		assertThat(poster("/api/conversations/" + kofi.id() + "/messages", awa, message("Secret", null))).hasStatus(HttpStatus.CREATED);
		// Le curieux ne voit que sa propre conversation (vide) avec Kofi, jamais celle d'Awa et Kofi.
		assertThat(lire("/api/conversations/" + kofi.id() + "/messages", curieux)).hasStatusOk()
			.bodyJson().extractingPath("$.elements").asArray().isEmpty();
		assertThat(lire("/api/conversations", curieux)).bodyJson().extractingPath("$.elements").asArray().isEmpty();
	}

	@Test
	void limite_les_premieres_prises_de_contact_et_le_nombre_de_messages() {
		UtilisateurConnecte spam = utilisateur("Spam");
		Instant il_y_a = Instant.now().minus(Duration.ofMinutes(5));
		// 10 conversations ouvertes aujourd'hui : la 11ᵉ personne nouvelle est refusée, une ancienne reste joignable.
		UtilisateurConnecte premier = null;
		for (int i = 0; i < Messagerie.PREMIERS_CONTACTS_PAR_JOUR; i++) {
			UtilisateurConnecte cible = utilisateur("Cible " + i);
			premier = premier == null ? cible : premier;
			inserer(spam, cible, il_y_a.minusSeconds(i * 60L));
		}
		assertThat(poster("/api/conversations/" + utilisateur("Nouveau").id() + "/messages", spam, message("Salut", null)))
			.hasStatus(HttpStatus.TOO_MANY_REQUESTS).headers().containsHeader("Retry-After");
		assertThat(poster("/api/conversations/" + premier.id() + "/messages", spam, message("Encore moi", null)))
			.hasStatus(HttpStatus.CREATED);

		// 30 messages dans l'heure : le suivant est refusé, même à une personne connue.
		for (int i = 0; i < Messagerie.MESSAGES_PAR_HEURE; i++) {
			inserer(spam, premier, il_y_a);
		}
		assertThat(poster("/api/conversations/" + premier.id() + "/messages", spam, message("Et encore", null)))
			.hasStatus(HttpStatus.TOO_MANY_REQUESTS);
	}

	private void inserer(UtilisateurConnecte de, UtilisateurConnecte a, Instant le) {
		base.sql("INSERT INTO message (id, expediteur_id, destinataire_id, texte, envoye_le) VALUES (:id, :de, :a, 'Bonjour', :le)")
			.param("id", UUID.randomUUID()).param("de", de.id()).param("a", a.id()).param("le", Timestamp.from(le)).update();
	}

}
