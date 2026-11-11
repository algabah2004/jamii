package dev.jamii.api.projets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;

import dev.jamii.api.TestcontainersConfiguration;
import dev.jamii.api.systalink.ChiffresDesModules;

/** L'accès aux projets et les contraintes de la migration V6, sur un vrai PostgreSQL. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ProjetsTest {

	@Autowired
	Projets projets;

	@Autowired
	ChiffresDesModules.Projets chiffres;

	@Autowired
	JdbcClient base;

	private UUID utilisateur() {
		UUID id = UUID.randomUUID();
		Timestamp maintenant = Timestamp.from(Instant.now());
		base.sql("INSERT INTO utilisateur (id, nom, cree_le, mis_a_jour_le) VALUES (:id, 'Test', :t, :t)")
			.param("id", id).param("t", maintenant).update();
		return id;
	}

	private UUID projet(UUID porteur, String statut, UUID demande, Instant creeLe) {
		UUID id = UUID.randomUUID();
		Timestamp t = Timestamp.from(creeLe);
		base.sql("""
				INSERT INTO projet (id, titre, origine, demande_id, porteur_id, statut, derniere_activite_le, cree_le, mis_a_jour_le)
				VALUES (:id, 'Un projet', :origine, :demande, :porteur, :statut, :t, :t, :t)
				""")
			.param("id", id).param("origine", demande == null ? "idee" : "demande").param("demande", demande)
			.param("porteur", porteur).param("statut", statut).param("t", t).update();
		membre(id, porteur, null);
		return id;
	}

	private void membre(UUID projet, UUID utilisateur, Instant partiLe) {
		base.sql("INSERT INTO membre_equipe (projet_id, utilisateur_id, rejoint_le, parti_le) VALUES (:p, :u, now(), :parti)")
			.param("p", projet).param("u", utilisateur).param("parti", partiLe == null ? null : Timestamp.from(partiLe)).update();
	}

	@Test
	void projet_inexistant_est_introuvable() {
		assertThat(projets.acces(UUID.randomUUID(), utilisateur())).isEqualTo(AccesAuProjet.INTROUVABLE);
	}

	@Test
	void le_porteur_et_les_membres_sont_membres() {
		UUID porteur = utilisateur();
		UUID coequipier = utilisateur();
		UUID projet = projet(porteur, "recrutement", null, Instant.now());
		membre(projet, coequipier, null);

		assertThat(projets.acces(projet, porteur)).isEqualTo(AccesAuProjet.MEMBRE);
		assertThat(projets.acces(projet, coequipier)).isEqualTo(AccesAuProjet.MEMBRE);
	}

	@Test
	void tout_autre_utilisateur_connecte_est_lecteur() {
		UUID projet = projet(utilisateur(), "recrutement", null, Instant.now());

		assertThat(projets.acces(projet, utilisateur())).isEqualTo(AccesAuProjet.LECTEUR);
	}

	@Test
	void un_membre_parti_redevient_lecteur() {
		UUID porteur = utilisateur();
		UUID parti = utilisateur();
		UUID projet = projet(porteur, "en_cours", null, Instant.now());
		membre(projet, parti, Instant.now());

		assertThat(projets.acces(projet, parti)).isEqualTo(AccesAuProjet.LECTEUR);
		assertThat(projets.acces(projet, porteur)).isEqualTo(AccesAuProjet.MEMBRE);
	}

	@Test
	void les_projets_termines_ou_abandonnes_restent_visibles() {
		UUID porteur = utilisateur();
		for (String statut : new String[] { "en_pause", "livre", "abandonne" }) {
			assertThat(projets.acces(projet(porteur, statut, null, Instant.now()), utilisateur())).as(statut)
				.isEqualTo(AccesAuProjet.LECTEUR);
		}
	}

	@Test
	void compte_les_projets_depuis_une_date() {
		long avant = chiffres.compter(null);
		Instant debut = Instant.now().minus(1, ChronoUnit.MINUTES);
		projet(utilisateur(), "recrutement", null, Instant.now());
		projet(utilisateur(), "abandonne", null, Instant.now().minus(30, ChronoUnit.DAYS));

		assertThat(chiffres.compter(null)).isEqualTo(avant + 2);
		assertThat(chiffres.compter(debut)).isGreaterThanOrEqualTo(1);
		assertThat(chiffres.compter(Instant.now().plus(1, ChronoUnit.DAYS))).isZero();
	}

	@Test
	void le_bean_du_tableau_de_bord_est_le_service_projets() {
		assertThat(chiffres).isSameAs(projets);
	}

	@Test
	void une_demande_n_a_qu_un_seul_projet_actif() {
		UUID demande = UUID.randomUUID();
		UUID premier = projet(utilisateur(), "recrutement", demande, Instant.now());

		assertThatThrownBy(() -> projet(utilisateur(), "en_cours", demande, Instant.now()))
			.isInstanceOf(DataIntegrityViolationException.class);

		// Une fois le premier abandonné, la demande peut être reprise par une autre équipe.
		base.sql("UPDATE projet SET statut = 'abandonne' WHERE id = :id").param("id", premier).update();
		projet(utilisateur(), "recrutement", demande, Instant.now());
	}

	@Test
	void statut_inconnu_et_origine_inconnue_sont_refuses() {
		UUID porteur = utilisateur();

		assertThatThrownBy(() -> projet(porteur, "secret", null, Instant.now()))
			.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> base.sql("""
				INSERT INTO projet (id, titre, origine, porteur_id, derniere_activite_le, cree_le, mis_a_jour_le)
				VALUES (gen_random_uuid(), 'x', 'autre', :p, now(), now(), now())
				""").param("p", porteur).update()).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void supprimer_un_compte_retire_son_appartenance_mais_pas_les_projets_qu_il_porte() {
		UUID porteur = utilisateur();
		UUID membre = utilisateur();
		UUID projet = projet(porteur, "en_cours", null, Instant.now());
		membre(projet, membre, null);

		base.sql("DELETE FROM utilisateur WHERE id = :id").param("id", membre).update();
		assertThat(projets.acces(projet, membre)).as("plus dans l'équipe").isEqualTo(AccesAuProjet.LECTEUR);

		assertThatThrownBy(() -> base.sql("DELETE FROM utilisateur WHERE id = :id").param("id", porteur).update())
			.as("le porteur doit d'abord transmettre son projet")
			.isInstanceOf(DataIntegrityViolationException.class);
	}

}
