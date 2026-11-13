package dev.jamii.api.deploiement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;

import dev.jamii.api.TestcontainersConfiguration;

/** Les règles de la mise en ligne, sur un vrai PostgreSQL. Aucun appel réseau : DNS et vérification sont simulés. */
@SpringBootTest(properties = { "DATACLOUD_DOMAINES=datacloud.test", "jamii.deploiement.premiere-verification=PT1H" })
@Import({ TestcontainersConfiguration.class, MiseEnLigneTest.Simulations.class })
@RecordApplicationEvents
class MiseEnLigneTest {

	static final UUID AWA = UUID.randomUUID();

	static final UUID KOFI = UUID.randomUUID();

	/** Projets dont AWA est membre. KOFI les voit sans être dans l'équipe. Les autres n'existent pas. */
	static final Set<UUID> PROJETS = ConcurrentHashMap.newKeySet();

	static final AtomicReference<Verificateur.Resultat> REPONSE = new AtomicReference<>();

	@TestConfiguration(proxyBeanMethods = false)
	static class Simulations {

		@Bean
		@Primary
		AccesProjets accesProjets() {
			return (projet, qui) -> !PROJETS.contains(projet) ? AccesProjets.Acces.INTROUVABLE
					: AWA.equals(qui) ? AccesProjets.Acces.MEMBRE : AccesProjets.Acces.LECTEUR;
		}

		@Bean
		@Primary
		Verificateur verificateurSimule() {
			return adresse -> REPONSE.get();
		}

		@Bean
		AdressesPubliques.Resolveur dnsSimule() {
			return nom -> switch (nom) {
				case "interne.example.test" -> new InetAddress[] { InetAddress.getByName("10.0.0.7") };
				case "inconnu.example.test" -> throw new UnknownHostException(nom);
				default -> new InetAddress[] { InetAddress.getByName("41.82.10.5") };
			};
		}

	}

	@Autowired
	MiseEnLigne miseEnLigne;

	@Autowired
	DeploiementsEnLigne enLigne;

	@Autowired
	JdbcClient base;

	@Autowired
	ApplicationEvents evenements;

	UUID projet;

	@BeforeEach
	void unProjetNeuf() {
		base.sql("DELETE FROM deploiement").update();
		miseEnLigne.oublierLimites();
		projet = UUID.randomUUID();
		PROJETS.add(projet);
		REPONSE.set(Verificateur.Resultat.REPOND);
	}

	private Deploiement attendreStatut(UUID id, Deploiement.Statut statut) {
		await().atMost(Duration.ofSeconds(5)).until(() -> lire(id).statut() == statut);
		return lire(id);
	}

	private Deploiement lire(UUID id) {
		return miseEnLigne.lister(projet, AWA, null, null, 100).stream().filter(d -> d.id().equals(id)).findFirst()
			.orElseThrow();
	}

	@Test
	void une_adresse_declaree_est_verifiee_puis_mise_en_ligne_une_seule_fois() {
		Deploiement d = miseEnLigne.declarer(projet, AWA, "https://couture-thies.example.test");
		assertThat(d.statut()).isEqualTo(Deploiement.Statut.EN_COURS);
		assertThat(d.hebergeur()).isEqualTo(Hebergeur.AUTRE);

		Deploiement verifie = attendreStatut(d.id(), Deploiement.Statut.EN_LIGNE);
		assertThat(verifie.verifieLe()).isNotNull();
		assertThat(verifie.misEnLigneLe()).isEqualTo(verifie.verifieLe());
		assertThat(verifie.dernierEssai().resultat()).isEqualTo("repond");

		Deploiement api = miseEnLigne.declarer(projet, AWA, "https://api.couture-thies.example.test");
		attendreStatut(api.id(), Deploiement.Statut.EN_LIGNE);
		assertThat(evenements.stream(ProjetMisEnLigne.class)).as("premier passage en ligne du projet seulement")
			.singleElement()
			.extracting(ProjetMisEnLigne::deploiementId)
			.isEqualTo(d.id());
	}

	@Test
	void l_hebergeur_est_deduit_de_l_adresse_et_compte_pour_le_tableau_de_bord() {
		Deploiement d = miseEnLigne.declarer(projet, AWA, "https://boutique.datacloud.test");
		assertThat(d.hebergeur()).isEqualTo(Hebergeur.DATACLOUD);
		attendreStatut(d.id(), Deploiement.Statut.EN_LIGNE);

		UUID autreProjet = UUID.randomUUID();
		PROJETS.add(autreProjet);
		Deploiement ailleurs = miseEnLigne.declarer(autreProjet, AWA, "https://ailleurs.example.test");
		await().atMost(Duration.ofSeconds(5)).until(() -> enLigne.projetsEnLigne() == 2);

		assertThat(enLigne.projetsSurDatacloud()).isEqualTo(1);
		assertThat(enLigne.lister()).extracting(DeploiementsEnLigne.ProjetEnLigne::deploiementId)
			.containsExactlyInAnyOrder(d.id(), ailleurs.id());
	}

	@Test
	void sans_reponse_depuis_24_heures_le_projet_sort_de_la_vitrine() {
		Deploiement d = miseEnLigne.declarer(projet, AWA, "https://tombe.example.test");
		attendreStatut(d.id(), Deploiement.Statut.EN_LIGNE);
		base.sql("UPDATE deploiement SET verifie_le = verifie_le - interval '25 hours' WHERE id = :id")
			.param("id", d.id())
			.update();
		assertThat(enLigne.projetsEnLigne()).as("plus vérifié récemment").isZero();

		REPONSE.set(Verificateur.Resultat.DELAI_DEPASSE);
		miseEnLigne.verifierTout();

		assertThat(lire(d.id()).statut()).isEqualTo(Deploiement.Statut.ECHEC);
		assertThat(lire(d.id()).dernierEssai().resultat()).isEqualTo("delai_depasse");
		assertThat(enLigne.lister()).isEmpty();

		REPONSE.set(Verificateur.Resultat.REPOND);
		miseEnLigne.verifierTout();
		assertThat(lire(d.id()).statut()).as("revient seul dès que l'adresse répond").isEqualTo(Deploiement.Statut.EN_LIGNE);
	}

