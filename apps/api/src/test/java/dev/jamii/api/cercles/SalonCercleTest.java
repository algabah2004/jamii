package dev.jamii.api.cercles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;

import java.sql.Timestamp;
import java.time.Instant;
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
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import dev.jamii.api.TestcontainersConfiguration;
import dev.jamii.api.auth.Role;
import dev.jamii.api.auth.UtilisateurConnecte;

/** Le salon du cercle (contracts/cercles.yaml v0.2), sur un vrai PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, CerclesTest.DepotSimule.class })
class SalonCercleTest {

	private static final String IDEE = "{\"titre\":\"Une caisse hors ligne\",\"description\":\"Une caisse qui marche sans internet.\"}";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient base;

	// ---------------------------------------------------------------- aides

	private UtilisateurConnecte utilisateur(String nom) {
		UUID id = UUID.randomUUID();
		Timestamp t = Timestamp.from(Instant.now());
		base.sql("INSERT INTO utilisateur (id, nom, login_github, avatar_url, cree_le, mis_a_jour_le) VALUES (:id, :nom, :login, 'https://avatars.example/a', :t, :t)")
			.param("id", id).param("nom", nom).param("login", "gh-" + id.toString().substring(0, 8)).param("t", t).update();
		return new UtilisateurConnecte(id, Set.of(Role.MEMBRE));
	}

	private MvcTestResult envoyer(String methode, String uri, UtilisateurConnecte qui, String corps) {
		var requete = switch (methode) {
			case "PUT" -> mvc.put().uri(uri);
			case "PATCH" -> mvc.patch().uri(uri);
			case "DELETE" -> mvc.delete().uri(uri);
			default -> mvc.post().uri(uri);
		};
		if (corps != null) {
			requete = requete.contentType(MediaType.APPLICATION_JSON).content(corps);
		}
		return requete.with(csrf()).with(oauth2Login().oauth2User(qui)).exchange();
	}

	private MvcTestResult lire(String uri, UtilisateurConnecte qui) {
		return mvc.get().uri(uri).with(oauth2Login().oauth2User(qui)).exchange();
	}

	private record Cercle(String id, UtilisateurConnecte createur, List<UtilisateurConnecte> membres) {
	}

	/** Un cercle de 4, complet, idées déposées, ordre tiré. */
	private Cercle cercleTire() {
		UtilisateurConnecte createur = utilisateur("Awa");
		var creation = envoyer("POST", "/api/cercles", createur, "{\"nom\":\"Cercle du salon\",\"taille\":4}");
		assertThat(creation).hasStatus(HttpStatus.CREATED);
		String id = creation.getResponse().getHeader("Location").substring("/api/cercles/".length());
		List<UtilisateurConnecte> membres = new ArrayList<>(List.of(createur));
		for (String nom : List.of("Kofi", "Fatou", "Moussa")) {
			UtilisateurConnecte m = utilisateur(nom);
			assertThat(envoyer("POST", "/api/cercles/" + id + "/membres", m, null)).hasStatusOk();
			membres.add(m);
		}
		membres.forEach(m -> assertThat(envoyer("PUT", "/api/cercles/" + id + "/membres/moi/idee", m, IDEE)).hasStatusOk());
		assertThat(envoyer("POST", "/api/cercles/" + id + "/tirage", membres.get(2), null)).hasStatusOk();
		return new Cercle(id, createur, membres);
	}

	private String salon(Cercle c) {
		return "/api/cercles/" + c.id() + "/salon";
	}

	// ---------------------------------------------------------------- tests

	@Test
	void le_salon_raconte_les_etapes_du_cercle_dans_l_ordre() {
		Cercle c = cercleTire();
		var reponse = lire(salon(c), c.createur());
		assertThat(reponse).hasStatusOk();
		assertThat(reponse).bodyJson().extractingPath("$.elements[*].evenement.code").asArray()
			.containsExactly("cercle_cree", "membre_rejoint", "membre_rejoint", "membre_rejoint", "tirage", "tour_ouvert");
		assertThat(reponse).bodyJson().extractingPath("$.elements[0].auteur").isNull();
		assertThat(reponse).bodyJson().extractingPath("$.elements[4].evenement.personne.nom").isEqualTo("Fatou");
		assertThat(reponse).bodyJson().extractingPath("$.elements[5].evenement.tour").isEqualTo(1);
	}

	@Test
	void le_salon_est_reserve_aux_membres() {
		Cercle c = cercleTire();
		UtilisateurConnecte autre = utilisateur("Curieux");
		assertThat(lire(salon(c), autre)).hasStatus(HttpStatus.FORBIDDEN).bodyJson().extractingPath("$.code").isEqualTo("pas_membre");
		assertThat(envoyer("POST", salon(c) + "/messages", autre, "{\"type\":\"texte\",\"texte\":\"Bonjour\"}"))
			.hasStatus(HttpStatus.FORBIDDEN);
		assertThat(lire("/api/cercles/" + UUID.randomUUID() + "/salon", autre)).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void un_membre_ecrit_et_on_relit_seulement_les_nouveaux_messages() throws Exception {
		Cercle c = cercleTire();
		var envoi = envoyer("POST", salon(c) + "/messages", c.membres().get(1), "{\"type\":\"texte\",\"texte\":\"  On se partage les écrans ?  \"}");
		assertThat(envoi).hasStatus(HttpStatus.CREATED);
		assertThat(envoi).bodyJson().extractingPath("$.texte").isEqualTo("On se partage les écrans ?");
		assertThat(envoi).bodyJson().extractingPath("$.auteur.nom").isEqualTo("Kofi");
		// `apres` : la plus grande version connue (#157) : on reçoit les nouveaux messages et ceux qui ont changé.
		long version = ((Number) com.jayway.jsonpath.JsonPath.read(envoi.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8), "$.version")).longValue();

		envoyer("POST", salon(c) + "/messages", c.membres().get(2), "{\"type\":\"texte\",\"texte\":\"Je prends la connexion\"}");
		var nouveaux = lire(salon(c) + "?apres=" + version, c.createur());
		assertThat(nouveaux).bodyJson().extractingPath("$.elements.length()").isEqualTo(1);
		assertThat(nouveaux).bodyJson().extractingPath("$.elements[0].texte").isEqualTo("Je prends la connexion");

		assertThat(envoyer("POST", salon(c) + "/messages", c.createur(), "{\"type\":\"texte\",\"texte\":\"   \"}"))
			.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(envoyer("POST", salon(c) + "/messages", c.createur(), "{\"type\":\"evenement\",\"texte\":\"triche\"}"))
			.hasStatus(HttpStatus.BAD_REQUEST);
	}

	private static String champ(MvcTestResult r, String chemin) throws Exception {
		return String.valueOf((Object) com.jayway.jsonpath.JsonPath.read(r.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8), chemin));
	}

	@Test
	void repondre_modifier_reagir_supprimer_et_les_autres_le_voient_a_la_relecture() throws Exception {
		Cercle c = cercleTire();
		var kofi = c.membres().get(1);
		var fatou = c.membres().get(2);
		var question = envoyer("POST", salon(c) + "/messages", kofi, "{\"type\":\"texte\",\"texte\":\"Qui prend les écrans ?\"}");
		String idQuestion = champ(question, "$.id");
		var reponse = envoyer("POST", salon(c) + "/messages", fatou, "{\"type\":\"texte\",\"texte\":\"Moi\",\"reponseA\":\"" + idQuestion + "\"}");
		assertThat(reponse).hasStatus(HttpStatus.CREATED);
		assertThat(reponse).bodyJson().extractingPath("$.reponseA.auteur.nom").isEqualTo("Kofi");
		assertThat(reponse).bodyJson().extractingPath("$.reponseA.texte").isEqualTo("Qui prend les écrans ?");
		long vu = Long.parseLong(champ(reponse, "$.version"));

		// Kofi corrige sa question, Fatou y réagit : Awa, qui relit depuis `vu`, reçoit la question à jour.
		assertThat(envoyer("PATCH", salon(c) + "/messages/" + idQuestion, kofi, "{\"texte\":\"Qui prend les écrans de connexion ?\"}"))
			.hasStatusOk().bodyJson().extractingPath("$.modifieLe").isNotNull();
		assertThat(envoyer("POST", salon(c) + "/messages/" + idQuestion + "/reactions", fatou, "{\"emoji\":\"👍\",\"active\":true}"))
			.hasStatusOk().bodyJson().extractingPath("$.reactions[0].nombre").isEqualTo(1);
		var relu = lire(salon(c) + "?apres=" + vu, c.createur());
		assertThat(relu).bodyJson().extractingPath("$.elements.length()").isEqualTo(1);
		assertThat(relu).bodyJson().extractingPath("$.elements[0].texte").isEqualTo("Qui prend les écrans de connexion ?");
		assertThat(relu).bodyJson().extractingPath("$.elements[0].reactions[0].noms[0]").isEqualTo("Fatou");

		// Seul l'auteur modifie ou supprime ; un message supprimé garde sa place, sans texte ni réactions.
		assertThat(envoyer("PATCH", salon(c) + "/messages/" + idQuestion, fatou, "{\"texte\":\"pirate\"}")).hasStatus(HttpStatus.CONFLICT);
		assertThat(envoyer("DELETE", salon(c) + "/messages/" + idQuestion, fatou, null)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(envoyer("DELETE", salon(c) + "/messages/" + idQuestion, kofi, null)).hasStatus(HttpStatus.NO_CONTENT);
		var apres = lire(salon(c) + "?apres=" + vu, c.createur());
		assertThat(apres).bodyJson().extractingPath("$.elements[0].supprime").isEqualTo(true);
		assertThat(apres).bodyJson().extractingPath("$.elements[0].texte").isNull();
		assertThat(apres).bodyJson().extractingPath("$.elements[0].reactions.length()").isEqualTo(0);
		assertThat(envoyer("POST", salon(c) + "/messages/" + idQuestion + "/reactions", fatou, "{\"emoji\":\"👍\",\"active\":true}"))
			.hasStatus(HttpStatus.CONFLICT);
		assertThat(envoyer("POST", salon(c) + "/messages/" + idQuestion + "/reactions", fatou, "{\"emoji\":\"ok\",\"active\":true}"))
			.hasStatus(HttpStatus.BAD_REQUEST);
		// La réponse cite maintenant un message supprimé.
		var tout = lire(salon(c), c.createur());
		assertThat(tout).bodyJson().extractingPath("$.elements[-1].reponseA.supprime").isEqualTo(true);
	}

	@Test
	void un_point_par_jour_et_le_salon_dit_qui_l_a_fait() {
		Cercle c = cercleTire();
		String point = "{\"type\":\"point\",\"point\":{\"fait\":\"Le menu\",\"prevu\":\"La caisse\",\"bloque\":\"\"}}";
		var envoi = envoyer("POST", salon(c) + "/messages", c.membres().get(1), point);
		assertThat(envoi).hasStatus(HttpStatus.CREATED);
		assertThat(envoi).bodyJson().extractingPath("$.point.fait").isEqualTo("Le menu");
		assertThat(envoi).bodyJson().extractingPath("$.point.bloque").isNull();

		assertThat(envoyer("POST", salon(c) + "/messages", c.membres().get(1), point))
			.hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.code").isEqualTo("point_deja_fait");
		assertThat(envoyer("POST", salon(c) + "/messages", c.membres().get(2), "{\"type\":\"point\",\"point\":{\"fait\":\"x\"}}"))
			.hasStatus(HttpStatus.BAD_REQUEST);

		assertThat(lire(salon(c), c.createur())).bodyJson().extractingPath("$.pointsDuJour").asArray()
			.containsExactly(c.membres().get(1).id().toString());
	}

	@Test
	void un_coup_de_main_reste_en_haut_jusqu_a_ce_qu_un_membre_le_resolve() throws Exception {
		Cercle c = cercleTire();
		var demande = envoyer("POST", salon(c) + "/messages", c.membres().get(3), "{\"type\":\"coup_de_main\",\"texte\":\"Le build Android échoue\"}");
		assertThat(demande).hasStatus(HttpStatus.CREATED);
		assertThat(demande).bodyJson().extractingPath("$.coupDeMain.resoluLe").isNull();
		String id = com.jayway.jsonpath.JsonPath.read(demande.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8), "$.id");

		assertThat(lire(salon(c), c.createur())).bodyJson().extractingPath("$.coupsDeMain[0].texte").isEqualTo("Le build Android échoue");

		var resolu = envoyer("POST", salon(c) + "/messages/" + id + "/resolu", c.membres().get(1), null);
		assertThat(resolu).hasStatusOk();
		assertThat(resolu).bodyJson().extractingPath("$.coupDeMain.resoluPar.nom").isEqualTo("Kofi");
		// Idempotent : le premier qui a aidé reste.
		assertThat(envoyer("POST", salon(c) + "/messages/" + id + "/resolu", c.createur(), null))
			.bodyJson().extractingPath("$.coupDeMain.resoluPar.nom").isEqualTo("Kofi");
		assertThat(lire(salon(c), c.createur())).bodyJson().extractingPath("$.coupsDeMain.length()").isEqualTo(0);
	}

	@Test
	void un_seul_appel_a_la_fois_annonce_dans_le_salon_et_termine_par_son_lanceur() {
		Cercle c = cercleTire();
		var lance = envoyer("POST", salon(c) + "/appel", c.membres().get(1), null);
		assertThat(lance).hasStatus(HttpStatus.CREATED);
		assertThat(lance).bodyJson().extractingPath("$.url").asString().contains("jamii-cercle-");
		assertThat(envoyer("POST", salon(c) + "/appel", c.membres().get(2), null)).hasStatusOk()
			.bodyJson().extractingPath("$.lancePar.nom").isEqualTo("Kofi");

		var salonLu = lire(salon(c), c.createur());
		assertThat(salonLu).bodyJson().extractingPath("$.appel.lancePar.nom").isEqualTo("Kofi");
		assertThat(salonLu).bodyJson().extractingPath("$.elements[-1].type").isEqualTo("appel");
		assertThat(salonLu).bodyJson().extractingPath("$.elements[-1].appel.enCours").isEqualTo(true);

		assertThat(envoyer("DELETE", salon(c) + "/appel", c.membres().get(2), null))
			.hasStatus(HttpStatus.FORBIDDEN).bodyJson().extractingPath("$.code").isEqualTo("pas_lanceur");
		assertThat(envoyer("DELETE", salon(c) + "/appel", c.createur(), null)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(lire(salon(c), c.createur())).bodyJson().extractingPath("$.appel").isNull();
	}

	@Test
	void livrer_un_tour_l_annonce_et_ouvre_le_suivant() {
		Cercle c = cercleTire();
		UUID premier = base.sql("SELECT utilisateur_id FROM cercle_membre WHERE cercle_id = :c AND rang = 1")
			.param("c", UUID.fromString(c.id())).query(UUID.class).single();
		assertThat(envoyer("POST", "/api/cercles/" + c.id() + "/tours/1/livrer", c.createur(), null)).hasStatusOk();
		var reponse = lire(salon(c), c.createur());
		assertThat(reponse).bodyJson().extractingPath("$.elements[-2].evenement.code").isEqualTo("tour_livre");
		assertThat(reponse).bodyJson().extractingPath("$.elements[-2].evenement.personne.id").isEqualTo(premier.toString());
		assertThat(reponse).bodyJson().extractingPath("$.elements[-1].evenement.code").isEqualTo("tour_ouvert");
		assertThat(reponse).bodyJson().extractingPath("$.elements[-1].evenement.tour").isEqualTo(2);
	}

	@Test
	void seul_le_chef_de_projet_du_tour_consigne_une_decision() {
		Cercle c = cercleTire();
		UUID chef = base.sql("SELECT utilisateur_id FROM cercle_membre WHERE cercle_id = :c AND rang = 1")
			.param("c", UUID.fromString(c.id())).query(UUID.class).single();
		UtilisateurConnecte leChef = c.membres().stream().filter(m -> m.id().equals(chef)).findFirst().orElseThrow();
		UtilisateurConnecte unAutre = c.membres().stream().filter(m -> !m.id().equals(chef)).findFirst().orElseThrow();

		assertThat(envoyer("POST", salon(c) + "/messages", unAutre, "{\"type\":\"decision\",\"texte\":\"On part sur Flutter\"}"))
			.hasStatus(HttpStatus.FORBIDDEN).bodyJson().extractingPath("$.code").isEqualTo("pas_chef_de_projet");
		assertThat(envoyer("POST", salon(c) + "/messages", leChef, "{\"type\":\"decision\",\"texte\":\"On part sur Flutter\"}"))
			.hasStatus(HttpStatus.CREATED).bodyJson().extractingPath("$.type").isEqualTo("decision");
		envoyer("POST", salon(c) + "/messages", leChef, "{\"type\":\"decision\",\"texte\":\"La démo est vendredi\"}");
		var lu = lire(salon(c), unAutre);
		assertThat(lu).bodyJson().extractingPath("$.decisions[*].texte").asArray().containsExactly("La démo est vendredi", "On part sur Flutter");
	}

	// ---------------------------------------------------------------- l'appel WebRTC du cercle (#157)

	private static final String SDP = "v=0\\r\\no=- 1 2 IN IP4 127.0.0.1\\r\\ns=-\\r\\n";

	private MvcTestResult presence(Cercle c, UtilisateurConnecte qui) {
		return envoyer("POST", salon(c) + "/appel/presence", qui, null);
	}

	private MvcTestResult signal(Cercle c, UtilisateurConnecte de, UtilisateurConnecte a, String type, String sdp) {
		return envoyer("POST", salon(c) + "/appel/signaux", de, "{\"a\":\"" + a.id() + "\",\"type\":\"" + type + "\",\"sdp\":\"" + sdp + "\"}");
	}

	@Test
	void l_appel_du_cercle_relie_les_membres_deux_a_deux_et_se_termine_quand_tous_partent() {
		Cercle c = cercleTire();
		UtilisateurConnecte awa = c.createur();
		UtilisateurConnecte kofi = c.membres().get(1);
		UtilisateurConnecte fatou = c.membres().get(2);
		assertThat(presence(c, awa)).hasStatus(HttpStatus.NOT_FOUND).bodyJson().extractingPath("$.code").isEqualTo("pas_d_appel");

		assertThat(envoyer("POST", salon(c) + "/appel", awa, null)).hasStatus(HttpStatus.CREATED);
		assertThat(presence(c, awa)).hasStatusOk().bodyJson().extractingPath("$.presents.length()").isEqualTo(1);
		var p = presence(c, kofi);
		assertThat(p).hasStatusOk().bodyJson().extractingPath("$.presents[0].personne.nom").isEqualTo("Awa");
		assertThat(p).bodyJson().extractingPath("$.presents[1].personne.nom").isEqualTo("Kofi");
		assertThat(p.getResponse().getHeader("Cache-Control")).contains("no-store");
		assertThat(lire(salon(c), fatou)).bodyJson().extractingPath("$.appel.presents").isEqualTo(2);

		// Kofi, arrivé le dernier, envoie l'offre à Awa ; elle la lit une seule fois, puis répond.
		assertThat(signal(c, kofi, awa, "offre", SDP)).hasStatus(HttpStatus.NO_CONTENT);
		var boite = presence(c, awa);
		assertThat(boite).bodyJson().extractingPath("$.signaux[0].de").isEqualTo(kofi.id().toString());
		assertThat(boite).bodyJson().extractingPath("$.signaux[0].type").isEqualTo("offre");
		assertThat(presence(c, awa)).bodyJson().extractingPath("$.signaux.length()").isEqualTo(0);
		assertThat(signal(c, awa, kofi, "reponse", SDP)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(presence(c, kofi)).bodyJson().extractingPath("$.signaux[0].type").isEqualTo("reponse");

		// Personne d'autre : ni un signal vers un membre absent de l'appel, ni un SDP qui n'en est pas un, ni un inconnu.
		assertThat(signal(c, kofi, fatou, "offre", SDP)).hasStatus(HttpStatus.CONFLICT)
			.bodyJson().extractingPath("$.code").isEqualTo("pas_dans_l_appel");
		assertThat(signal(c, kofi, awa, "offre", "pas un sdp")).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(signal(c, kofi, awa, "autre", SDP)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(presence(c, utilisateur("Curieux"))).hasStatus(HttpStatus.FORBIDDEN);

		// Chacun part : le dernier termine l'appel.
		assertThat(envoyer("DELETE", salon(c) + "/appel/presence", kofi, null)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(lire(salon(c), awa)).bodyJson().extractingPath("$.appel.presents").isEqualTo(1);
		assertThat(envoyer("DELETE", salon(c) + "/appel/presence", awa, null)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(lire(salon(c), awa)).bodyJson().extractingPath("$.appel").isNull();
	}

	@Test
	void une_personne_qui_ne_donne_plus_signe_de_vie_quitte_l_appel_et_ses_signaux_partent_avec_elle() {
		Cercle c = cercleTire();
		UtilisateurConnecte awa = c.createur();
		UtilisateurConnecte kofi = c.membres().get(1);
		envoyer("POST", salon(c) + "/appel", awa, null);
		presence(c, awa);
		presence(c, kofi);
		signal(c, kofi, awa, "offre", SDP);
		// Kofi ferme son onglet : plus de signe de vie depuis 20 s.
		base.sql("UPDATE appel_cercle_presence SET vu_le = vu_le - make_interval(secs => 20) WHERE utilisateur_id = :k")
			.param("k", kofi.id()).update();
		var p = presence(c, awa);
		assertThat(p).bodyJson().extractingPath("$.presents.length()").isEqualTo(1);
		assertThat(p).bodyJson().extractingPath("$.signaux.length()").isEqualTo(0);
	}

	// ---------------------------------------------------------------- les fichiers du salon (#157)

	private static final byte[] WEBM = { 0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, (byte) 0x9F, 0x42, (byte) 0x86, (byte) 0x81, 0x01 };

	/** Un message vocal, réservé au cercle `cercleId` s'il est donné. */
	private String vocal(UtilisateurConnecte qui, String cercleId) throws Exception {
		var requete = mvc.post().uri("/api/fichiers").multipart()
			.file(new org.springframework.mock.web.MockMultipartFile("fichier", "message-vocal.webm", "audio/webm", WEBM)).param("vocal", "true");
		if (cercleId != null) requete = requete.param("cercleId", cercleId);
		var envoi = requete.with(csrf()).with(oauth2Login().oauth2User(qui)).exchange();
		assertThat(envoi).hasStatus(HttpStatus.CREATED);
		return com.jayway.jsonpath.JsonPath.read(envoi.getResponse().getContentAsString(), "$.id");
	}

	private MvcTestResult message(Cercle c, UtilisateurConnecte qui, String piece) {
		return envoyer("POST", salon(c) + "/messages", qui, "{\"type\":\"texte\",\"texte\":\"\",\"pieces\":[\"" + piece + "\"]}");
	}

	@Test
	void un_fichier_du_salon_ne_se_lit_que_par_les_membres_actuels_du_cercle() throws Exception {
		Cercle c = cercleTire();
		var awa = c.createur();
		var kofi = c.membres().get(1);
		var tiers = utilisateur("Tiers");
		String id = vocal(awa, c.id()), fichier = "/api/fichiers/" + id;
		// Avant l'envoi du message, seule son autrice l'écoute.
		assertThat(lire(fichier, awa)).hasStatusOk();
		assertThat(lire(fichier, kofi)).hasStatus(HttpStatus.NOT_FOUND);

		assertThat(message(c, awa, id)).hasStatus(HttpStatus.CREATED).bodyJson().extractingPath("$.piecesJointes[0].sorte").isEqualTo("audio");
		var lu = lire(fichier, kofi);
		assertThat(lu).hasStatusOk();
		assertThat(lu.getResponse().getHeader("Cache-Control")).isEqualTo("no-store");
		assertThat(lire(fichier, tiers)).hasStatus(HttpStatus.NOT_FOUND);
		// Un membre qui quitte le cercle perd l'accès, même avec l'adresse du fichier.
		base.sql("DELETE FROM cercle_membre WHERE cercle_id = :c AND utilisateur_id = :u")
			.param("c", UUID.fromString(c.id())).param("u", kofi.id()).update();
		assertThat(lire(fichier, kofi)).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void le_salon_refuse_le_fichier_d_un_autre_cercle_mais_accepte_un_fichier_sans_destination() throws Exception {
		Cercle c = cercleTire();
		Cercle autre = cercleTire();
		var awa = c.createur();
		// Un fichier d'Awa réservé à un autre cercle ne peut pas passer dans celui-ci.
		String ailleurs = vocal(awa, c.id());
		base.sql("UPDATE fichier SET cercle_id = :autre WHERE id = :id")
			.param("autre", UUID.fromString(autre.id())).param("id", UUID.fromString(ailleurs)).update();
		assertThat(message(c, awa, ailleurs)).hasStatus(HttpStatus.BAD_REQUEST);
		// Envoyé sans cercleId (une version plus ancienne de l'application) : toujours accepté, et lisible comme avant.
		assertThat(message(c, awa, vocal(awa, null))).hasStatus(HttpStatus.CREATED);
	}

}
