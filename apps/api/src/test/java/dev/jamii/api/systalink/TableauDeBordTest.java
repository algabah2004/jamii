package dev.jamii.api.systalink;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Règles de calcul pures : pas de base, pas de Spring. */
class TableauDeBordTest {

	@Test
	void un_pays_de_moins_de_5_inscrits_passe_dans_autres() {
		var inscrits = TableauDeBord.Inscrits.anonymiser(40, 12, Map.of("SN", 22L, "CI", 5L, "KE", 4L, "GH", 1L));

		assertThat(inscrits.parPays()).extracting(TableauDeBord.Pays::pays).containsExactly("SN", "CI");
		// 40 - 22 - 5 : les 5 inscrits du Kenya et du Ghana, plus 8 inscrits sans pays déclaré.
		assertThat(inscrits.autres()).isEqualTo(13);
	}

	@Test
	void les_pays_sont_classes_du_plus_grand_au_plus_petit_puis_par_code() {
		var inscrits = TableauDeBord.Inscrits.anonymiser(30, 0, Map.of("SN", 10L, "CM", 10L, "KE", 6L));

		assertThat(inscrits.parPays()).extracting(TableauDeBord.Pays::pays).containsExactly("CM", "SN", "KE");
	}

	@Test
	void autres_ne_devient_jamais_negatif_si_les_modules_se_contredisent() {
		assertThat(TableauDeBord.Inscrits.anonymiser(3, 0, Map.of("SN", 10L)).autres()).isZero();
	}

	@Test
	void la_periode_par_defaut_est_le_mois_et_une_periode_inconnue_est_refusee() {
		assertThat(Periode.lire(null)).contains(Periode.MOIS);
		assertThat(Periode.lire("semaine")).contains(Periode.SEMAINE);
		assertThat(Periode.lire("annee")).isEmpty();
		assertThat(Periode.lire("SEMAINE")).as("valeurs du contrat en minuscules").isEmpty();
	}

	@Test
	void tout_ne_limite_pas_la_fenetre() {
		Instant maintenant = Instant.parse("2026-11-13T10:00:00Z");
		assertThat(Periode.TOUT.debut(maintenant)).isNull();
		assertThat(Periode.SEMAINE.debut(maintenant)).isEqualTo(Instant.parse("2026-11-06T10:00:00Z"));
	}

}
