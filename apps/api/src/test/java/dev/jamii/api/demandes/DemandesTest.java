package dev.jamii.api.demandes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Les routes de contracts/demandes.yaml et le lancement d'un projet depuis une demande, sur un vrai PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@Import({ TestcontainersConfiguration.class, DemandesTest.IaDeTest.class })
class DemandesTest {

	private static final String BESOIN = "{\"titre\":\"Boutique de couture en ligne\",\"description\":\"Je vends mes créations et je veux que les clients commandent sans passer par WhatsApp.\",\"dureeSemaines\":8}";

	/** Un service IA que le test pilote : dépôts reçus, état des tâches, et panne. */
	static class ServiceIa implements PlansIa {

		final List<UUID> cles = new ArrayList<>();

		final List<Entree> entrees = new ArrayList<>();

		final Map<UUID, Etat> etats = new ConcurrentHashMap<>();

		final Map<UUID, UUID> parCle = new ConcurrentHashMap<>();

		volatile boolean enPanne;

		volatile Etat etatParDefaut = new Etat("en_attente", null, null, null);

		@Override
		public UUID deposer(UUID cle, Entree entree) {
			if (enPanne) {
				throw new IllegalStateException("service IA injoignable");
			}
			cles.add(cle);
			entrees.add(entree);
			return parCle.computeIfAbsent(cle, k -> UUID.randomUUID());
		}

		@Override
		public Etat lire(UUID tacheId) {
			if (enPanne) {
				throw new IllegalStateException("service IA injoignable");
			}
			return etats.getOrDefault(tacheId, etatParDefaut);
		}

		void reinitialiser() {
			cles.clear();
			entrees.clear();
			etats.clear();
			parCle.clear();
			enPanne = false;
			etatParDefaut = new Etat("en_attente", null, null, null);
		}

	}

	static final ServiceIa IA = new ServiceIa();

	@TestConfiguration(proxyBeanMethods = false)
	static class IaDeTest {

		@Bean
		@Primary
		PlansIa plansIaDeTest() {
			return IA;
		}

	}

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcClient base;

	@Autowired
	JsonMapper json;

	@BeforeEach
	void viderLIa() {
		IA.reinitialiser();
	}

	// ---------------------------------------------------------------- aides

	private UtilisateurConnecte utilisateur(String nom) {
		UUID id = UUID.randomUUID();
		Timestamp t = Timestamp.from(Instant.now());
		base.sql("INSERT INTO utilisateur (id, nom, email, cree_le, mis_a_jour_le) VALUES (:id, :nom, :email, :t, :t)")
			.param("id", id).param("nom", nom).param("email", nom + "." + id + "@secret.example").param("t", t).update();
		return new UtilisateurConnecte(id, Set.of(Role.MEMBRE));
	}

	private MvcTestResult creer(UtilisateurConnecte qui, String corps) {
		return mvc.post().uri("/api/demandes").contentType(MediaType.APPLICATION_JSON).content(corps).with(csrf())
			.with(oauth2Login().oauth2User(qui)).exchange();
	}

	private MvcTestResult lire(UtilisateurConnecte qui, Object id) {
		return mvc.get().uri("/api/demandes/{id}", id).with(oauth2Login().oauth2User(qui)).exchange();
	}

	private String idCree(MvcTestResult reponse) {
		assertThat(reponse).hasStatus(HttpStatus.CREATED);
		return reponse.getResponse().getHeader("Location").substring("/api/demandes/".length());
	}

	private JsonNode corps(MvcTestResult reponse) {
		try {
			return json.readTree(reponse.getResponse().getContentAsString());
		}
		catch (java.io.UnsupportedEncodingException e) {
			throw new IllegalStateException(e);
		}
	}

	private MvcTestResult lancerProjet(UtilisateurConnecte qui, String demandeId) {
		String corps = "{\"titre\":\"Boutique de couture\",\"description\":\"Une boutique en ligne pour l'atelier.\",\"origine\":\"demande\",\"demandeId\":\""
				+ demandeId + "\"}";
		return mvc.post().uri("/api/projets").contentType(MediaType.APPLICATION_JSON).content(corps).with(csrf())
			.with(oauth2Login().oauth2User(qui)).exchange();
	}

