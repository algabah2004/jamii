package dev.jamii.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class CheminRetourTest {

	@ParameterizedTest
	@ValueSource(strings = { "/", "/projets/42", "/projets?onglet=equipe#taches", "/communautes/dakar" })
	void garde_un_chemin_relatif_du_front(String retour) {
		assertThat(CheminRetour.valider(retour)).isEqualTo(retour);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "//faux-site.com", "https://faux-site.com", "faux-site.com", "/\\faux-site.com",
			"/%2Ffaux-site.com", "/%2f%2ffaux-site.com", "/%5Cfaux-site.com", "/a b", "/%", "javascript:alert(1)" })
	void refuse_tout_ce_qui_peut_sortir_du_front(String retour) {
		assertThat(CheminRetour.valider(retour)).isEqualTo("/");
	}

	@ParameterizedTest
	@ValueSource(ints = { 513, 10_000 })
	void refuse_un_chemin_trop_long(int longueur) {
		assertThat(CheminRetour.valider("/" + "a".repeat(longueur - 1))).isEqualTo("/");
	}

}
