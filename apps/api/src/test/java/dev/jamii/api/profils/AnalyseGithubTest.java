package dev.jamii.api.profils;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

class AnalyseGithubTest {

	private final AnalyseGithub.Resultat resultat = AnalyseGithub
		.analyser(new SourceGithubSimulee().depotsPublics("awa-dev"));

	@Test
	void les_forks_sont_ecartes() {
		assertThat(resultat.depots()).extracting(Profil.DepotGithub::nom).doesNotContain("flutter-samples");
	}

	@Test
	void langages_ponderes_par_taille_et_tries() {
		// Dart : 5200 + 1800 Ko, TypeScript : 2100, HTML : 300 (le fork Dart de 9000 Ko ne compte pas).
		assertThat(resultat.langages()).extracting(Profil.Langage::nom).containsExactly("Dart", "TypeScript", "HTML");
		assertThat(resultat.langages().get(0).part()).isEqualByComparingTo(new BigDecimal("0.745"));
	}

	@Test
	void competences_depuis_langages_puis_sujets_normalises() {
		assertThat(resultat.competences()).containsExactly("dart", "typescript", "html", "flutter", "firebase", "node.js",
				"postgresql");
	}

	@Test
	void depots_les_plus_suivis_d_abord() {
		assertThat(resultat.depots()).extracting(Profil.DepotGithub::nom)
			.containsExactly("marche-facile", "api-paiements", "agri-alertes", "portfolio");
	}

	@Test
	void aucun_depot_donne_un_profil_vide_sans_erreur() {
		var vide = AnalyseGithub.analyser(List.of());
		assertThat(vide.langages()).isEmpty();
		assertThat(vide.depots()).isEmpty();
		assertThat(vide.competences()).isEmpty();
	}

}
