package dev.jamii.api.projets;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import dev.jamii.api.TestcontainersConfiguration;
import dev.jamii.api.deploiement.AccesProjets;

/** Le service de la vitrine (demande #67) et le port du module deploiement (demande #62), sur un vrai PostgreSQL. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class FichesPubliquesTest {

	@Autowired
	FichesPubliques fiches;

	@Autowired
	AccesProjets acces;

	@Autowired
	JdbcClient base;

	private UUID utilisateur(String nom) {
		UUID id = UUID.randomUUID();
		Timestamp t = Timestamp.from(Instant.now());
		base.sql("INSERT INTO utilisateur (id, nom, email, avatar_url, cree_le, mis_a_jour_le) VALUES (:id, :nom, :email, 'https://avatars.example/a', :t, :t)")
			.param("id", id).param("nom", nom).param("email", nom + "." + id + "@secret.example").param("t", t).update();
		return id;
	}

	private UUID projet(UUID porteur, String titre, String description, String origine) {
		UUID id = UUID.randomUUID();
		Timestamp t = Timestamp.from(Instant.now());
		base.sql("""
				INSERT INTO projet (id, titre, description, origine, porteur_id, derniere_activite_le, cree_le, mis_a_jour_le)
				VALUES (:id, :titre, :description, :origine, :porteur, :t, :t, :t)
				""")
			.param("id", id).param("titre", titre).param("description", description).param("origine", origine)
			.param("porteur", porteur).param("t", t).update();
		membre(id, porteur, Instant.now().minus(10, ChronoUnit.DAYS), null);
		return id;
	}

	private void membre(UUID projet, UUID utilisateur, Instant rejointLe, Instant partiLe) {
		base.sql("INSERT INTO membre_equipe (projet_id, utilisateur_id, rejoint_le, parti_le) VALUES (:p, :u, :r, :parti)")
			.param("p", projet).param("u", utilisateur).param("r", Timestamp.from(rejointLe))
			.param("parti", partiLe == null ? null : Timestamp.from(partiLe)).update();
	}

	@Test
	void renvoie_titre_accroche_origine_et_equipe_avec_le_porteur_en_premier() {
		UUID awa = utilisateur("Awa");
		UUID kofi = utilisateur("Kofi");
		UUID fatou = utilisateur("Fatou");
		UUID projet = projet(awa, "Caisse hors ligne", "Une caisse qui marche sans internet.", "idee");
		// Fatou est arrivée avant Kofi, mais après le porteur.
		membre(projet, kofi, Instant.now().minus(1, ChronoUnit.DAYS), null);
		membre(projet, fatou, Instant.now().minus(5, ChronoUnit.DAYS), null);

		FichePublique fiche = fiches.lire(List.of(projet)).get(projet);

		assertThat(fiche.titre()).isEqualTo("Caisse hors ligne");
		assertThat(fiche.accroche()).isEqualTo("Une caisse qui marche sans internet.");
		assertThat(fiche.origine()).isEqualTo("idee");
		assertThat(fiche.equipe()).extracting(FichePublique.Personne::nom).containsExactly("Awa", "Fatou", "Kofi");
		assertThat(fiche.equipe().getFirst().avatarUrl()).isEqualTo("https://avatars.example/a");
	}

	@Test
	void ne_montre_jamais_d_email_ni_la_description_complete() {
		UUID projet = projet(utilisateur("Awa"), "Titre", "Début. " + "Suite très longue. ".repeat(40), "idee");

		FichePublique fiche = fiches.lire(List.of(projet)).get(projet);

		assertThat(fiche.toString()).doesNotContain("secret.example").doesNotContain("@");
		assertThat(fiche.accroche()).hasSizeLessThanOrEqualTo(FichesPubliques.ACCROCHE_MAX);
	}

	@Test
	void un_membre_parti_n_est_pas_dans_l_equipe_et_un_projet_inconnu_est_absent() {
		UUID awa = utilisateur("Awa");
		UUID parti = utilisateur("Parti");
		UUID projet = projet(awa, "Titre", "Une description assez longue.", "demande");
		membre(projet, parti, Instant.now().minus(3, ChronoUnit.DAYS), Instant.now());
		UUID inconnu = UUID.randomUUID();

		var resultat = fiches.lire(List.of(inconnu, projet));

		assertThat(resultat).containsOnlyKeys(projet);
		assertThat(resultat.get(projet).origine()).isEqualTo("demande");
		assertThat(resultat.get(projet).equipe()).extracting(FichePublique.Personne::nom).containsExactly("Awa");
	}

	@Test
	void plusieurs_projets_en_une_fois_dans_l_ordre_demande() {
		UUID awa = utilisateur("Awa");
		UUID premier = projet(awa, "Premier", "Une description assez longue.", "idee");
		UUID second = projet(utilisateur("Kofi"), "Second", "Une autre description assez longue.", "idee");

		var resultat = fiches.lire(List.of(second, premier));

		assertThat(resultat.keySet()).containsExactly(second, premier);
		assertThat(fiches.lire(List.of())).isEmpty();
	}

	@Test
	void accroche_courte_gardee_telle_quelle_et_espaces_ramenes_a_un() {
		assertThat(FichesPubliques.accroche("  Une  ligne\n\n sur   deux  ")).isEqualTo("Une ligne sur deux");
		assertThat(FichesPubliques.accroche(null)).isEmpty();
		assertThat(FichesPubliques.accroche("x".repeat(280))).hasSize(280).doesNotContain("…");
	}

	@Test
	void accroche_longue_coupee_entre_deux_mots_avec_des_points_de_suspension() {
		String longue = "mot ".repeat(100);

		String accroche = FichesPubliques.accroche(longue);

		assertThat(accroche).hasSizeLessThanOrEqualTo(280).endsWith("mot…");
		assertThat(FichesPubliques.accroche("a".repeat(500))).hasSize(280).endsWith("…");
		assertThat(FichesPubliques.accroche("Phrase, " + "mot ".repeat(100))).startsWith("Phrase, mot");
	}

	@Test
	void le_module_deploiement_obtient_les_trois_niveaux_d_acces() {
		UUID porteur = utilisateur("Awa");
		UUID parti = utilisateur("Parti");
		UUID projet = projet(porteur, "Titre", "Une description assez longue.", "idee");
		membre(projet, parti, Instant.now().minus(2, ChronoUnit.DAYS), Instant.now());

		assertThat(acces.acces(projet, porteur)).isEqualTo(AccesProjets.Acces.MEMBRE);
		assertThat(acces.acces(projet, parti)).as("un membre parti perd le droit de mettre en ligne")
			.isEqualTo(AccesProjets.Acces.LECTEUR);
		assertThat(acces.acces(projet, utilisateur("Kofi"))).isEqualTo(AccesProjets.Acces.LECTEUR);
		assertThat(acces.acces(UUID.randomUUID(), porteur)).isEqualTo(AccesProjets.Acces.INTROUVABLE);
	}

}
