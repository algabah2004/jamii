package dev.jamii.api.reseau;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

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

import com.jayway.jsonpath.JsonPath;

import dev.jamii.api.TestcontainersConfiguration;
import dev.jamii.api.auth.Role;
import dev.jamii.api.auth.UtilisateurConnecte;

/** Les appels WebRTC d'une discussion privée (#157), sur un vrai PostgreSQL. */
@SpringBootTest(properties = { "TURN_URLS=turn:turn.jamii.test:3478?transport=udp,turn:turn.jamii.test:3478?transport=tcp",
		"TURN_SECRET=secret-de-test" })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AppelsTest {

	static final String OFFRE = "v=0\r\no=- 1 2 IN IP4 127.0.0.1\r\ns=-\r\nt=0 0\r\nm=audio 9 UDP/TLS/RTP/SAVPF 111\r\n";

	static final String REPONSE = "v=0\r\no=- 3 4 IN IP4 127.0.0.1\r\ns=-\r\nt=0 0\r\nm=audio 9 UDP/TLS/RTP/SAVPF 111\r\n";

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient base;

	private UtilisateurConnecte utilisateur(String nom) {
		UUID id = UUID.randomUUID();
		Timestamp t = Timestamp.from(Instant.now());
		base.sql("INSERT INTO utilisateur (id, nom, login_github, avatar_url, cree_le, mis_a_jour_le) VALUES (:id, :nom, :login, NULL, :t, :t)")
			.param("id", id).param("nom", nom).param("login", "gh-" + id.toString().substring(0, 8)).param("t", t).update();
		return new UtilisateurConnecte(id, Set.of(Role.MEMBRE));
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

	private static String json(String sdp) {
		return sdp.replace("\r", "\\r").replace("\n", "\\n");
	}

	private String appeler(UtilisateurConnecte de, UtilisateurConnecte a, boolean video) throws Exception {
		MvcTestResult r = poster("/api/conversations/" + a.id() + "/appels", de,
				"{\"offre\":\"" + json(OFFRE) + "\",\"video\":" + video + "}");
		assertThat(r).hasStatus(HttpStatus.CREATED);
		return JsonPath.read(r.getResponse().getContentAsString(), "$.id");
	}

	@Test
	void un_appel_sonne_chez_l_autre_qui_decroche_puis_raccroche_et_la_conversation_le_garde() throws Exception {
		UtilisateurConnecte awa = utilisateur("Awa");
		UtilisateurConnecte kofi = utilisateur("Kofi");
		assertThat(lire("/api/appels/entrant", kofi)).hasStatus(HttpStatus.NO_CONTENT);

		String id = appeler(awa, kofi, true);

		// Kofi voit l'appel qui sonne, avec l'offre et le nom d'Awa ; Awa ne reçoit jamais sa propre offre.
		MvcTestResult entrant = lire("/api/appels/entrant", kofi);
		assertThat(entrant).hasStatusOk();
		assertThat(entrant).bodyJson().extractingPath("$.id").isEqualTo(id);
		assertThat(entrant).bodyJson().extractingPath("$.personne.nom").isEqualTo("Awa");
		assertThat(entrant).bodyJson().extractingPath("$.video").isEqualTo(true);
		assertThat(entrant).bodyJson().extractingPath("$.offre").isEqualTo(OFFRE);
		assertThat(entrant.getResponse().getHeader("Cache-Control")).contains("no-store");
		assertThat(lire("/api/appels/" + id, awa)).bodyJson().extractingPath("$.offre").isNull();

		// Kofi décroche : sa réponse va à Awa, et à elle seule.
		assertThat(poster("/api/appels/" + id + "/reponse", kofi, "{\"reponse\":\"" + json(REPONSE) + "\"}")).hasStatusOk()
			.bodyJson().extractingPath("$.statut").isEqualTo("en_cours");
		assertThat(lire("/api/appels/" + id, awa)).bodyJson().extractingPath("$.reponse").isEqualTo(REPONSE);
		assertThat(lire("/api/appels/" + id, kofi)).bodyJson().extractingPath("$.reponse").isNull();
		assertThat(lire("/api/appels/entrant", kofi)).hasStatus(HttpStatus.NO_CONTENT);

		// Raccrocher deux fois ne fait rien de plus ; les SDP (adresses réseau) sont effacées.
		assertThat(poster("/api/appels/" + id + "/fin", kofi, null)).bodyJson().extractingPath("$.statut").isEqualTo("termine");
		assertThat(poster("/api/appels/" + id + "/fin", awa, null)).bodyJson().extractingPath("$.statut").isEqualTo("termine");
		assertThat(base.sql("SELECT count(*) FROM appel_prive WHERE id = :id AND offre IS NULL AND reponse IS NULL")
			.param("id", UUID.fromString(id)).query(Integer.class).single()).isEqualTo(1);

		// La conversation garde une carte de l'appel.
		MvcTestResult fil = lire("/api/conversations/" + kofi.id() + "/messages", awa);
		assertThat(fil).bodyJson().extractingPath("$.elements[0].appel.id").isEqualTo(id);
		assertThat(fil).bodyJson().extractingPath("$.elements[0].appel.statut").isEqualTo("termine");
		assertThat(fil).bodyJson().extractingPath("$.elements[0].appel.enCours").isEqualTo(false);
		assertThat(fil).bodyJson().extractingPath("$.elements[0].appel.url").isNull();
	}

	@Test
	void refuser_annuler_et_appel_manque_apres_45_secondes() throws Exception {
		UtilisateurConnecte awa = utilisateur("Awa");
		UtilisateurConnecte kofi = utilisateur("Kofi");

		String refuse = appeler(awa, kofi, false);
		assertThat(poster("/api/appels/" + refuse + "/refus", kofi, null)).bodyJson().extractingPath("$.statut").isEqualTo("refuse");
		assertThat(poster("/api/appels/" + refuse + "/reponse", kofi, "{\"reponse\":\"" + json(REPONSE) + "\"}"))
			.hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.code").isEqualTo("appel_fini");

		String annule = appeler(awa, kofi, false);
		assertThat(poster("/api/appels/" + annule + "/fin", awa, null)).bodyJson().extractingPath("$.statut").isEqualTo("annule");

		String manque = appeler(awa, kofi, false);
		base.sql("UPDATE appel_prive SET cree_le = :t WHERE id = :id")
			.param("t", Timestamp.from(Instant.now().minusSeconds(60))).param("id", UUID.fromString(manque)).update();
		assertThat(lire("/api/appels/entrant", kofi)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(lire("/api/appels/" + manque, awa)).bodyJson().extractingPath("$.statut").isEqualTo("manque");
	}

	@Test
	void seule_la_personne_appelee_decroche_et_personne_d_autre_ne_voit_l_appel() throws Exception {
		UtilisateurConnecte awa = utilisateur("Awa");
		UtilisateurConnecte kofi = utilisateur("Kofi");
		UtilisateurConnecte curieux = utilisateur("Curieux");
		String id = appeler(awa, kofi, false);

		assertThat(lire("/api/appels/" + id, curieux)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(poster("/api/appels/" + id + "/fin", curieux, null)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(lire("/api/appels/entrant", curieux)).hasStatus(HttpStatus.NO_CONTENT);
		assertThat(poster("/api/appels/" + id + "/reponse", awa, "{\"reponse\":\"" + json(REPONSE) + "\"}"))
			.hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.code").isEqualTo("pas_appele");
		assertThat(lire("/api/appels/pas-un-uuid", awa)).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void l_offre_est_validee_et_on_ne_s_appelle_pas_soi_meme() {
		UtilisateurConnecte awa = utilisateur("Awa");
		UtilisateurConnecte kofi = utilisateur("Kofi");
		assertThat(poster("/api/conversations/" + kofi.id() + "/appels", awa, "{\"offre\":\"pas une offre\"}"))
			.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(poster("/api/conversations/" + kofi.id() + "/appels", awa, "{\"offre\":\"v=0" + "x".repeat(Appels.SDP_MAX) + "\"}"))
			.hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(poster("/api/conversations/" + kofi.id() + "/appels", awa, null)).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(poster("/api/conversations/" + awa.id() + "/appels", awa, "{\"offre\":\"" + json(OFFRE) + "\"}"))
			.hasStatus(HttpStatus.CONFLICT);
		assertThat(poster("/api/conversations/" + UUID.randomUUID() + "/appels", awa, "{\"offre\":\"" + json(OFFRE) + "\"}"))
			.hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void les_identifiants_turn_sont_temporaires_et_signes_pour_coturn() throws Exception {
		UtilisateurConnecte awa = utilisateur("Awa");
		MvcTestResult r = lire("/api/appels/ice", awa);
		assertThat(r).hasStatusOk();
		assertThat(r.getResponse().getHeader("Cache-Control")).contains("no-store");
		String corps = r.getResponse().getContentAsString();
		assertThat((Boolean) JsonPath.read(corps, "$.relais")).isTrue();
		List<String> stun = JsonPath.read(corps, "$.iceServers[0].urls");
		assertThat(stun).containsExactly("stun:stun.l.google.com:19302");
		String nom = JsonPath.read(corps, "$.iceServers[1].username");
		String motDePasse = JsonPath.read(corps, "$.iceServers[1].credential");

		// <expiration>:<utilisateur>, signé en HMAC-SHA1 avec le secret partagé avec coturn (use-auth-secret).
		long expiration = Long.parseLong(nom.substring(0, nom.indexOf(':')));
		assertThat(nom).endsWith(":" + awa.id());
		assertThat(expiration).isBetween(Instant.now().plusSeconds(3 * 3600).getEpochSecond(), Instant.now().plusSeconds(5 * 3600).getEpochSecond());
		Mac mac = Mac.getInstance("HmacSHA1");
		mac.init(new SecretKeySpec("secret-de-test".getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
		assertThat(motDePasse).isEqualTo(Base64.getEncoder().encodeToString(mac.doFinal(nom.getBytes(StandardCharsets.UTF_8))));
	}

	@Test
	void sans_secret_turn_seulement_le_stun() {
		var sansRelais = new ServeursIce(List.of("stun:stun.example.test:3478"), List.of("turn:x:3478"), "",
				Clock.fixed(Instant.parse("2026-11-16T10:00:00Z"), ZoneOffset.UTC));
		assertThat(sansRelais.relaisConfigure()).isFalse();
		assertThat(sansRelais.pour(UUID.randomUUID())).hasSize(1).first()
			.satisfies(s -> assertThat(s.username()).isNull());
	}

}