	@Test
	void une_panne_courte_ne_fait_pas_sortir_le_projet() {
		Deploiement d = miseEnLigne.declarer(projet, AWA, "https://hoquet.example.test");
		attendreStatut(d.id(), Deploiement.Statut.EN_LIGNE);

		REPONSE.set(Verificateur.Resultat.ERREUR_SERVEUR);
		miseEnLigne.verifierTout();

		assertThat(lire(d.id()).statut()).isEqualTo(Deploiement.Statut.EN_LIGNE);
		assertThat(lire(d.id()).dernierEssai().resultat()).isEqualTo("erreur_serveur");
	}

	@Test
	void les_adresses_dangereuses_ou_invalides_sont_refusees_avant_tout_appel() {
		assertThat(codeDeChamp("http://site.example.test")).isEqualTo("pas_https");
		assertThat(codeDeChamp("https://site.example.test:8443")).isEqualTo("port_interdit");
		assertThat(codeDeChamp("https://moi:secret@site.example.test")).isEqualTo("identifiant_interdit");
		assertThat(codeDeChamp("https://interne.example.test")).isEqualTo("adresse_refusee");
		assertThat(codeDeChamp("https://inconnu.example.test")).isEqualTo("nom_inconnu");
		assertThat(codeDeChamp("pas une adresse")).isEqualTo("format_invalide");
		assertThat(base.sql("SELECT count(*) FROM deploiement").query(Long.class).single()).isZero();
	}

	private String codeDeChamp(String adresse) {
		try {
			miseEnLigne.declarer(projet, AWA, adresse);
		}
		catch (MiseEnLigne.Refus refus) {
			assertThat(refus.statut).isEqualTo(HttpStatus.BAD_REQUEST);
			return refus.champ;
		}
		throw new AssertionError("adresse acceptée : " + adresse);
	}

	@Test
	void seule_l_equipe_met_en_ligne() {
		assertThatThrownBy(() -> miseEnLigne.declarer(projet, KOFI, "https://site.example.test"))
			.isInstanceOfSatisfying(MiseEnLigne.Refus.class, r -> assertThat(r.statut).isEqualTo(HttpStatus.FORBIDDEN));
		assertThatThrownBy(() -> miseEnLigne.declarer(UUID.randomUUID(), AWA, "https://site.example.test"))
			.isInstanceOfSatisfying(MiseEnLigne.Refus.class, r -> assertThat(r.statut).isEqualTo(HttpStatus.NOT_FOUND));
		assertThat(miseEnLigne.lister(projet, KOFI, null, null, 20)).as("mais tout connecté qui voit le projet lit")
			.isEmpty();
	}

	@Test
	void une_adresse_n_est_declaree_qu_une_fois_par_projet() {
		miseEnLigne.declarer(projet, AWA, "https://double.example.test");

		assertThatThrownBy(() -> miseEnLigne.declarer(projet, AWA, "https://double.example.test"))
			.isInstanceOfSatisfying(MiseEnLigne.Refus.class, r -> assertThat(r.code).isEqualTo("deja_declaree"));
	}

	@Test
	void relancer_la_verification_est_limite_a_une_fois_par_minute() {
		Deploiement d = miseEnLigne.declarer(projet, AWA, "https://relance.example.test");
		attendreStatut(d.id(), Deploiement.Statut.EN_LIGNE);

		assertThatThrownBy(() -> miseEnLigne.relancer(projet, AWA, d.id()))
			.isInstanceOfSatisfying(MiseEnLigne.Refus.class,
					r -> assertThat(r.statut).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
	}

	@Test
	void au_dela_de_10_declarations_par_heure_c_est_refuse() {
		for (int i = 0; i < MiseEnLigne.DECLARATIONS_PAR_HEURE; i++) {
			miseEnLigne.declarer(projet, AWA, "https://site" + i + ".example.test");
		}

		assertThatThrownBy(() -> miseEnLigne.declarer(projet, AWA, "https://encore.example.test"))
			.isInstanceOfSatisfying(MiseEnLigne.Refus.class,
					r -> assertThat(r.statut).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
	}

	@Test
	void retirer_une_adresse() {
		Deploiement d = miseEnLigne.declarer(projet, AWA, "https://retiree.example.test");

		miseEnLigne.retirer(projet, AWA, d.id());

		assertThat(miseEnLigne.lister(projet, AWA, null, null, 20)).isEmpty();
		assertThatThrownBy(() -> miseEnLigne.retirer(projet, AWA, d.id()))
			.isInstanceOfSatisfying(MiseEnLigne.Refus.class, r -> assertThat(r.statut).isEqualTo(HttpStatus.NOT_FOUND));
	}

	@Test
	void ouvrir_la_mise_en_ligne_propose_datacloud() {
		var proposition = miseEnLigne.proposerDatacloud(projet, AWA);

		assertThat(proposition.service().code()).isEqualTo("datacloud");
		assertThat(base.sql("SELECT count(*) FROM moment_systalink WHERE contexte_id = :projet")
			.param("projet", projet)
			.query(Long.class)
			.single()).isEqualTo(1);
	}

}
