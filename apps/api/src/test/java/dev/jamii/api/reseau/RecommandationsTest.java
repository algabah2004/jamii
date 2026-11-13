package dev.jamii.api.reseau;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import dev.jamii.api.profils.ProfilsPublies;

/** Le calcul des recommandations, sans base : des fonctions pures. */
class RecommandationsTest {

	private static final UUID AWA = UUID.randomUUID();

	private static final UUID KOFI = UUID.randomUUID();

	private static ProfilsPublies.Resume profil(UUID id, String ville, String disponibilite, String... competences) {
		return new ProfilsPublies.Resume(id, "SN", ville, disponibilite, 10, List.of(competences));
	}

	private static LectureCercles.CercleLu cercle(String ville, int taille, String statut, UUID... membres) {
		return new LectureCercles.CercleLu(UUID.randomUUID(), "Cercle", ville, taille, statut, membres[0], Instant.now(),
				List.of(membres));
	}

	@Test
	void recommande_un_cercle_a_qui_manque_ma_competence_et_dit_pourquoi() {
		var moi = profil(AWA, "Dakar", "disponible", "flutter", "react");
		var abidjan = cercle("Abidjan", 5, "ouvert", KOFI);
		var recos = Recommandations.cerclesPour(moi, List.of(abidjan), Map.of(KOFI, List.of("react")), 6);
		assertThat(recos).singleElement().satisfies(r -> {
			assertThat(r.competences()).containsExactly("flutter");
			assertThat(r.raisons()).containsExactly(Recommandations.COMPETENCE_MANQUANTE);
		});
	}

	@Test
	void la_meme_ville_passe_devant_et_presque_complet_se_dit() {
		var moi = profil(AWA, "dakar ", "disponible", "flutter");
		var abidjan = cercle("Abidjan", 5, "ouvert", KOFI);
		var dakar = cercle("Dakar", 2 + 2, "ouvert", KOFI, UUID.randomUUID(), UUID.randomUUID());
		var recos = Recommandations.cerclesPour(moi, List.of(abidjan, dakar), Map.of(), 6);
		assertThat(recos).extracting(r -> r.cercle().ville()).containsExactly("Dakar", "Abidjan");
		assertThat(recos.get(0).raisons()).containsExactly(Recommandations.COMPETENCE_MANQUANTE,
				Recommandations.MEME_VILLE, Recommandations.PRESQUE_COMPLET);
	}

	@Test
	void ne_recommande_ni_un_cercle_qui_a_deja_tout_ni_un_cercle_ferme() {
		var moi = profil(AWA, "Dakar", "disponible", "flutter");
		var couvert = cercle("Abidjan", 5, "ouvert", KOFI);
		var tire = cercle("Dakar", 4, "en_cours", KOFI);
		assertThat(Recommandations.cerclesPour(moi, List.of(couvert, tire), Map.of(KOFI, List.of("flutter")), 6)).isEmpty();
	}

	@Test
	void propose_au_cercle_les_profils_disponibles_qui_apportent_ce_qui_manque() {
		var cercle = cercle("Saint-Louis", 4, "ouvert", AWA);
		UUID ibrahima = UUID.randomUUID();
		UUID kwame = UUID.randomUUID();
		UUID zawadi = UUID.randomUUID();
		var candidats = List.of(profil(kwame, "Accra", "disponible", "postgresql", "typescript"),
				profil(ibrahima, "Saint-Louis", "disponible", "spring-boot"),
				profil(zawadi, "Nairobi", "occupe", "postgresql"), profil(AWA, "Saint-Louis", "disponible", "rust"),
				profil(UUID.randomUUID(), "Lagos", "disponible", "flutter"));
		var competences = Map.of(AWA, List.of("flutter"));
		var recos = Recommandations.profilsPour(cercle, competences, candidats, 6);
		// Même ville d'abord ; l'occupée, le membre et celui qui n'apporte rien sont écartés.
		assertThat(recos).extracting(r -> r.profil().utilisateurId()).containsExactly(ibrahima, kwame);
		assertThat(recos.get(1).competences()).containsExactly("postgresql", "typescript");
		assertThat(recos.get(1).raisons()).containsExactly(Recommandations.COMPETENCE_MANQUANTE, Recommandations.DISPONIBLE);
		assertThat(Recommandations.manquantes(cercle, competences, candidats)).startsWith("postgresql")
			.doesNotContain("flutter");
	}

}