	// ---------------------------------------------------------------- création

	@Test
	void creer_une_demande_repond_201_et_depose_la_tache_sans_donnee_personnelle() {
		UtilisateurConnecte pme = utilisateur("Awa");

		MvcTestResult reponse = creer(pme, BESOIN);

		String id = idCree(reponse);
		assertThat(reponse).bodyJson().extractingPath("$.titre").isEqualTo("Boutique de couture en ligne");
		assertThat(reponse).bodyJson().extractingPath("$.dureeSemaines").isEqualTo(8);
		assertThat(reponse).bodyJson().extractingPath("$.plan.statut").isEqualTo("en_attente");
		assertThat(reponse).bodyJson().doesNotHavePath("$.plan.resultat");
		assertThat(reponse.getResponse().getHeader("Cache-Control")).contains("no-store");
		assertThat(IA.cles).as("la clé d'idempotence est l'identifiant de la demande").containsExactly(UUID.fromString(id));
		assertThat(IA.entrees.getFirst().titre()).isEqualTo("Boutique de couture en ligne");
		assertThat(IA.entrees.getFirst().dureeSemaines()).isEqualTo(8);
		assertThat(IA.entrees.getFirst().toString()).as("ni nom ni e-mail envoyés à l'IA")
			.doesNotContain("Awa").doesNotContain("secret.example");
	}

	@Test
	void champs_invalides_repondent_400_avec_le_detail() {
		UtilisateurConnecte pme = utilisateur("Awa");
		String longue = "x".repeat(20);
		String[][] cas = {
				{ "{\"titre\":\"ab\",\"description\":\"" + longue + "\"}", "titre" },
				{ "{\"titre\":\"" + "t".repeat(121) + "\",\"description\":\"" + longue + "\"}", "titre" },
				{ "{\"titre\":\"Un titre\",\"description\":\"trop court\"}", "description" },
				{ "{\"titre\":\"Un titre\",\"description\":\"" + "d".repeat(4001) + "\"}", "description" },
				{ "{\"titre\":\"Un titre\",\"description\":\"" + longue + "\",\"dureeSemaines\":0}", "dureeSemaines" },
				{ "{\"titre\":\"Un titre\",\"description\":\"" + longue + "\",\"dureeSemaines\":27}", "dureeSemaines" } };
		for (String[] c : cas) {
			MvcTestResult reponse = creer(pme, c[0]);
			assertThat(reponse).as(c[1]).hasStatus(HttpStatus.BAD_REQUEST);
			assertThat(reponse).bodyJson().extractingPath("$.code").isEqualTo("validation");
			assertThat(reponse).bodyJson().extractingPath("$.erreurs[0].champ").isEqualTo(c[1]);
		}
		assertThat(IA.cles).as("une demande invalide n'est jamais envoyée à l'IA").isEmpty();
	}

	@Test
	void la_duree_est_facultative() {
		MvcTestResult reponse = creer(utilisateur("Awa"),
				"{\"titre\":\"Un titre\",\"description\":\"Une description d'au moins vingt caractères.\"}");

		idCree(reponse);
		assertThat(reponse).bodyJson().extractingPath("$.dureeSemaines").isNull();
		assertThat(IA.entrees.getFirst().dureeSemaines()).isNull();
	}

