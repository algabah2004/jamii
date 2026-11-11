package dev.jamii.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class LimiteurDemandesTest {

	private static final Duration HEURE = Duration.ofHours(1);

	private final Instant debut = Instant.parse("2026-11-11T10:00:00Z");

	@Test
	void laisse_passer_jusqu_au_maximum_puis_dit_combien_attendre() {
		LimiteurDemandes limiteur = new LimiteurDemandes();

		for (int i = 0; i < 3; i++) {
			assertThat(limiteur.essayer("ip", 3, HEURE, debut.plusSeconds(i))).isZero();
		}

		assertThat(limiteur.essayer("ip", 3, HEURE, debut.plusSeconds(600))).isEqualTo(3000);
	}

	@Test
	void chaque_cle_a_sa_limite() {
		LimiteurDemandes limiteur = new LimiteurDemandes();
		limiteur.essayer("a", 1, HEURE, debut);

		assertThat(limiteur.essayer("a", 1, HEURE, debut)).isPositive();
		assertThat(limiteur.essayer("b", 1, HEURE, debut)).isZero();
	}

	@Test
	void la_fenetre_se_renouvelle() {
		LimiteurDemandes limiteur = new LimiteurDemandes();
		limiteur.essayer("ip", 1, HEURE, debut);
		assertThat(limiteur.essayer("ip", 1, HEURE, debut.plusSeconds(10))).isPositive();

		assertThat(limiteur.essayer("ip", 1, HEURE, debut.plus(HEURE))).isZero();
	}

	@Test
	void le_nombre_de_cles_suivies_reste_borne() {
		LimiteurDemandes limiteur = new LimiteurDemandes();
		for (int i = 0; i < LimiteurDemandes.CLES_MAX; i++) {
			limiteur.essayer("ip" + i, 5, HEURE, debut);
		}

		// Une heure plus tard, toutes les fenêtres sont finies : la nouvelle clé déclenche le nettoyage.
		assertThat(limiteur.essayer("nouvelle", 5, HEURE, debut.plus(HEURE).plusSeconds(1))).isZero();
		assertThat(limiteur.taille()).isLessThan(LimiteurDemandes.CLES_MAX);
	}

}
