package dev.jamii.api.questionnaires;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** La lecture d'un questionnaire et la vérification des réponses, sans base ni HTTP. */
class QuestionnaireTest {

	private final List<String> erreurs = new ArrayList<>();

	private final Questionnaire.Erreurs collecte = (champ, code) -> erreurs.add(champ + ":" + code);

	private static final List<Map<String, Object>> QUESTIONS = List.of(
			Map.of("id", "q1", "intitule", "Ton lien GitHub ou ton portfolio", "type", "texte_court", "obligatoire", true),
			Map.of("id", "q2", "intitule", "Ton niveau en Flutter", "type", "choix_unique", "choix", List.of("Débutant", "À l'aise", "Expert")),
			Map.of("id", "q3", "intitule", "Tes disponibilités", "type", "choix_multiples", "choix", List.of("Soir", "Week-end", "Journée")),
			Map.of("id", "q4", "intitule", "As-tu déjà livré une application ?", "type", "oui_non", "obligatoire", true));

	private List<Questionnaire.Question> questions() {
		List<Questionnaire.Question> q = Questionnaire.lire(QUESTIONS, "questionnaire", collecte);
		assertThat(erreurs).isEmpty();
		return q;
	}

	@Test
	void un_questionnaire_valide_se_lit_et_se_relit_depuis_le_json() {
		List<Questionnaire.Question> q = questions();
		assertThat(q).hasSize(4);
		assertThat(q.get(1).choix()).containsExactly("Débutant", "À l'aise", "Expert");
		assertThat(q.get(0).choix()).isEmpty();
		assertThat(Questionnaire.questions(Questionnaire.enJson(q))).isEqualTo(q);
	}

	@Test
	void un_questionnaire_mal_forme_donne_toutes_ses_erreurs() {
		List<Object> brut = new ArrayList<>(List.of(
				Map.of("id", "q1", "intitule", "Ok ?", "type", "inconnu"),
				Map.of("id", "q1", "intitule", "Un doublon d'identifiant", "type", "texte_long"),
				Map.of("id", "Q 2", "intitule", "x", "type", "choix_unique", "choix", List.of("Seul")),
				Map.of("id", "q3", "intitule", "Choix en double", "type", "choix_multiples", "choix", List.of("A", "A"), "piege", 1)));
		for (int i = 0; i < 8; i++) {
			brut.add(Map.of("id", "x" + i, "intitule", "Question numéro " + i, "type", "texte_court"));
		}
		Questionnaire.lire(brut, "questionnaire", collecte);
		assertThat(erreurs).contains("questionnaire:trop_nombreux", "questionnaire[0].type:valeur_inconnue", "questionnaire[1].id:doublon",
				"questionnaire[2].id:format_invalide", "questionnaire[2].intitule:trop_court", "questionnaire[2].choix:trop_peu",
				"questionnaire[3].choix[1]:doublon", "questionnaire[3].piege:champ_inconnu");
	}

	@Test
	void les_reponses_sont_verifiees_et_rangees_dans_l_ordre_des_questions() {
		List<Questionnaire.Reponse> r = Questionnaire.verifier(List.of(
				Map.of("questionId", "q4", "valeur", "oui"),
				Map.of("questionId", "q3", "valeur", List.of("Soir", "Week-end", "Soir")),
				Map.of("questionId", "q1", "valeur", "  https://github.com/awa  "),
				Map.of("questionId", "q2", "valeur", "")), questions(), "reponses", collecte);
		assertThat(erreurs).isEmpty();
		assertThat(r).containsExactly(new Questionnaire.Reponse("q1", "https://github.com/awa"),
				new Questionnaire.Reponse("q3", List.of("Soir", "Week-end")), new Questionnaire.Reponse("q4", "oui"));
		assertThat(Questionnaire.reponses(Questionnaire.enJson(r))).isEqualTo(r);
	}

	@Test
	void une_reponse_obligatoire_absente_un_choix_inconnu_ou_un_texte_trop_long_sont_refuses() {
		Questionnaire.verifier(List.of(
				Map.of("questionId", "q1", "valeur", "x".repeat(201)),
				Map.of("questionId", "q2", "valeur", "Moyen"),
				Map.of("questionId", "q3", "valeur", "Soir"),
				Map.of("questionId", "q9", "valeur", "?")), questions(), "reponses", collecte);
		assertThat(erreurs).containsExactlyInAnyOrder("reponses[3].questionId:valeur_inconnue", "reponses.q1:trop_long",
				"reponses.q2:valeur_inconnue", "reponses.q3:type_invalide", "reponses.q4:obligatoire");
	}

	@Test
	void sans_questionnaire_aucune_reponse_n_est_attendue() {
		assertThat(Questionnaire.verifier(null, List.of(), "reponses", collecte)).isEmpty();
		assertThat(erreurs).isEmpty();
	}

}
