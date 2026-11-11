package dev.jamii.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import dev.jamii.api.TestcontainersConfiguration;

/** Les deux routes de la connexion par lien e-mail, sur un vrai PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, LienEmailTest.EnvoiCapture.class })
class LienEmailTest {

	record Message(String destinataire, String objet, String texte) {
	}

	/** Remplace l'envoi : garde les messages, pour retrouver le lien comme le ferait la personne. */
	@TestConfiguration(proxyBeanMethods = false)
	static class EnvoiCapture {

		static final List<Message> ENVOYES = new ArrayList<>();

		@Bean
		@Primary
		EnvoiEmail envoiCapture() {
			return (destinataire, objet, texte) -> ENVOYES.add(new Message(destinataire, objet, texte));
		}

	}

	private static final AtomicInteger COMPTEUR = new AtomicInteger();

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient base;

	@Autowired
	JetonsConnexion jetons;

	@BeforeEach
	void viderLesMessages() {
		EnvoiCapture.ENVOYES.clear();
	}

	/** Chaque test a sa propre adresse IP et sa propre adresse e-mail : les limites ne se mélangent pas. */
	private static String ip() {
		return "10.1." + (COMPTEUR.incrementAndGet() / 250) + "." + (COMPTEUR.get() % 250 + 1);
	}

	private static String adresse() {
		return "personne" + UUID.randomUUID() + "@example.org";
	}

	private MvcTestResult demander(String corps, String ip) {
		return mvc.post().uri("/api/auth/lien-email").contentType(MediaType.APPLICATION_JSON).content(corps).with(csrf())
			.with(requete -> {
				requete.setRemoteAddr(ip);
				return requete;
			}).exchange();
	}

	private MvcTestResult confirmer(String jeton) {
		var requete = mvc.post().uri("/api/auth/lien-email/confirmation").contentType(MediaType.APPLICATION_JSON)
			.content("{\"jeton\":\"" + jeton + "\"}").with(csrf());
		return requete.exchange();
	}

	private static String corps(MvcTestResult reponse) {
		try {
			return reponse.getResponse().getContentAsString();
		}
		catch (java.io.UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
	}

	private static String jetonDuDernierMessage() {
		Matcher m = Pattern.compile("jeton=([A-Za-z0-9_-]+)").matcher(EnvoiCapture.ENVOYES.getLast().texte());
		assertThat(m.find()).isTrue();
		return m.group(1);
	}

	@Test
	void la_demande_envoie_un_lien_et_ne_stocke_que_l_empreinte() {
		String email = adresse();

		assertThat(demander("{\"email\":\"" + email + "\"}", ip())).hasStatus(HttpStatus.ACCEPTED);

		assertThat(EnvoiCapture.ENVOYES).hasSize(1);
		assertThat(EnvoiCapture.ENVOYES.getFirst().destinataire()).isEqualTo(email);
		String jeton = jetonDuDernierMessage();
		assertThat(jeton).hasSizeGreaterThanOrEqualTo(32);
		assertThat(base.sql("SELECT count(*) FROM jeton_connexion WHERE empreinte = :j").param("j", jeton)
			.query(Long.class).single()).as("le jeton en clair n'est jamais stocké").isZero();
		assertThat(base.sql("SELECT count(*) FROM jeton_connexion WHERE empreinte = :j")
			.param("j", JetonsConnexion.empreinte(jeton)).query(Long.class).single()).isEqualTo(1);
	}

	@Test
	void la_reponse_est_la_meme_que_l_adresse_soit_inscrite_ou_non() {
		String connue = adresse();
		assertThat(confirmerNouveauCompte(connue)).hasStatus(HttpStatus.NO_CONTENT);

		MvcTestResult pourConnue = demander("{\"email\":\"" + connue + "\"}", ip());
		MvcTestResult pourInconnue = demander("{\"email\":\"" + adresse() + "\"}", ip());

		assertThat(pourConnue).hasStatus(HttpStatus.ACCEPTED);
		assertThat(pourInconnue).hasStatus(HttpStatus.ACCEPTED);
		assertThat(corps(pourConnue)).isEqualTo(corps(pourInconnue));
	}

	@Test
	void adresse_invalide_repond_400() {
		for (String mauvaise : List.of("", "   ", "pas-une-adresse", "a@b", "a b@example.org", "@example.org",
				"a@example..org", "x".repeat(250) + "@example.org")) {
			MvcTestResult reponse = demander("{\"email\":\"" + mauvaise + "\"}", ip());
			assertThat(reponse).as(mauvaise).hasStatus(HttpStatus.BAD_REQUEST);
			assertThat(reponse).bodyJson().extractingPath("$.code").isEqualTo("validation");
		}
		assertThat(demander("{}", ip())).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(EnvoiCapture.ENVOYES).isEmpty();
	}

	@Test
	void le_chemin_de_retour_douteux_est_ignore() {
		demander("{\"email\":\"" + adresse() + "\",\"retour\":\"//evil.example/x\"}", ip());
		assertThat(EnvoiCapture.ENVOYES.getLast().texte()).doesNotContain("retour=").doesNotContain("evil");

		demander("{\"email\":\"" + adresse() + "\",\"retour\":\"/projets/mes-projets\"}", ip());
		assertThat(EnvoiCapture.ENVOYES.getLast().texte()).contains("&retour=%2Fprojets%2Fmes-projets");
	}

	@Test
	void sixieme_demande_pour_la_meme_adresse_est_refusee_en_429() {
		String corps = "{\"email\":\"" + adresse() + "\"}";
		for (int i = 0; i < LiensEmail.MAX_PAR_EMAIL; i++) {
			assertThat(demander(corps, ip())).hasStatus(HttpStatus.ACCEPTED);
		}

		MvcTestResult refus = demander(corps, ip());

		assertThat(refus).hasStatus(HttpStatus.TOO_MANY_REQUESTS).hasContentType("application/problem+json");
		assertThat(refus).bodyJson().extractingPath("$.code").isEqualTo("trop_de_requetes");
		assertThat(refus.getResponse().getHeader("Retry-After")).isEqualTo("3600");
		assertThat(EnvoiCapture.ENVOYES).hasSize(LiensEmail.MAX_PAR_EMAIL);
	}

	@Test
	void vingt_et_unieme_demande_depuis_la_meme_ip_est_refusee_en_429() {
		String ip = ip();
		for (int i = 0; i < LiensEmail.MAX_PAR_IP; i++) {
			assertThat(demander("{\"email\":\"" + adresse() + "\"}", ip)).hasStatus(HttpStatus.ACCEPTED);
		}

		MvcTestResult refus = demander("{\"email\":\"" + adresse() + "\"}", ip);

		assertThat(refus).hasStatus(HttpStatus.TOO_MANY_REQUESTS);
		assertThat(Long.parseLong(refus.getResponse().getHeader("Retry-After"))).isBetween(1L, 3600L);
	}

	@Test
	void sans_jeton_csrf_la_demande_et_la_confirmation_sont_refusees() {
		assertThat(mvc.post().uri("/api/auth/lien-email").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + adresse() + "\"}")).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(mvc.post().uri("/api/auth/lien-email/confirmation").contentType(MediaType.APPLICATION_JSON)
			.content("{\"jeton\":\"" + "a".repeat(43) + "\"}")).hasStatus(HttpStatus.FORBIDDEN);
	}

	/** Demande un lien pour cette adresse puis le confirme, comme le ferait la personne. */
	private MvcTestResult confirmerNouveauCompte(String email) {
		demander("{\"email\":\"" + email + "\"}", ip());
		return confirmer(jetonDuDernierMessage());
	}

	@Test
	void la_confirmation_cree_un_membre_au_nom_neutre() {
		String email = adresse();
		demander("{\"email\":\"" + email + "\"}", ip());

		assertThat(confirmer(jetonDuDernierMessage())).hasStatus(HttpStatus.NO_CONTENT);

		var compte = base.sql("SELECT nom, github_id FROM utilisateur WHERE email = :e").param("e", email)
			.query((rs, n) -> rs.getString("nom") + "|" + rs.getObject("github_id")).single();
		assertThat(compte).as("jamais le début de l'adresse comme nom, et pas de lien avec GitHub").isEqualTo("Nouveau membre|null");
		assertThat(base.sql("SELECT role FROM utilisateur_role WHERE utilisateur_id = (SELECT id FROM utilisateur WHERE email = :e)")
			.param("e", email).query(String.class).list()).containsExactly("membre");
	}

	@Test
	void la_meme_adresse_retrouve_le_meme_compte_quelle_que_soit_la_casse() {
		String email = adresse();
		demander("{\"email\":\"" + email + "\"}", ip());
		confirmer(jetonDuDernierMessage());
		demander("{\"email\":\"  " + email.toUpperCase() + " \"}", ip());
		assertThat(EnvoiCapture.ENVOYES.getLast().destinataire()).isEqualTo(email);

		assertThat(confirmer(jetonDuDernierMessage())).hasStatus(HttpStatus.NO_CONTENT);

		assertThat(base.sql("SELECT count(*) FROM utilisateur WHERE lower(email) = :e").param("e", email)
			.query(Long.class).single()).isEqualTo(1);
	}

	@Test
	void un_lien_ne_sert_qu_une_fois() {
		demander("{\"email\":\"" + adresse() + "\"}", ip());
		String jeton = jetonDuDernierMessage();

		assertThat(confirmer(jeton)).hasStatus(HttpStatus.NO_CONTENT);
		MvcTestResult second = confirmer(jeton);

		assertThat(second).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(second).bodyJson().extractingPath("$.code").isEqualTo("lien_invalide");
		assertThat(second.getResponse().getHeaders("Set-Cookie")).as("pas de session pour un lien refusé")
			.noneMatch(c -> c.contains("SESSION"));
	}

	@Test
	void lien_inconnu_ou_expire_donnent_la_meme_reponse_que_lien_deja_utilise() {
		String expire = jetons.creer(adresse(), Instant.now().minus(JetonsConnexion.VALIDITE).minusSeconds(1));
		String inconnu = "z".repeat(43);

		MvcTestResult reponseExpire = confirmer(expire);
		MvcTestResult reponseInconnu = confirmer(inconnu);

		assertThat(reponseExpire).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(reponseInconnu).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(corps(reponseExpire).replaceAll("\"instance\":\"[^\"]*\"", ""))
			.isEqualTo(corps(reponseInconnu).replaceAll("\"instance\":\"[^\"]*\"", ""));
	}

	@Test
	void jeton_de_mauvaise_forme_repond_400_validation() {
		for (String mauvais : List.of("court", "a".repeat(129))) {
			MvcTestResult reponse = confirmer(mauvais);
			assertThat(reponse).hasStatus(HttpStatus.BAD_REQUEST);
			assertThat(reponse).bodyJson().extractingPath("$.code").isEqualTo("validation");
		}
	}

	@Test
	void les_vieux_jetons_sont_supprimes() {
		String vieux = jetons.creer(adresse(), Instant.now().minus(Duration.ofHours(30)));
		String recent = jetons.creer(adresse(), Instant.now());

		jetons.supprimerAvant(Instant.now().minus(Duration.ofHours(24)));

		assertThat(existe(vieux)).isFalse();
		assertThat(existe(recent)).isTrue();
	}

	private boolean existe(String jeton) {
		return base.sql("SELECT count(*) FROM jeton_connexion WHERE empreinte = :e")
			.param("e", JetonsConnexion.empreinte(jeton)).query(Long.class).single() > 0;
	}

}
