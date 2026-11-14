package dev.jamii.api.cercles;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import dev.jamii.api.TestcontainersConfiguration;

/**
 * Les chiffres des cercles pour l'espace Systalink, sur un vrai PostgreSQL. La base est partagée avec les autres
 * tests : on mesure donc ce que nos insertions ajoutent, pas des totaux absolus.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ChiffresCerclesTest {

	@Autowired
	ChiffresCercles chiffres;

	@Autowired
	JdbcClient base;

	@Test
	void compte_les_cercles_par_statut_les_tours_livres_et_chaque_membre_une_fois() {
		Instant maintenant = Instant.now();
		Instant ilYaDixJours = maintenant.minus(Duration.ofDays(10));
		long actifsAvant = chiffres.actifs();
		long terminesAvant = chiffres.termines();
		long toursAvant = chiffres.toursLivres(null);
		long toursSemaineAvant = chiffres.toursLivres(maintenant.minus(Duration.ofDays(7)));
		int membresAvant = chiffres.membres().size();

		UUID awa = utilisateur();
		UUID kofi = utilisateur();
		UUID fatou = utilisateur();
		UUID ouvert = cercle(awa, "ouvert", null);
		UUID enCours = cercle(kofi, "en_cours", 2);
		UUID termine = cercle(fatou, "termine", null);
		membre(ouvert, awa, null);
		// Awa est dans deux cercles : elle ne compte qu'une fois.
		membre(enCours, awa, ilYaDixJours);
		membre(enCours, kofi, null);
		membre(termine, fatou, maintenant);
		membre(termine, kofi, ilYaDixJours);

		assertThat(chiffres.actifs() - actifsAvant).as("ouvert et en cours").isEqualTo(2);
		assertThat(chiffres.termines() - terminesAvant).isEqualTo(1);
		assertThat(chiffres.toursLivres(null) - toursAvant).isEqualTo(3);
		assertThat(chiffres.toursLivres(maintenant.minus(Duration.ofDays(7))) - toursSemaineAvant)
			.as("seul le tour de Fatou est livré cette semaine").isEqualTo(1);
		assertThat(chiffres.membres()).contains(awa, kofi, fatou);
		assertThat(chiffres.membres().size() - membresAvant).as("Awa et Kofi comptés une seule fois").isEqualTo(3);
	}

	private UUID utilisateur() {
		UUID id = UUID.randomUUID();
		Timestamp t = Timestamp.from(Instant.now());
		base.sql("INSERT INTO utilisateur (id, nom, login_github, avatar_url, cree_le, mis_a_jour_le) VALUES (:id, 'Dev', :login, 'https://avatars.example/a', :t, :t)")
			.param("id", id).param("login", "gh-" + id.toString().substring(0, 8)).param("t", t).update();
		return id;
	}

	/** Un cercle déjà tiré (en cours ou terminé) a une date de tirage ; seul un cercle en cours a un tour en cours. */
	private UUID cercle(UUID createur, String statut, Integer tour) {
		UUID id = UUID.randomUUID();
		Timestamp t = Timestamp.from(Instant.now());
		base.sql("""
				INSERT INTO cercle (id, nom, ville, taille, createur_id, statut, tour_en_cours, tirage_le, cree_le)
				VALUES (:id, 'Cercle', 'Dakar', 4, :createur, :statut, :tour, :tirage, :t)
				""")
			.param("id", id).param("createur", createur).param("statut", statut).param("tour", tour)
			.param("tirage", "ouvert".equals(statut) ? null : t).param("t", t).update();
		return id;
	}

	private void membre(UUID cercle, UUID utilisateur, Instant livreLe) {
		base.sql("INSERT INTO cercle_membre (cercle_id, utilisateur_id, rejoint_le, livre_le) VALUES (:c, :u, :t, :livre)")
			.param("c", cercle).param("u", utilisateur).param("t", Timestamp.from(Instant.now()))
			.param("livre", livreLe == null ? null : Timestamp.from(livreLe)).update();
	}

}