	@Test
	void sans_connexion_401_et_sans_jeton_csrf_403() {
		assertThat(mvc.get().uri("/api/demandes/{id}", UUID.randomUUID())).hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.post().uri("/api/demandes").contentType(MediaType.APPLICATION_JSON).content(BESOIN).with(csrf()))
			.hasStatus(HttpStatus.UNAUTHORIZED);
		assertThat(mvc.post().uri("/api/demandes").contentType(MediaType.APPLICATION_JSON).content(BESOIN)
			.with(oauth2Login().oauth2User(utilisateur("Awa")))).hasStatus(HttpStatus.FORBIDDEN);
	}

	@Test
	void onzieme_demande_de_l_heure_repond_429_et_n_est_pas_enregistree() {
		UtilisateurConnecte pme = utilisateur("Awa");
		for (int i = 0; i < GestionDemandes.PLANS_PAR_HEURE; i++) {
			idCree(creer(pme, BESOIN));
		}

		MvcTestResult refus = creer(pme, BESOIN);

		assertThat(refus).hasStatus(HttpStatus.TOO_MANY_REQUESTS).hasContentType("application/problem+json");
		assertThat(refus).bodyJson().extractingPath("$.code").isEqualTo("trop_de_requetes");
		assertThat(Long.parseLong(refus.getResponse().getHeader("Retry-After"))).isBetween(1L, 3600L);
		assertThat(base.sql("SELECT count(*) FROM demande WHERE auteur_id = :a").param("a", pme.id()).query(Long.class).single())
			.isEqualTo(GestionDemandes.PLANS_PAR_HEURE);
		assertThat(IA.cles).hasSize(GestionDemandes.PLANS_PAR_HEURE);
	}

	// ---------------------------------------------------------------- lecture

	@Test
	void seul_l_auteur_voit_sa_demande_les_autres_recoivent_404() {
		UtilisateurConnecte pme = utilisateur("Awa");
		String id = idCree(creer(pme, BESOIN));

		assertThat(lire(pme, id)).hasStatusOk();
		MvcTestResult autre = lire(utilisateur("Curieux"), id);
		assertThat(autre).hasStatus(HttpStatus.NOT_FOUND).hasContentType("application/problem+json");
		assertThat(autre).bodyJson().extractingPath("$.code").isEqualTo("introuvable");
		assertThat(lire(pme, UUID.randomUUID())).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void le_plan_suit_l_etat_de_la_tache_jusqu_au_resultat() throws Exception {
		UtilisateurConnecte pme = utilisateur("Awa");
		String id = idCree(creer(pme, BESOIN));
		UUID tache = IA.parCle.get(UUID.fromString(id));

		IA.etats.put(tache, new PlansIa.Etat("en_cours", null, null, null));
		assertThat(lire(pme, id)).bodyJson().extractingPath("$.plan.statut").isEqualTo("en_cours");

		JsonNode orientation = json.readTree("""
				{"resume":"Une boutique","secteur":"commerce","services":[],"developpeursNecessaires":false,"competences":[],
				 "plan":{"cahierDesCharges":{"resume":"Un résumé"},"jalons":[],"taches":[]}}""");
		IA.etats.put(tache, new PlansIa.Etat("terminee", "repli", orientation, null));
		MvcTestResult fini = lire(pme, id);
		assertThat(fini).bodyJson().extractingPath("$.plan.statut").isEqualTo("terminee");
		assertThat(fini).bodyJson().extractingPath("$.plan.origine").isEqualTo("repli");
		assertThat(fini).bodyJson().extractingPath("$.plan.resultat.cahierDesCharges.resume").isEqualTo("Un résumé");
		assertThat(fini).bodyJson().doesNotHavePath("$.plan.erreurCode");
		assertThat(IA.cles).as("une lecture ne redépose jamais la tâche").hasSize(1);
	}

	@Test
	void un_echec_de_l_ia_est_transmis_avec_son_code() {
		UtilisateurConnecte pme = utilisateur("Awa");
		String id = idCree(creer(pme, BESOIN));
		IA.etats.put(IA.parCle.get(UUID.fromString(id)), new PlansIa.Etat("echouee", null, null, "entree_refusee"));

		MvcTestResult reponse = lire(pme, id);

		assertThat(reponse).bodyJson().extractingPath("$.plan.statut").isEqualTo("echouee");
		assertThat(reponse).bodyJson().extractingPath("$.plan.erreurCode").isEqualTo("entree_refusee");
		assertThat(reponse).bodyJson().doesNotHavePath("$.plan.resultat");
	}

	// ---------------------------------------------------------------- service IA en panne

	@Test
	void ia_en_panne_a_la_creation_la_demande_est_gardee_et_le_depot_reessaye_a_la_lecture() {
		UtilisateurConnecte pme = utilisateur("Awa");
		IA.enPanne = true;

		MvcTestResult reponse = creer(pme, BESOIN);

		String id = idCree(reponse);
		assertThat(reponse).bodyJson().extractingPath("$.plan.statut").isEqualTo("en_attente");
		assertThat(base.sql("SELECT tache_ia_id FROM demande WHERE id = :i").param("i", UUID.fromString(id)).query(UUID.class)
			.optional()).as("pas encore de tâche").isEmpty();

		IA.enPanne = false;
		MvcTestResult relue = lire(pme, id);

		assertThat(relue).hasStatusOk();
		assertThat(IA.cles).as("même clé d'idempotence qu'au premier essai").containsExactly(UUID.fromString(id));
		assertThat(base.sql("SELECT tache_ia_id FROM demande WHERE id = :i").param("i", UUID.fromString(id)).query(UUID.class)
			.single()).isEqualTo(IA.parCle.get(UUID.fromString(id)));
	}

	@Test
	void ia_toujours_en_panne_apres_deux_minutes_le_plan_echoue_mais_le_texte_reste() {
		UtilisateurConnecte pme = utilisateur("Awa");
		IA.enPanne = true;
		String id = idCree(creer(pme, BESOIN));
		base.sql("UPDATE demande SET cree_le = :t WHERE id = :i")
			.param("t", Timestamp.from(Instant.now().minus(3, ChronoUnit.MINUTES))).param("i", UUID.fromString(id)).update();

		MvcTestResult reponse = lire(pme, id);

		assertThat(reponse).hasStatusOk();
		assertThat(reponse).bodyJson().extractingPath("$.plan.statut").isEqualTo("echouee");
		assertThat(reponse).bodyJson().extractingPath("$.plan.erreurCode").isEqualTo("delai_depasse");
		assertThat(reponse).bodyJson().extractingPath("$.description").isEqualTo(
				"Je vends mes créations et je veux que les clients commandent sans passer par WhatsApp.");
	}

	@Test
	void une_demande_ancienne_dont_l_ia_repond_montre_le_vrai_etat() {
		UtilisateurConnecte pme = utilisateur("Awa");
		String id = idCree(creer(pme, BESOIN));
		base.sql("UPDATE demande SET cree_le = :t WHERE id = :i")
			.param("t", Timestamp.from(Instant.now().minus(3, ChronoUnit.MINUTES))).param("i", UUID.fromString(id)).update();
		IA.etats.put(IA.parCle.get(UUID.fromString(id)), new PlansIa.Etat("en_cours", null, null, null));

		assertThat(lire(pme, id)).bodyJson().extractingPath("$.plan.statut").isEqualTo("en_cours");
	}

	@Test
	void ia_qui_tombe_apres_le_depot_garde_le_plan_en_attente_puis_le_retrouve() {
		UtilisateurConnecte pme = utilisateur("Awa");
		String id = idCree(creer(pme, BESOIN));
		IA.enPanne = true;

		assertThat(lire(pme, id)).bodyJson().extractingPath("$.plan.statut").isEqualTo("en_attente");

		IA.enPanne = false;
		IA.etats.put(IA.parCle.get(UUID.fromString(id)), new PlansIa.Etat("terminee", "ia", json.createObjectNode(), null));
		assertThat(lire(pme, id)).bodyJson().extractingPath("$.plan.statut").isEqualTo("terminee");
	}

	// ---------------------------------------------------------------- lancer un projet depuis la demande

	@Test
	void l_auteur_lance_un_projet_depuis_sa_demande_une_seule_fois() {
		UtilisateurConnecte pme = utilisateur("Awa");
		String demande = idCree(creer(pme, BESOIN));

		MvcTestResult projet = lancerProjet(pme, demande);

		assertThat(projet).hasStatus(HttpStatus.CREATED);
		assertThat(projet).bodyJson().extractingPath("$.origine").isEqualTo("demande");
		assertThat(projet).bodyJson().extractingPath("$.demandeId").isEqualTo(demande);
		assertThat(projet).bodyJson().extractingPath("$.statut").isEqualTo("recrutement");
		assertThat(projet).bodyJson().extractingPath("$.moi.estPorteur").isEqualTo(true);

		MvcTestResult encore = lancerProjet(pme, demande);
		assertThat(encore).hasStatus(HttpStatus.CONFLICT);
		assertThat(encore).bodyJson().extractingPath("$.code").isEqualTo("demande_deja_prise");
	}

	@Test
	void on_ne_lance_pas_un_projet_depuis_la_demande_d_un_autre_ni_d_une_demande_inconnue() {
		String demande = idCree(creer(utilisateur("Awa"), BESOIN));
		UtilisateurConnecte autre = utilisateur("Kofi");

		assertThat(lancerProjet(autre, demande)).hasStatus(HttpStatus.NOT_FOUND);
		assertThat(lancerProjet(autre, UUID.randomUUID().toString())).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void supprimer_le_compte_supprime_ses_demandes() {
		UtilisateurConnecte pme = utilisateur("Awa");
		String id = idCree(creer(pme, BESOIN));

		base.sql("DELETE FROM utilisateur WHERE id = :u").param("u", pme.id()).update();

		assertThat(base.sql("SELECT count(*) FROM demande WHERE id = :i").param("i", UUID.fromString(id)).query(Long.class)
			.single()).isZero();
	}

	// ---------------------------------------------------------------- orientation (v0.2)

	private UtilisateurConnecte developpeur(String nom, String ville, String pays, String... competences) {
		UtilisateurConnecte dev = utilisateur(nom);
		Timestamp t = Timestamp.from(Instant.now());
		base.sql("""
				INSERT INTO profil (utilisateur_id, pays, ville, disponibilite, heures_par_semaine, publie, generation_statut, mis_a_jour_le)
				VALUES (:u, :pays, :ville, 'disponible', 15, TRUE, 'terminee', :t)
				""").param("u", dev.id()).param("pays", pays).param("ville", ville).param("t", t).update();
		for (String competence : competences) {
			base.sql("INSERT INTO competence_profil (utilisateur_id, competence, source) VALUES (:u, :c, 'declaree')")
				.param("u", dev.id()).param("c", competence).update();
		}
		return dev;
	}

	@Test
	void l_orientation_recommande_les_developpeurs_qui_couvrent_le_besoin_avec_leurs_raisons() throws Exception {
		UtilisateurConnecte cabinet = utilisateur("Cabinet");
		UtilisateurConnecte fatou = developpeur("Fatou", "Dakar", "SN", "react", "spring-boot");
		UtilisateurConnecte kofi = developpeur("Kofi", "Accra", "GH", "react");
		UtilisateurConnecte sansRapport = developpeur("Yann", "Dakar", "SN", "unity");
		String corpsDemande = "{\"titre\":\"Suivi des dossiers\",\"description\":\"Notre cabinet perd des dossiers papier, il nous faut une application.\",\"ville\":\"Dakar\",\"pays\":\"SN\"}";
		String id = idCree(creer(cabinet, corpsDemande));
		JsonNode orientation = json.readTree("""
				{"resume":"Suivre les dossiers","secteur":"juridique",
				 "services":[{"id":"datacloud-stockage","nom":"Stockage","pourCeBesoin":"Les dossiers","lien":"https://systalink.com"}],
				 "developpeursNecessaires":true,"competences":["react","spring"],"plan":null}""");
		IA.etats.put(IA.parCle.get(UUID.fromString(id)), new PlansIa.Etat("terminee", "ia", orientation, null));

		JsonNode demande = corps(lire(cabinet, id));

		assertThat(demande.at("/orientation/resultat/services/0/id").asString()).isEqualTo("datacloud-stockage");
		List<String> recommandes = new ArrayList<>();
		demande.get("recommandations").forEach(r -> recommandes.add(r.get("utilisateurId").asString()));
		assertThat(recommandes).as("Fatou couvre tout et est à Dakar : en premier")
			.startsWith(fatou.id().toString()).contains(kofi.id().toString())
			.doesNotContain(sansRapport.id().toString(), cabinet.id().toString());
		JsonNode premiere = demande.get("recommandations").get(0);
		assertThat(premiere.get("score").asInt()).isEqualTo(100);
		assertThat(premiere.get("raisons").toString()).contains("Compétences : react, spring", "Dakar");
		assertThat(premiere.toString()).doesNotContain("@secret.example");
		assertThat(base.sql("SELECT orientation->>'secteur' FROM demande WHERE id = :i").param("i", UUID.fromString(id))
			.query(String.class).single()).as("le résumé est recopié pour les tendances").isEqualTo("juridique");
	}

	@Test
	void sans_developpeurs_necessaires_il_n_y_a_ni_plan_ni_recommandation() throws Exception {
		UtilisateurConnecte boutique = utilisateur("Boutique");
		developpeur("Nia", "Dakar", "SN", "react");
		String id = idCree(creer(boutique, BESOIN));
		JsonNode orientation = json.readTree("""
				{"resume":"Un site vitrine","secteur":"commerce","services":[],"developpeursNecessaires":false,
				 "competences":[],"plan":null}""");
		IA.etats.put(IA.parCle.get(UUID.fromString(id)), new PlansIa.Etat("terminee", "ia", orientation, null));

		MvcTestResult reponse = lire(boutique, id);

		assertThat(reponse).bodyJson().extractingPath("$.recommandations").asArray().isEmpty();
		assertThat(reponse).bodyJson().doesNotHavePath("$.plan.resultat");
	}

	@Test
	void l_accord_pour_les_offres_systalink_n_est_jamais_donne_par_defaut_et_se_retire() {
		UtilisateurConnecte pme = utilisateur("Awa");
		String id = idCree(creer(pme, BESOIN));
		assertThat(lire(pme, id)).bodyJson().extractingPath("$.offresSystalink").isEqualTo(false);

		MvcTestResult accord = mvc.put().uri("/api/demandes/{id}/offres-systalink", id).contentType(MediaType.APPLICATION_JSON)
			.content("{\"accepte\":true}").with(csrf()).with(oauth2Login().oauth2User(pme)).exchange();
		assertThat(accord).hasStatusOk();
		assertThat(accord).bodyJson().extractingPath("$.offresSystalink").isEqualTo(true);

		MvcTestResult retrait = mvc.put().uri("/api/demandes/{id}/offres-systalink", id).contentType(MediaType.APPLICATION_JSON)
			.content("{\"accepte\":false}").with(csrf()).with(oauth2Login().oauth2User(pme)).exchange();
		assertThat(retrait).bodyJson().extractingPath("$.offresSystalink").isEqualTo(false);
		assertThat(base.sql("SELECT offres_accord_le FROM demande WHERE id = :i").param("i", UUID.fromString(id))
			.query(Timestamp.class).optional()).isEmpty();

		MvcTestResult autre = mvc.put().uri("/api/demandes/{id}/offres-systalink", id).contentType(MediaType.APPLICATION_JSON)
			.content("{\"accepte\":true}").with(csrf()).with(oauth2Login().oauth2User(utilisateur("Kofi"))).exchange();
		assertThat(autre).hasStatus(HttpStatus.NOT_FOUND);
	}

	@Test
	void ni_e_mail_ni_telephone_ne_partent_vers_l_ia() {
		UtilisateurConnecte pme = utilisateur("Awa");
		creer(pme, "{\"titre\":\"Mon atelier\",\"description\":\"Écrivez-moi à awa.couture@gmail.com ou au +221 77 123 45 67 pour le site.\"}");

		String envoye = IA.entrees.getFirst().description();

		assertThat(envoye).doesNotContain("awa.couture@gmail.com", "77 123 45 67").contains("[e-mail retiré]", "[téléphone retiré]");
	}

}
