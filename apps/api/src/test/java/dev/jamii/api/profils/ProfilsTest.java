package dev.jamii.api.profils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;

import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;

import dev.jamii.api.TestcontainersConfiguration;
import dev.jamii.api.auth.Role;
import dev.jamii.api.auth.UtilisateurConnecte;
import dev.jamii.api.systalink.ChiffresDesModules;

@SpringBootTest(properties = "jamii.github.source=simule")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ProfilsTest {

	private static final AtomicLong GITHUB_ID = new AtomicLong(50_000);

	private static final MediaType MERGE_PATCH = MediaType.parseMediaType("application/merge-patch+json");

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	ChiffresDesModules.PaysDesInscrits paysDesInscrits;

	// ---------- Génération ----------

	@Test
	void sans_profil_le_front_propose_de_le_generer() {
		MvcTestResult reponse = mvc.get().uri("/api/me/profil").with(compte("sans-profil")).exchange();
		assertThat(reponse).hasStatus(HttpStatus.NOT_FOUND).hasContentType("application/problem+json");
		assertThat(reponse).bodyJson().extractingPath("$.code").isEqualTo("profil_non_genere");
	}

	@Test
	void genere_le_profil_depuis_github_et_le_garde_prive() {
		RequestPostProcessor awa = compte("awa-dev");

		MvcTestResult lancement = mvc.post().uri("/api/me/profil/generation").with(awa).with(csrf()).exchange();
		assertThat(lancement).hasStatus(HttpStatus.ACCEPTED).bodyJson().extractingPath("$.statut").isEqualTo("en_cours");

		String profil = attendreFinGeneration(awa);
		assertThat(JsonPath.<String>read(profil, "$.generation.statut")).isEqualTo("terminee");
		assertThat(JsonPath.<Boolean>read(profil, "$.publie")).as("privé par défaut").isFalse();
		assertThat(JsonPath.<String>read(profil, "$.loginGithub")).isEqualTo("awa-dev");
		assertThat(JsonPath.<String>read(profil, "$.langages[0].nom")).isEqualTo("Dart");
		assertThat(JsonPath.<java.util.List<String>>read(profil, "$.competences[*].competence")).contains("flutter",
				"postgresql");
		assertThat(JsonPath.<java.util.List<String>>read(profil, "$.depotsGithub[*].nom")).doesNotContain("flutter-samples");
		assertThat(profil).doesNotContain("email");
	}

	@Test
	void compte_cree_par_email_ne_peut_pas_generer() {
		var pme = connecte(creerCompte("Boutique Ndèye", null));
		MvcTestResult reponse = mvc.post().uri("/api/me/profil/generation").with(pme).with(csrf()).exchange();
		assertThat(reponse).hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.code").isEqualTo("github_non_relie");
	}

	@Test
	void github_en_panne_ou_compte_disparu_donne_un_echec_explique() {
		var panne = compte("panne");
		mvc.post().uri("/api/me/profil/generation").with(panne).with(csrf()).exchange();
		assertThat(JsonPath.<String>read(attendreFinGeneration(panne), "$.generation.erreur"))
			.isEqualTo("github_indisponible");

		var disparu = compte("introuvable");
		mvc.post().uri("/api/me/profil/generation").with(disparu).with(csrf()).exchange();
		assertThat(JsonPath.<String>read(attendreFinGeneration(disparu), "$.generation.erreur")).isEqualTo("github_refus");
	}

	@Test
	void generation_limitee_a_5_par_heure() {
		var presse = compte("presse");
		for (int i = 0; i < GenerationProfils.LIMITE_PAR_HEURE; i++) {
			assertThat(mvc.post().uri("/api/me/profil/generation").with(presse).with(csrf())).hasStatus(HttpStatus.ACCEPTED);
			attendreFinGeneration(presse);
		}
		MvcTestResult refus = mvc.post().uri("/api/me/profil/generation").with(presse).with(csrf()).exchange();
		assertThat(refus).hasStatus(HttpStatus.TOO_MANY_REQUESTS).headers().containsHeader("Retry-After");
	}

	@Test
	void ecriture_sans_jeton_csrf_refusee() {
		assertThat(mvc.post().uri("/api/me/profil/generation").with(compte("sans-csrf"))).hasStatus(HttpStatus.FORBIDDEN);
	}

	// ---------- Modification ----------

	@Test
	void modifie_ce_que_je_declare_sans_que_la_regeneration_l_efface() {
		var kofi = profilGenere("kofi");

		MvcTestResult modif = patch(kofi, """
				{"bio":"  Dev mobile à Accra  ","pays":"GH","ville":"Accra","disponibilite":"occupe",
				 "heuresParSemaine":8,"competencesDeclarees":["React.js","UX Design"],"version":%d}"""
			.formatted(version(kofi)));
		assertThat(modif).hasStatusOk();
		assertThat(modif).bodyJson().extractingPath("$.bio").isEqualTo("Dev mobile à Accra");
		assertThat(modif).bodyJson().extractingPath("$.pays").isEqualTo("GH");

		mvc.post().uri("/api/me/profil/generation").with(kofi).with(csrf()).exchange();
		String apres = attendreFinGeneration(kofi);
		assertThat(JsonPath.<String>read(apres, "$.bio")).isEqualTo("Dev mobile à Accra");
		assertThat(JsonPath.<java.util.List<String>>read(apres, "$.competences[?(@.source=='declaree')].competence"))
			.containsExactlyInAnyOrder("react", "ux-design");
	}

	@Test
	void null_efface_et_absent_ne_change_pas() {
		var fatou = profilGenere("fatou");
		patch(fatou, "{\"ville\":\"Dakar\",\"pays\":\"SN\",\"version\":%d}".formatted(version(fatou)));

		MvcTestResult reponse = patch(fatou, "{\"ville\":null,\"version\":%d}".formatted(version(fatou)));
		assertThat(reponse).bodyJson().extractingPath("$.ville").isNull();
		assertThat(reponse).bodyJson().extractingPath("$.pays").isEqualTo("SN");
	}

	@Test
	void deux_modifications_concurrentes_la_seconde_recoit_409() {
		var moussa = profilGenere("moussa");
		int v = version(moussa);
		assertThat(patch(moussa, "{\"bio\":\"un\",\"version\":%d}".formatted(v))).hasStatusOk();
		MvcTestResult seconde = patch(moussa, "{\"bio\":\"deux\",\"version\":%d}".formatted(v));
		assertThat(seconde).hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.code")
			.isEqualTo("modification_concurrente");
	}

	@Test
	void entree_invalide_liste_tous_les_champs_et_ne_change_rien() {
		var aicha = profilGenere("aicha");
		MvcTestResult reponse = patch(aicha, """
				{"pays":"XX","heuresParSemaine":90,"disponibilite":"peut-etre","competencesDeclarees":["ok","<b>"],
				 "email":"pirate@exemple.org","version":%d}""".formatted(version(aicha)));

		assertThat(reponse).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.code").isEqualTo("validation");
		assertThat(reponse).bodyJson().extractingPath("$.erreurs[*].champ").asArray().containsExactlyInAnyOrder("email",
				"pays", "heuresParSemaine", "disponibilite", "competencesDeclarees[1]");
		assertThat(mvc.get().uri("/api/me/profil").with(aicha)).bodyJson().extractingPath("$.pays").isNull();
	}

	// ---------- Carte de compétences proposée par l'IA ----------

	@Test
	void la_carte_proposee_reste_privee_jusqu_a_ce_que_je_garde_une_competence() {
		UUID idNana = creerCompte("Nana", "nana");
		var nana = connecte(idNana);
		String profil = contenu(mvc.get().uri("/api/me/profil").with(profilGenere(nana)).exchange());

		assertThat(JsonPath.<java.util.List<String>>read(profil, "$.competencesProposees[*].competence"))
			.containsExactly("dart", "typescript", "html");
		assertThat(JsonPath.<String>read(profil, "$.competencesProposees[0].niveau")).isEqualTo("intermediaire");
		assertThat(JsonPath.<java.util.List<String>>read(profil, "$.competencesProposees[0].preuves"))
			.as("les preuves sont des dépôts publics, jamais le fork").containsExactly("marche-facile", "agri-alertes");
		assertThat(JsonPath.<java.util.List<String>>read(profil, "$.competences[?(@.source=='ia')].competence")).isEmpty();

		MvcTestResult garde = patch(nana, "{\"competencesIaRetenues\":[\"dart\"],\"version\":%d}".formatted(version(nana)));
		assertThat(garde).hasStatusOk();
		mvc.post().uri("/api/me/profil/publier").with(nana).with(csrf()).exchange();

		String public_ = contenu(mvc.get().uri("/api/profils/{id}", idNana).with(compte("visiteur-carte")).exchange());
		assertThat(public_).doesNotContain("competencesProposees", "typescript\",\"niveau");
		assertThat(JsonPath.<java.util.List<String>>read(public_, "$.competences[?(@.source=='ia')].competence"))
			.containsExactly("dart");
		assertThat(JsonPath.<java.util.List<String>>read(public_, "$.competences[?(@.source=='ia')].niveau"))
			.containsExactly("intermediaire");
		assertThat(JsonPath.<java.util.List<String>>read(public_, "$.competences[?(@.source=='github')].preuves"))
			.as("niveau et preuves seulement pour ia").isEmpty();

		mvc.post().uri("/api/me/profil/generation").with(nana).with(csrf()).exchange();
		assertThat(JsonPath.<java.util.List<String>>read(attendreFinGeneration(nana),
				"$.competences[?(@.source=='ia')].competence")).as("une nouvelle génération garde mon choix").containsExactly("dart");

		assertThat(patch(nana, "{\"competencesIaRetenues\":[],\"version\":%d}".formatted(version(nana)))).hasStatusOk();
		assertThat(JsonPath.<java.util.List<String>>read(contenu(mvc.get().uri("/api/me/profil").with(nana).exchange()),
				"$.competences[?(@.source=='ia')]")).isEmpty();
	}

	@Test
	void on_ne_garde_que_ce_que_l_ia_a_propose() {
		var omar = profilGenere("omar");
		MvcTestResult refus = patch(omar,
				"{\"competencesIaRetenues\":[\"dart\",\"cobol\"],\"version\":%d}".formatted(version(omar)));
		assertThat(refus).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(refus).bodyJson().extractingPath("$.erreurs[*].champ").asArray().containsExactly("competencesIaRetenues[1]");
		assertThat(refus).bodyJson().extractingPath("$.erreurs[0].code").isEqualTo("non_proposee");
	}

	// ---------- Publication et confidentialité ----------

	@Test
	void un_profil_non_publie_est_invisible_pour_les_autres() {
		UUID idTariq = creerCompte("Tariq", "tariq");
		var tariq = connecte(idTariq);
		profilGenere(tariq);
		var autre = compte("curieux");

		assertThat(mvc.get().uri("/api/profils/{id}", idTariq).with(autre)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(mvc.get().uri("/api/profils/{id}", idTariq).with(tariq)).as("moi, je le vois").hasStatusOk();

		assertThat(mvc.post().uri("/api/me/profil/publier").with(tariq).with(csrf())).hasStatusOk();
		MvcTestResult publie = mvc.get().uri("/api/profils/{id}", idTariq).with(autre).exchange();
		assertThat(publie).hasStatusOk().bodyText().doesNotContain("email", "version", "publie");

		mvc.post().uri("/api/me/profil/retirer").with(tariq).with(csrf()).exchange();
		assertThat(mvc.get().uri("/api/profils/{id}", idTariq).with(autre)).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void identifiant_invalide_repond_404() {
		assertThat(mvc.get().uri("/api/profils/pas-un-uuid").with(compte("x"))).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void supprimer_le_compte_supprime_le_profil() {
		UUID id = creerCompte("Éphémère", "ephemere");
		profilGenere(connecte(id));
		jdbc.update("DELETE FROM utilisateur WHERE id = ?", id);
		assertThat(jdbc.queryForObject("SELECT count(*) FROM profil WHERE utilisateur_id = ?", Integer.class, id)).isZero();
	}

	// ---------- Recherche de coéquipiers ----------

	@Test
	void cherche_par_competences_pays_et_disponibilite_parmi_les_profils_publies() {
		String marque = "test-" + UUID.randomUUID().toString().substring(0, 8);
		UUID dakar = profilPublie("Dakar", "SN", "disponible", marque, "kotlin");
		UUID nairobi = profilPublie("Nairobi", "KE", "occupe", marque, "kotlin");
		UUID lagos = profilPublie("Lagos", "NG", "disponible", marque);
		UUID prive = creerCompte("Privé", "prive-" + marque);
		var vuPrive = connecte(prive);
		profilGenere(vuPrive);
		patch(vuPrive, "{\"competencesDeclarees\":[\"%s\"],\"version\":%d}".formatted(marque, version(vuPrive)));

		var moi = connecte(dakar);
		assertThat(chercher(moi, "competence=" + marque)).as("ni moi, ni le profil privé")
			.containsExactly(lagos.toString(), nairobi.toString());

		var autre = compte("recruteur-" + marque);
		assertThat(chercher(autre, "competence=" + marque)).as("disponibles d'abord, puis les plus récents")
			.containsExactly(lagos.toString(), dakar.toString(), nairobi.toString())
			.doesNotContain(prive.toString());
		assertThat(chercher(autre, "competence=" + marque + "&competence=Kotlin")).as("toutes les compétences")
			.containsExactly(dakar.toString(), nairobi.toString());
		assertThat(chercher(autre, "competence=" + marque + "&pays=KE&pays=NG"))
			.containsExactly(lagos.toString(), nairobi.toString());
		assertThat(chercher(autre, "competence=" + marque + "&disponibilite=occupe")).containsExactly(nairobi.toString());
	}

	@Test
	void le_tableau_de_bord_compte_les_inscrits_par_pays_declare() {
		long avant = paysDesInscrits.parPays().getOrDefault("GH", 0L);
		String marque = "pays-" + UUID.randomUUID().toString().substring(0, 8);
		profilPublie("Accra", "GH", "disponible", marque);
		var prive = compte("prive-" + marque);
		profilGenere(prive);
		patch(prive, "{\"pays\":\"GH\",\"version\":%d}".formatted(version(prive)));
		var sansPays = compte("sans-pays-" + marque);
		profilGenere(sansPays);

		assertThat(paysDesInscrits.parPays()).as("publiés ou non, ceux sans pays ne comptent pas")
			.containsEntry("GH", avant + 2)
			.doesNotContainKey(null);
	}

	@Test
	void la_recherche_se_parcourt_page_par_page() {
		String marque = "page-" + UUID.randomUUID().toString().substring(0, 8);
		for (int i = 0; i < 3; i++) {
			profilPublie("Dev " + i, "CI", "disponible", marque);
		}
		var moi = compte("lecteur-" + marque);
		String page1 = contenu(mvc.get().uri("/api/profils?limite=2&competence=" + marque).with(moi).exchange());
		String curseur = JsonPath.read(page1, "$.curseurSuivant");
		assertThat(JsonPath.<java.util.List<?>>read(page1, "$.elements")).hasSize(2);

		MvcTestResult page2 = mvc.get().uri("/api/profils?limite=2&competence=" + marque + "&curseur=" + curseur).with(moi)
			.exchange();
		assertThat(page2).bodyJson().extractingPath("$.elements").asArray().hasSize(1);
		assertThat(page2).bodyJson().extractingPath("$.curseurSuivant").isNull();
	}

	@Test
	void recherche_refuse_les_filtres_invalides() {
		MvcTestResult reponse = mvc.get().uri("/api/profils?pays=ZZ&disponibilite=bof&limite=500")
			.with(compte("filtres")).exchange();
		assertThat(reponse).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(mvc.get().uri("/api/profils?curseur=n-importe-quoi").with(compte("curseur")))
			.hasStatus(HttpStatus.BAD_REQUEST);
	}

	// ---------- Outils ----------

	private UUID profilPublie(String nom, String pays, String disponibilite, String... competences) {
		UUID id = creerCompte(nom, "dev-" + GITHUB_ID.get());
		var p = connecte(id);
		profilGenere(p);
		String liste = String.join("\",\"", competences);
		assertThat(patch(p, "{\"pays\":\"%s\",\"disponibilite\":\"%s\",\"competencesDeclarees\":[\"%s\"],\"version\":%d}"
			.formatted(pays, disponibilite, liste, version(p)))).hasStatusOk();
		mvc.post().uri("/api/me/profil/publier").with(p).with(csrf()).exchange();
		return id;
	}

	private java.util.List<String> chercher(RequestPostProcessor qui, String filtres) {
		MvcTestResult reponse = mvc.get().uri("/api/profils?" + filtres).with(qui).exchange();
		assertThat(reponse).hasStatusOk();
		return JsonPath.read(contenu(reponse), "$.elements[*].utilisateurId");
	}

	private RequestPostProcessor profilGenere(String login) {
		return profilGenere(compte(login));
	}

	private RequestPostProcessor profilGenere(RequestPostProcessor qui) {
		assertThat(mvc.post().uri("/api/me/profil/generation").with(qui).with(csrf())).hasStatus(HttpStatus.ACCEPTED);
		attendreFinGeneration(qui);
		return qui;
	}

	private String attendreFinGeneration(RequestPostProcessor qui) {
		String[] profil = new String[1];
		await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(50)).until(() -> {
			profil[0] = contenu(mvc.get().uri("/api/me/profil").with(qui).exchange());
			return !"en_cours".equals(JsonPath.read(profil[0], "$.generation.statut"));
		});
		return profil[0];
	}

	private int version(RequestPostProcessor qui) {
		return JsonPath.read(contenu(mvc.get().uri("/api/me/profil").with(qui).exchange()), "$.version");
	}

	private MvcTestResult patch(RequestPostProcessor qui, String json) {
		return mvc.patch().uri("/api/me/profil").with(qui).with(csrf()).contentType(MERGE_PATCH).content(json).exchange();
	}

	private RequestPostProcessor compte(String login) {
		return connecte(creerCompte(login, login));
	}

	private RequestPostProcessor connecte(UUID id) {
		return oauth2Login().oauth2User(new UtilisateurConnecte(id, Set.of(Role.MEMBRE)));
	}

	/** Un compte en base, comme après une connexion GitHub (login) ou par e-mail (login null). */
	private UUID creerCompte(String nom, String login) {
		UUID id = UUID.randomUUID();
		Long githubId = login == null ? null : GITHUB_ID.incrementAndGet();
		jdbc.update("""
				INSERT INTO utilisateur (id, nom, github_id, login_github, cree_le, mis_a_jour_le)
				VALUES (?, ?, ?, ?, now(), now())""", id, nom, githubId, login);
		jdbc.update("INSERT INTO utilisateur_role (utilisateur_id, role) VALUES (?, 'membre')", id);
		return id;
	}

	private static String contenu(MvcTestResult reponse) {
		try {
			return reponse.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
		}
		catch (java.io.UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
	}

}
