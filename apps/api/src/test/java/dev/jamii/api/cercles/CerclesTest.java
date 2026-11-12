package dev.jamii.api.cercles;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

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
import dev.jamii.api.auth.Role;
import dev.jamii.api.auth.UtilisateurConnecte;

/** Les routes de contracts/cercles.yaml, sur un vrai PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, CerclesTest.DepotSimule.class })
class CerclesTest {

	private static final String IDEE = "{\"titre\":\"Une caisse hors ligne\",\"description\":\"Une caisse qui marche sans internet.\"}";

	/** Ce que répondra l'adaptateur de dépôt : un dépôt, rien, ou une panne. */
	static final AtomicReference<java.util.function.Function<DepotCode.DemandeDepot, Optional<DepotCode.DepotDuTour>>> DEPOT =
			new AtomicReference<>();

	static final List<DepotCode.DemandeDepot> DEMANDES_DEPOT = java.util.Collections.synchronizedList(new ArrayList<>());

	@TestConfiguration(proxyBeanMethods = false)
	static class DepotSimule {

		@Bean
		@Primary
		DepotCode depotCode() {
			return demande -> {
				DEMANDES_DEPOT.add(demande);
				var comportement = DEPOT.get();
				return comportement == null ? Optional.empty() : comportement.apply(demande);
			};
		}

	}

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient base;

	@Autowired
	GestionCercles gestion;

	@BeforeEach
	void remettreLesSimulations() {
		DEPOT.set(null);
		DEMANDES_DEPOT.clear();
	}

	// ---------------------------------------------------------------- aides

	private UtilisateurConnecte utilisateur(String nom) {
		UUID id = UUID.randomUUID();
		Timestamp t = Timestamp.from(Instant.now());
		base.sql("INSERT INTO utilisateur (id, nom, login_github, avatar_url, cree_le, mis_a_jour_le) VALUES (:id, :nom, :login, 'https://avatars.example/a', :t, :t)")
			.param("id", id).param("nom", nom).param("login", "gh-" + id.toString().substring(0, 8)).param("t", t).update();
		return new UtilisateurConnecte(id, Set.of(Role.MEMBRE));
	}

	private MvcTestResult ecrire(String methode, String uri, UtilisateurConnecte qui, String corps) {
		var requete = switch (methode) {
			case "PUT" -> mvc.put().uri(uri);
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

	private static String creation(int taille) {
		return "{\"nom\":\"Cercle de Dakar\",\"ville\":\"Dakar\",\"taille\":" + taille + "}";
	}

	private String creerCercle(UtilisateurConnecte createur, int taille) {
		MvcTestResult reponse = ecrire("POST", "/api/cercles", createur, creation(taille));
		assertThat(reponse).hasStatus(HttpStatus.CREATED);
		return reponse.getResponse().getHeader("Location").substring("/api/cercles/".length());
	}

	private void rejoindre(String cercle, UtilisateurConnecte qui) {
		assertThat(ecrire("POST", "/api/cercles/" + cercle + "/membres", qui, null)).hasStatus(HttpStatus.OK);
	}

	private void deposer(String cercle, UtilisateurConnecte qui) {
		assertThat(ecrire("PUT", "/api/cercles/" + cercle + "/membres/moi/idee", qui, IDEE)).hasStatus(HttpStatus.OK);
	}

	/** Un cercle de 4 prêt à tirer : tous membres, toutes les idées déposées. */
	private record Pret(String id, UtilisateurConnecte createur, List<UtilisateurConnecte> membres) {
	}

	private Pret cercleComplet() {
		UtilisateurConnecte createur = utilisateur("Créateur");
		String id = creerCercle(createur, 4);
		List<UtilisateurConnecte> membres = new ArrayList<>(List.of(createur));
		for (String nom : List.of("Kofi", "Fatou", "Moussa")) {
			UtilisateurConnecte m = utilisateur(nom);
			rejoindre(id, m);
			membres.add(m);
		}
		membres.forEach(m -> deposer(id, m));
		return new Pret(id, createur, membres);
	}

	private Pret cercleTire() {
		Pret pret = cercleComplet();
		assertThat(ecrire("POST", "/api/cercles/" + pret.id() + "/tirage", pret.createur(), null)).hasStatusOk();
		return pret;
	}

	/** Le membre du rang donné. */
	private UtilisateurConnecte membreDuRang(Pret pret, int rang) {
		UUID id = base.sql("SELECT utilisateur_id FROM cercle_membre WHERE cercle_id = :c AND rang = :r")
			.param("c", UUID.fromString(pret.id())).param("r", rang).query(UUID.class).single();
		return pret.membres().stream().filter(m -> m.id().equals(id)).findFirst().orElseThrow();
	}

	private static String extraire(MvcTestResult reponse, String champ) {
		try {
			String corps = reponse.getResponse().getContentAsString();
			int i = corps.indexOf("\"" + champ + "\":\"");
			return corps.substring(i + champ.length() + 4, corps.indexOf('"', i + champ.length() + 4));
		}
		catch (java.io.UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
	}

	// ---------------------------------------------------------------- création et lecture

	@Test
	void creer_un_cercle_fait_de_moi_le_createur_et_le_premier_membre() {
		UtilisateurConnecte awa = utilisateur("Awa");

		MvcTestResult reponse = ecrire("POST", "/api/cercles", awa, creation(5));

		assertThat(reponse).hasStatus(HttpStatus.CREATED);
		assertThat(reponse.getResponse().getHeader("Location")).startsWith("/api/cercles/");
		assertThat(reponse).bodyJson().extractingPath("$.statut").isEqualTo("ouvert");
		assertThat(reponse).bodyJson().extractingPath("$.taille").isEqualTo(5);
		assertThat(reponse).bodyJson().extractingPath("$.nombreMembres").isEqualTo(1);
		assertThat(reponse).bodyJson().extractingPath("$.createur.nom").isEqualTo("Awa");
		assertThat(reponse).bodyJson().extractingPath("$.moi.estCreateur").isEqualTo(true);
		assertThat(reponse).bodyJson().extractingPath("$.moi.estMembre").isEqualTo(true);
		assertThat(reponse).bodyJson().extractingPath("$.tours").isEqualTo(List.of());
		assertThat(reponse).bodyJson().extractingPath("$.tourEnCours").isNull();
		assertThat(reponse).bodyJson().extractingPath("$.tirage").isNull();
		assertThat(reponse).bodyJson().extractingPath("$.membres[0].rang").isNull();
	}

	@Test
	void champs_invalides_repondent_400_avec_le_detail() {
		UtilisateurConnecte awa = utilisateur("Awa");
		String[][] cas = {
				{ "{\"nom\":\"ab\",\"ville\":\"Dakar\",\"taille\":4}", "nom" },
				{ "{\"nom\":\"Cercle\",\"ville\":\"D\",\"taille\":4}", "ville" },
				{ "{\"nom\":\"Cercle\",\"ville\":\"Dakar\",\"taille\":3}", "taille" },
				{ "{\"nom\":\"Cercle\",\"ville\":\"Dakar\",\"taille\":7}", "taille" },
				{ "{\"nom\":\"Cercle\",\"ville\":\"Dakar\"}", "taille" },
				{ "{\"nom\":\"" + "x".repeat(81) + "\",\"ville\":\"Dakar\",\"taille\":4}", "nom" } };
		for (String[] c : cas) {
			MvcTestResult reponse = ecrire("POST", "/api/cercles", awa, c[0]);
			assertThat(reponse).as(c[1]).hasStatus(HttpStatus.BAD_REQUEST);
			assertThat(reponse).bodyJson().extractingPath("$.code").isEqualTo("validation");
			assertThat(reponse).bodyJson().extractingPath("$.erreurs[0].champ").isEqualTo(c[1]);
		}
	}

	@Test
	void sixieme_creation_du_jour_repond_429_avec_retry_after() {
		UtilisateurConnecte awa = utilisateur("Awa");
		for (int i = 0; i < GestionCercles.CERCLES_PAR_JOUR; i++) {
			creerCercle(awa, 4);
		}

		MvcTestResult refus = ecrire("POST", "/api/cercles", awa, creation(4));

		assertThat(refus).hasStatus(HttpStatus.TOO_MANY_REQUESTS).hasContentType("application/problem+json");
		assertThat(refus).bodyJson().extractingPath("$.code").isEqualTo("trop_de_requetes");
		assertThat(Long.parseLong(refus.getResponse().getHeader("Retry-After"))).isBetween(1L, 86400L);
	}

	@Test
	void sans_connexion_401_et_sans_jeton_csrf_403() {
		assertThat(mvc.get().uri("/api/cercles")).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.get().uri("/api/cercles/{id}", UUID.randomUUID())).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.post().uri("/api/cercles").contentType(MediaType.APPLICATION_JSON).content(creation(4)).with(csrf()))
			.hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.post().uri("/api/cercles").contentType(MediaType.APPLICATION_JSON).content(creation(4))
			.with(oauth2Login().oauth2User(utilisateur("Awa")))).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void cercle_inconnu_repond_404_au_format_du_contrat() {
		MvcTestResult reponse = lire("/api/cercles/" + UUID.randomUUID(), utilisateur("Awa"));

		assertThat(reponse).hasStatus(HttpStatus.NOT_FOUND).hasContentType("application/problem+json");
		assertThat(reponse).bodyJson().extractingPath("$.code").isEqualTo("introuvable");
		assertThat(ecrire("POST", "/api/cercles/" + UUID.randomUUID() + "/membres", utilisateur("Awa"), null))
			.hasStatus(HttpStatus.NOT_FOUND);
	}

	// ---------------------------------------------------------------- rejoindre et idées

	@Test
	void rejoindre_ajoute_un_membre_et_ne_se_fait_pas_deux_fois() {
		UtilisateurConnecte awa = utilisateur("Awa");
		UtilisateurConnecte kofi = utilisateur("Kofi");
		String id = creerCercle(awa, 4);

		MvcTestResult reponse = ecrire("POST", "/api/cercles/" + id + "/membres", kofi, null);

		assertThat(reponse).hasStatusOk();
		assertThat(reponse).bodyJson().extractingPath("$.nombreMembres").isEqualTo(2);
		assertThat(reponse).bodyJson().extractingPath("$.moi.estMembre").isEqualTo(true);
		assertThat(reponse).bodyJson().extractingPath("$.moi.estCreateur").isEqualTo(false);
		MvcTestResult encore = ecrire("POST", "/api/cercles/" + id + "/membres", kofi, null);
		assertThat(encore).hasStatus(HttpStatus.CONFLICT);
		assertThat(encore).bodyJson().extractingPath("$.code").isEqualTo("deja_membre");
	}

	@Test
	void la_derniere_place_prise_le_cercle_est_complet() {
		UtilisateurConnecte awa = utilisateur("Awa");
		String id = creerCercle(awa, 4);
		for (int i = 0; i < 3; i++) {
			rejoindre(id, utilisateur("Membre" + i));
		}

		MvcTestResult refus = ecrire("POST", "/api/cercles/" + id + "/membres", utilisateur("Retard"), null);

		assertThat(refus).hasStatus(HttpStatus.CONFLICT);
		assertThat(refus).bodyJson().extractingPath("$.code").isEqualTo("cercle_complet");
	}

	@Test
	void deux_personnes_qui_visent_la_derniere_place_une_seule_l_obtient() throws Exception {
		UtilisateurConnecte awa = utilisateur("Awa");
		String id = creerCercle(awa, 4);
		for (int i = 0; i < 2; i++) {
			rejoindre(id, utilisateur("Membre" + i));
		}

		long reussites = enParallele(List.of(
				() -> tenter(() -> gestion.rejoindre(UUID.fromString(id), utilisateur("A").id(), Instant.now())),
				() -> tenter(() -> gestion.rejoindre(UUID.fromString(id), utilisateur("B").id(), Instant.now()))));

		assertThat(reussites).isEqualTo(1);
		assertThat(base.sql("SELECT count(*) FROM cercle_membre WHERE cercle_id = :c").param("c", UUID.fromString(id))
			.query(Long.class).single()).isEqualTo(4);
	}

	@Test
	void deposer_une_idee_est_reserve_aux_membres_et_se_remplace() {
		UtilisateurConnecte awa = utilisateur("Awa");
		String id = creerCercle(awa, 4);

		assertThat(ecrire("PUT", "/api/cercles/" + id + "/membres/moi/idee", utilisateur("Intrus"), IDEE))
			.hasStatus(HttpStatus.FORBIDDEN);
		MvcTestResult premiere = ecrire("PUT", "/api/cercles/" + id + "/membres/moi/idee", awa, IDEE);
		assertThat(premiere).hasStatusOk();
		assertThat(premiere).bodyJson().extractingPath("$.aDeposeIdee").isEqualTo(true);
		assertThat(premiere).bodyJson().extractingPath("$.idee.titre").isEqualTo("Une caisse hors ligne");

		MvcTestResult remplacee = ecrire("PUT", "/api/cercles/" + id + "/membres/moi/idee", awa,
				"{\"titre\":\"Un autre titre\",\"description\":\"Une autre description assez longue.\"}");
		assertThat(remplacee).bodyJson().extractingPath("$.idee.titre").isEqualTo("Un autre titre");
	}

	@Test
	void idee_invalide_repond_400() {
		UtilisateurConnecte awa = utilisateur("Awa");
		String id = creerCercle(awa, 4);

		MvcTestResult reponse = ecrire("PUT", "/api/cercles/" + id + "/membres/moi/idee", awa,
				"{\"titre\":\"ab\",\"description\":\"court\"}");

		assertThat(reponse).hasStatus(HttpStatus.BAD_REQUEST);
		assertThat(reponse).bodyJson().extractingPath("$.erreurs.length()").isEqualTo(2);
	}

	@Test
	void les_idees_ne_sont_visibles_que_des_membres() {
		UtilisateurConnecte awa = utilisateur("Awa");
		String id = creerCercle(awa, 4);
		deposer(id, awa);

		MvcTestResult pourMoi = lire("/api/cercles/" + id, awa);
		MvcTestResult pourUnAutre = lire("/api/cercles/" + id, utilisateur("Curieux"));

		assertThat(pourMoi).bodyJson().extractingPath("$.membres[0].idee.titre").isEqualTo("Une caisse hors ligne");
		assertThat(pourUnAutre).bodyJson().extractingPath("$.membres[0].aDeposeIdee").isEqualTo(true);
		assertThat(pourUnAutre).bodyJson().extractingPath("$.membres[0].idee").isNull();
		assertThat(pourUnAutre).bodyJson().extractingPath("$.moi.estMembre").isEqualTo(false);
	}

	// ---------------------------------------------------------------- tirage

	@Test
	void le_tirage_est_reserve_au_createur_et_exige_un_cercle_complet_avec_toutes_les_idees() {
		UtilisateurConnecte awa = utilisateur("Awa");
		String id = creerCercle(awa, 4);
		UtilisateurConnecte kofi = utilisateur("Kofi");
		rejoindre(id, kofi);

		assertThat(ecrire("POST", "/api/cercles/" + id + "/tirage", kofi, null)).hasStatus(HttpStatus.FORBIDDEN);
		MvcTestResult incomplet = ecrire("POST", "/api/cercles/" + id + "/tirage", awa, null);
		assertThat(incomplet).hasStatus(HttpStatus.CONFLICT);
		assertThat(incomplet).bodyJson().extractingPath("$.code").isEqualTo("cercle_incomplet");

		rejoindre(id, utilisateur("Fatou"));
		rejoindre(id, utilisateur("Moussa"));
		deposer(id, awa);
		MvcTestResult sansIdees = ecrire("POST", "/api/cercles/" + id + "/tirage", awa, null);
		assertThat(sansIdees).hasStatus(HttpStatus.CONFLICT);
		assertThat(sansIdees).bodyJson().extractingPath("$.code").isEqualTo("idees_manquantes");
		assertThat(base.sql("SELECT statut FROM cercle WHERE id = :c").param("c", UUID.fromString(id)).query(String.class).single())
			.as("un tirage refusé ne change rien").isEqualTo("ouvert");
	}

	@Test
	void le_tirage_donne_un_rang_a_chacun_et_ouvre_le_tour_1_avec_son_projet() {
		Pret pret = cercleComplet();

		MvcTestResult reponse = ecrire("POST", "/api/cercles/" + pret.id() + "/tirage", pret.createur(), null);

		assertThat(reponse).hasStatusOk();
		assertThat(reponse).bodyJson().extractingPath("$.statut").isEqualTo("en_cours");
		assertThat(reponse).bodyJson().extractingPath("$.tourEnCours").isEqualTo(1);
		assertThat(reponse).bodyJson().extractingPath("$.tirage.parId").isEqualTo(pret.createur().id().toString());
		assertThat(reponse).bodyJson().extractingPath("$.tirage.effectueLe").isNotNull();
		assertThat(reponse).bodyJson().extractingPath("$.membres[*].rang").isEqualTo(List.of(1, 2, 3, 4));
		assertThat(reponse).bodyJson().extractingPath("$.tours.length()").isEqualTo(4);
		assertThat(reponse).bodyJson().extractingPath("$.tours[0].statut").isEqualTo("en_cours");
		assertThat(reponse).bodyJson().extractingPath("$.tours[1].statut").isEqualTo("a_venir");
		assertThat(reponse).bodyJson().extractingPath("$.tours[1].projetId").isNull();

		UUID projet = base.sql("SELECT projet_id FROM cercle_membre WHERE cercle_id = :c AND rang = 1")
			.param("c", UUID.fromString(pret.id())).query(UUID.class).single();
		UtilisateurConnecte premier = membreDuRang(pret, 1);
		assertThat(base.sql("SELECT porteur_id FROM projet WHERE id = :p").param("p", projet).query(UUID.class).single())
			.isEqualTo(premier.id());
		assertThat(base.sql("SELECT statut FROM projet WHERE id = :p").param("p", projet).query(String.class).single())
			.isEqualTo("en_cours");
		assertThat(base.sql("SELECT count(*) FROM membre_equipe WHERE projet_id = :p AND parti_le IS NULL").param("p", projet)
			.query(Long.class).single()).as("toute l'équipe du cercle").isEqualTo(4);
		assertThat(base.sql("SELECT titre FROM projet WHERE id = :p").param("p", projet).query(String.class).single())
			.isEqualTo("Une caisse hors ligne");
	}

	@Test
	void le_tirage_ne_se_fait_qu_une_fois_et_ferme_le_cercle() {
		Pret pret = cercleTire();
		String ordre = base.sql("SELECT string_agg(utilisateur_id::text, ',' ORDER BY rang) FROM cercle_membre WHERE cercle_id = :c")
			.param("c", UUID.fromString(pret.id())).query(String.class).single();

		MvcTestResult encore = ecrire("POST", "/api/cercles/" + pret.id() + "/tirage", pret.createur(), null);

		assertThat(encore).hasStatus(HttpStatus.CONFLICT);
		assertThat(encore).bodyJson().extractingPath("$.code").isEqualTo("tirage_deja_fait");
		assertThat(base.sql("SELECT string_agg(utilisateur_id::text, ',' ORDER BY rang) FROM cercle_membre WHERE cercle_id = :c")
			.param("c", UUID.fromString(pret.id())).query(String.class).single()).as("l'ordre ne change plus").isEqualTo(ordre);
		MvcTestResult retard = ecrire("POST", "/api/cercles/" + pret.id() + "/membres", utilisateur("Retard"), null);
		assertThat(retard).bodyJson().extractingPath("$.code").isEqualTo("tirage_deja_fait");
		MvcTestResult idee = ecrire("PUT", "/api/cercles/" + pret.id() + "/membres/moi/idee", pret.createur(), IDEE);
		assertThat(idee).hasStatus(HttpStatus.CONFLICT);
		assertThat(idee).bodyJson().extractingPath("$.code").isEqualTo("tirage_deja_fait");
	}

	@Test
	void l_ordre_depend_du_hasard_et_pas_de_l_ordre_d_arrivee() {
		Set<Integer> rangsDuCreateur = new HashSet<>();
		for (int i = 0; i < 14; i++) {
			Pret pret = cercleTire();
			rangsDuCreateur.add(base.sql("SELECT rang FROM cercle_membre WHERE cercle_id = :c AND utilisateur_id = :u")
				.param("c", UUID.fromString(pret.id())).param("u", pret.createur().id()).query(Integer.class).single());
		}

		assertThat(rangsDuCreateur).as("le créateur n'est pas toujours premier").hasSizeGreaterThan(1);
	}

	// ---------------------------------------------------------------- livraison

	@Test
	void livrer_le_tour_ouvre_le_suivant_et_passe_le_projet_a_livre() {
		Pret pret = cercleTire();
		UtilisateurConnecte premier = membreDuRang(pret, 1);

		MvcTestResult reponse = ecrire("POST", "/api/cercles/" + pret.id() + "/tours/1/livrer", premier, null);

		assertThat(reponse).hasStatusOk();
		assertThat(reponse).bodyJson().extractingPath("$.tourEnCours").isEqualTo(2);
		assertThat(reponse).bodyJson().extractingPath("$.tours[0].statut").isEqualTo("livre");
		assertThat(reponse).bodyJson().extractingPath("$.tours[0].livreLe").isNotNull();
		assertThat(reponse).bodyJson().extractingPath("$.tours[1].statut").isEqualTo("en_cours");
		assertThat(reponse).bodyJson().extractingPath("$.tours[1].projetId").isNotNull();
		assertThat(reponse).bodyJson().extractingPath("$.tours[2].statut").isEqualTo("a_venir");
		assertThat(base.sql("""
				SELECT p.statut FROM projet p JOIN cercle_membre m ON m.projet_id = p.id
				WHERE m.cercle_id = :c AND m.rang = 1
				""").param("c", UUID.fromString(pret.id())).query(String.class).single()).isEqualTo("livre");
		assertThat(base.sql("""
				SELECT p.porteur_id FROM projet p JOIN cercle_membre m ON m.projet_id = p.id
				WHERE m.cercle_id = :c AND m.rang = 2
				""").param("c", UUID.fromString(pret.id())).query(UUID.class).single()).isEqualTo(membreDuRang(pret, 2).id());
	}

	@Test
	void rejouer_la_livraison_ne_livre_jamais_deux_tours() {
		Pret pret = cercleTire();
		UtilisateurConnecte premier = membreDuRang(pret, 1);
		assertThat(ecrire("POST", "/api/cercles/" + pret.id() + "/tours/1/livrer", premier, null)).hasStatusOk();

		MvcTestResult rejeu = ecrire("POST", "/api/cercles/" + pret.id() + "/tours/1/livrer", premier, null);

		assertThat(rejeu).hasStatus(HttpStatus.CONFLICT);
		assertThat(rejeu).bodyJson().extractingPath("$.code").isEqualTo("tour_non_en_cours");
		assertThat(base.sql("SELECT tour_en_cours FROM cercle WHERE id = :c").param("c", UUID.fromString(pret.id()))
			.query(Integer.class).single()).isEqualTo(2);
	}

	@Test
	void deux_livraisons_simultanees_du_meme_tour_une_seule_passe() throws Exception {
		Pret pret = cercleTire();
		UUID cercle = UUID.fromString(pret.id());
		UUID premier = membreDuRang(pret, 1).id();

		long reussites = enParallele(List.of(
				() -> tenter(() -> gestion.livrer(cercle, 1, premier, Instant.now())),
				() -> tenter(() -> gestion.livrer(cercle, 1, pret.createur().id(), Instant.now()))));

		assertThat(reussites).isEqualTo(1);
		assertThat(base.sql("SELECT tour_en_cours FROM cercle WHERE id = :c").param("c", cercle).query(Integer.class).single())
			.isEqualTo(2);
	}

	@Test
	void seuls_le_proprietaire_du_tour_et_le_createur_livrent() {
		Pret pret = cercleTire();
		UtilisateurConnecte autre = pret.membres().stream()
			.filter(m -> !m.id().equals(pret.createur().id()) && !m.id().equals(membreDuRang(pret, 1).id())).findFirst().orElseThrow();

		assertThat(ecrire("POST", "/api/cercles/" + pret.id() + "/tours/1/livrer", autre, null)).hasStatus(HttpStatus.FORBIDDEN);
		assertThat(ecrire("POST", "/api/cercles/" + pret.id() + "/tours/1/livrer", utilisateur("Intrus"), null))
			.hasStatus(HttpStatus.FORBIDDEN);
		assertThat(ecrire("POST", "/api/cercles/" + pret.id() + "/tours/1/livrer", pret.createur(), null)).hasStatusOk();
	}

	@Test
	void un_tour_pas_encore_ouvert_ou_inexistant_est_refuse() {
		Pret pret = cercleTire();

		MvcTestResult futur = ecrire("POST", "/api/cercles/" + pret.id() + "/tours/3/livrer", pret.createur(), null);
		assertThat(futur).hasStatus(HttpStatus.CONFLICT);
		assertThat(futur).bodyJson().extractingPath("$.code").isEqualTo("tour_non_en_cours");
		assertThat(ecrire("POST", "/api/cercles/" + pret.id() + "/tours/7/livrer", pret.createur(), null))
			.hasStatus(HttpStatus.NOT_FOUND);
		assertThat(ecrire("POST", "/api/cercles/" + pret.id() + "/tours/0/livrer", pret.createur(), null))
			.hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void livrer_tous_les_tours_termine_le_cercle() {
		Pret pret = cercleTire();
		MvcTestResult derniere = null;
		for (int tour = 1; tour <= 4; tour++) {
			derniere = ecrire("POST", "/api/cercles/" + pret.id() + "/tours/" + tour + "/livrer", pret.createur(), null);
			assertThat(derniere).as("tour " + tour).hasStatusOk();
		}

		assertThat(derniere).bodyJson().extractingPath("$.statut").isEqualTo("termine");
		assertThat(derniere).bodyJson().extractingPath("$.tourEnCours").isNull();
		assertThat(derniere).bodyJson().extractingPath("$.tours[*].statut").isEqualTo(List.of("livre", "livre", "livre", "livre"));
		MvcTestResult encore = ecrire("POST", "/api/cercles/" + pret.id() + "/tours/4/livrer", pret.createur(), null);
		assertThat(encore).hasStatus(HttpStatus.CONFLICT);
		assertThat(encore).bodyJson().extractingPath("$.code").isEqualTo("cercle_termine");
	}

	@Test
	void livrer_avant_le_tirage_est_refuse() {
		UtilisateurConnecte awa = utilisateur("Awa");
		String id = creerCercle(awa, 4);

		MvcTestResult reponse = ecrire("POST", "/api/cercles/" + id + "/tours/1/livrer", awa, null);

		assertThat(reponse).hasStatus(HttpStatus.CONFLICT);
		assertThat(reponse).bodyJson().extractingPath("$.code").isEqualTo("tour_non_en_cours");
	}

	// ---------------------------------------------------------------- liste

	@Test
	void la_liste_des_cercles_ouverts_exclut_les_complets_et_les_tires() {
		UtilisateurConnecte awa = utilisateur("Awa");
		String ouvert = creerCercle(awa, 4);
		Pret tire = cercleTire();
		Pret complet = cercleComplet();

		List<String> ids = idsDe(lire("/api/cercles?limite=100", utilisateur("Curieux")));

		assertThat(ids).contains(ouvert).doesNotContain(tire.id(), complet.id());
	}

	@Test
	void mes_cercles_et_pagination_par_curseur() {
		UtilisateurConnecte awa = utilisateur("Awa");
		String un = creerCercle(awa, 4);
		String deux = creerCercle(awa, 4);
		String trois = creerCercle(awa, 4);
		creerCercle(utilisateur("Autre"), 4);

		MvcTestResult page1 = lire("/api/cercles?portee=mes&limite=2", awa);
		assertThat(page1).bodyJson().extractingPath("$.elements.length()").isEqualTo(2);
		String curseur = extraire(page1, "curseurSuivant");
		assertThat(idsDe(page1)).containsExactly(trois, deux);

		MvcTestResult page2 = lire("/api/cercles?portee=mes&limite=2&curseur=" + curseur, awa);
		assertThat(idsDe(page2)).containsExactly(un);
		assertThat(page2).bodyJson().extractingPath("$.curseurSuivant").isNull();
	}

	@Test
	void parametres_de_liste_invalides_repondent_400() {
		UtilisateurConnecte awa = utilisateur("Awa");

		for (String uri : List.of("/api/cercles?portee=tous", "/api/cercles?limite=0", "/api/cercles?limite=101",
				"/api/cercles?curseur=pasunecurseur")) {
			MvcTestResult reponse = lire(uri, awa);
			assertThat(reponse).as(uri).hasStatus(HttpStatus.BAD_REQUEST);
			assertThat(reponse).bodyJson().extractingPath("$.code").isEqualTo("validation");
		}
	}

	/** Les identifiants des cercles d'une page, dans l'ordre (pas ceux de leurs créateurs). */
	private List<String> idsDe(MvcTestResult reponse) {
		try {
			var racine = new tools.jackson.databind.json.JsonMapper().readTree(reponse.getResponse().getContentAsString());
			List<String> ids = new ArrayList<>();
			racine.get("elements").forEach(e -> ids.add(e.get("id").asString()));
			return ids;
		}
		catch (java.io.UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
	}

	// ---------------------------------------------------------------- dépôt de code

	@Test
	void un_adaptateur_de_depot_remplit_le_depot_du_tour_ouvert() {
		DEPOT.set(demande -> Optional.of(new DepotCode.DepotDuTour("gitea", "https://git.example/cercle/tour-" + demande.numero(),
				"https://git.example/cercle/tour-" + demande.numero() + ".git")));
		Pret pret = cercleComplet();

		MvcTestResult reponse = ecrire("POST", "/api/cercles/" + pret.id() + "/tirage", pret.createur(), null);
		MvcTestResult relue = lire("/api/cercles/" + pret.id(), pret.createur());

		assertThat(relue).bodyJson().extractingPath("$.tours[0].depot.fournisseur").isEqualTo("gitea");
		assertThat(relue).bodyJson().extractingPath("$.tours[0].depot.url").isEqualTo("https://git.example/cercle/tour-1");
		assertThat(relue).bodyJson().extractingPath("$.tours[1].depot").isNull();
		assertThat(reponse).hasStatusOk();
		assertThat(DEMANDES_DEPOT).hasSize(1);
		assertThat(DEMANDES_DEPOT.getFirst().numero()).isEqualTo(1);
		assertThat(DEMANDES_DEPOT.getFirst().loginsGithub()).hasSize(4);
		assertThat(DEMANDES_DEPOT.getFirst().titre()).isEqualTo("Une caisse hors ligne");

		UtilisateurConnecte premier = membreDuRang(pret, 1);
		MvcTestResult apres = ecrire("POST", "/api/cercles/" + pret.id() + "/tours/1/livrer", premier, null);
		assertThat(apres).bodyJson().extractingPath("$.tours[1].depot.url").isEqualTo("https://git.example/cercle/tour-2");
	}

	@Test
	void un_depot_en_panne_ou_invalide_ne_defait_jamais_le_tirage() {
		DEPOT.set(demande -> {
			throw new IllegalStateException("fournisseur en panne");
		});
		Pret enPanne = cercleComplet();
		MvcTestResult tirage = ecrire("POST", "/api/cercles/" + enPanne.id() + "/tirage", enPanne.createur(), null);
		assertThat(tirage).hasStatusOk();
		assertThat(tirage).bodyJson().extractingPath("$.statut").isEqualTo("en_cours");
		assertThat(tirage).bodyJson().extractingPath("$.tours[0].depot").isNull();

		DEPOT.set(demande -> Optional.of(new DepotCode.DepotDuTour("gitea", "http://pas-https.example/x", "http://pas-https.example/x.git")));
		Pret invalide = cercleComplet();
		MvcTestResult tirage2 = ecrire("POST", "/api/cercles/" + invalide.id() + "/tirage", invalide.createur(), null);
		assertThat(tirage2).hasStatusOk();
		assertThat(tirage2).bodyJson().extractingPath("$.tours[0].depot").isNull();
	}

	// ---------------------------------------------------------------- aides de concurrence

	private static boolean tenter(Runnable action) {
		try {
			action.run();
			return true;
		}
		catch (ErreurCercle refus) {
			return false;
		}
	}

	private static long enParallele(List<Callable<Boolean>> taches) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(taches.size());
		try {
			CountDownLatch depart = new CountDownLatch(1);
			List<Future<Boolean>> resultats = new ArrayList<>();
			for (Callable<Boolean> tache : taches) {
				resultats.add(pool.submit(() -> {
					depart.await();
					return tache.call();
				}));
			}
			depart.countDown();
			long reussites = 0;
			for (Future<Boolean> resultat : resultats) {
				if (resultat.get()) {
					reussites++;
				}
			}
			return reussites;
		}
		finally {
			pool.shutdownNow();
		}
	}

}
