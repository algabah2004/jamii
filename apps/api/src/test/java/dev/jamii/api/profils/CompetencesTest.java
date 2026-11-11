package dev.jamii.api.profils;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class CompetencesTest {

	@ParameterizedTest
	@CsvSource({ "React.js,react", "  ReactJS ,react", "postgres,postgresql", "Node,node.js", "C#,c#",
			"Jupyter Notebook,jupyter-notebook", "UX Design,ux-design", "flutter,flutter" })
	void ramene_les_variantes_a_une_seule_forme(String brut, String attendu) {
		assertThat(Competences.normaliser(brut)).contains(attendu);
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "   ", "-flutter", "<script>", "a/b" })
	void refuse_ce_qui_n_est_pas_une_competence(String brut) {
		assertThat(Competences.normaliser(brut)).isEmpty();
	}

	@org.junit.jupiter.api.Test
	void refuse_null_et_trop_long() {
		assertThat(Competences.normaliser(null)).isEmpty();
		assertThat(Competences.normaliser("a".repeat(51))).isEmpty();
	}

}
