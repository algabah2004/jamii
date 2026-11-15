package dev.jamii.api.reseau;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import dev.jamii.api.TestcontainersConfiguration;
import dev.jamii.api.hackathons.InvitationEquipe;
import dev.jamii.api.missions.DevisEnvoye;
import dev.jamii.api.missions.MissionAttribuee;
import dev.jamii.api.missions.PropositionRecue;

/**
 * Les événements publics des missions et des hackathons deviennent des notifications (demande #201), sur un vrai
 * PostgreSQL. Les événements sont publiés dans une transaction, comme le font les modules d'origine.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class EvenementsEnNotificationsTest {

	@Autowired
	ApplicationEventPublisher evenements;

	@Autowired
	TransactionTemplate transaction;

	@Autowired
	JdbcClient base;

	private UUID utilisateur(String nom) {
		UUID id = UUID.randomUUID();
		Timestamp t = Timestamp.from(Instant.now());
		base.sql("INSERT INTO utilisateur (id, nom, email, cree_le, mis_a_jour_le) VALUES (:id, :nom, :email, :t, :t)")
			.param("id", id).param("nom", nom).param("email", nom + "." + id + "@secret.example").param("t", t).update();
		return id;
	}

	private record Ligne(String type, UUID personneId, String lien) {
	}

	private List<Ligne> notifications(UUID personne) {
		return base.sql("SELECT type, personne_id, lien FROM notification WHERE utilisateur_id = :u ORDER BY type")
			.param("u", personne).query((rs, i) -> new Ligne(rs.getString("type"), rs.getObject("personne_id", UUID.class), rs.getString("lien")))
			.list();
	}

	@Test
	void le_client_est_prevenu_d_une_proposition_et_d_un_devis_et_le_developpeur_d_une_attribution() {
		UUID client = utilisateur("awa");
		UUID developpeur = utilisateur("kofi");
		UUID mission = UUID.randomUUID();
		Instant maintenant = Instant.now();

		transaction.executeWithoutResult(s -> {
			evenements.publishEvent(new PropositionRecue(mission, UUID.randomUUID(), client, developpeur, maintenant));
			evenements.publishEvent(new DevisEnvoye(mission, UUID.randomUUID(), client, developpeur, maintenant));
			evenements.publishEvent(new MissionAttribuee(mission, UUID.randomUUID(), client, developpeur, List.of(), maintenant));
		});

		// Le client : une proposition et un devis, de la part du développeur, qui mènent à la mission.
		assertThat(notifications(client)).containsExactly(
				new Ligne("devis_recu", developpeur, "/missions/" + mission),
				new Ligne("proposition_recue", developpeur, "/missions/" + mission));
		// Le développeur : « le client t'a choisi ».
		assertThat(notifications(developpeur)).containsExactly(new Ligne("mission_attribuee", client, "/missions/" + mission));
	}

	@Test
	void le_chef_d_equipe_invite_une_personne_qui_est_prevenue() {
		UUID chef = utilisateur("fatou");
		UUID invite = utilisateur("moussa");
		UUID hackathon = UUID.randomUUID();

		transaction.executeWithoutResult(s ->
				evenements.publishEvent(new InvitationEquipe(hackathon, UUID.randomUUID(), invite, chef, Instant.now())));

		assertThat(notifications(invite)).containsExactly(new Ligne("invitation_equipe", chef, "/hackathons/" + hackathon));
		assertThat(notifications(chef)).isEmpty();
	}

	@Test
	void une_action_annulee_ne_previent_personne() {
		UUID client = utilisateur("awa");
		UUID developpeur = utilisateur("kofi");

		transaction.executeWithoutResult(s -> {
			evenements.publishEvent(new PropositionRecue(UUID.randomUUID(), UUID.randomUUID(), client, developpeur, Instant.now()));
			s.setRollbackOnly();
		});

		assertThat(notifications(client)).isEmpty();
	}

}
