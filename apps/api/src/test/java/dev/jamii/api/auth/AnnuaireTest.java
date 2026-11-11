package dev.jamii.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;

import dev.jamii.api.TestcontainersConfiguration;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AnnuaireTest {

	@Autowired
	Annuaire annuaire;

	@Autowired
	ComptesGithub comptes;

	@Autowired
	JdbcClient base;

	@Test
	void lit_un_compte_sans_jamais_montrer_l_email() {
		UtilisateurConnecte awa = comptes.enregistrer(2001, "awa-annuaire", "Awa Diallo", "https://avatars.example/2001");
		base.sql("UPDATE utilisateur SET email = 'awa@example.org' WHERE id = :id").param("id", awa.id()).update();

		Compte compte = annuaire.lire(awa.id()).orElseThrow();

		assertThat(compte).isEqualTo(new Compte(awa.id(), "Awa Diallo", "https://avatars.example/2001", "awa-annuaire"));
		assertThat(Compte.class.getRecordComponents()).extracting("name")
			.as("le type public ne porte aucune donnée privée")
			.containsExactly("id", "nom", "avatarUrl", "loginGithub");
	}

	@Test
	void compte_inconnu_ou_supprime_est_absent() {
		assertThat(annuaire.lire(UUID.randomUUID())).isEmpty();

		UtilisateurConnecte parti = comptes.enregistrer(2002, "parti", "Parti", null);
		base.sql("DELETE FROM utilisateur_role WHERE utilisateur_id = :id").param("id", parti.id()).update();
		base.sql("DELETE FROM utilisateur WHERE id = :id").param("id", parti.id()).update();

		assertThat(annuaire.lire(parti.id())).isEmpty();
	}

	@Test
	void lit_une_liste_en_ignorant_les_inconnus() {
		UtilisateurConnecte a = comptes.enregistrer(2003, "liste-a", "A", null);
		UtilisateurConnecte b = comptes.enregistrer(2004, "liste-b", "B", null);
		UUID inconnu = UUID.randomUUID();

		Map<UUID, Compte> lus = annuaire.lire(List.of(a.id(), b.id(), inconnu));

		assertThat(lus).containsOnlyKeys(a.id(), b.id());
		assertThat(lus.get(b.id()).loginGithub()).isEqualTo("liste-b");
		assertThat(annuaire.lire(Set.<UUID>of())).isEmpty();
	}

	@Test
	void compte_les_inscrits_depuis_une_date() {
		long avant = annuaire.compterInscrits(null);
		Instant debut = Instant.now().minus(1, ChronoUnit.MINUTES);
		comptes.enregistrer(2005, "nouveau", "Nouveau", null);

		assertThat(annuaire.compterInscrits(null)).isEqualTo(avant + 1);
		assertThat(annuaire.compterInscrits(debut)).isGreaterThanOrEqualTo(1);
		assertThat(annuaire.compterInscrits(Instant.now().plus(1, ChronoUnit.DAYS))).isZero();
	}

}
