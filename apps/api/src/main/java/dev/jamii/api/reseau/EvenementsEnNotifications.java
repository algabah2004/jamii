package dev.jamii.api.reseau;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import dev.jamii.api.hackathons.InscriptionRecue;
import dev.jamii.api.hackathons.InscriptionTriee;
import dev.jamii.api.hackathons.InvitationEquipe;
import dev.jamii.api.missions.DevisEnvoye;
import dev.jamii.api.missions.MissionAttribuee;
import dev.jamii.api.missions.PropositionRecue;

/**
 * Les événements publics des missions et des hackathons, transformés en notifications (demande #201) : la bonne
 * personne est prévenue sans que ce module lise les tables des autres.
 *
 * <p>Chaque écoute attend que l'action d'origine soit validée (`AFTER_COMMIT`) : une notification n'existe jamais pour
 * une action annulée, et une panne ici ne peut pas défaire une inscription ou une proposition (l'erreur est journalisée,
 * pas relancée). L'écriture se fait dans sa propre transaction (`REQUIRES_NEW`), comme Spring l'exige après le commit.
 *
 * <p>Le lien suit la contrainte de la table (`^/[a-z0-9/-]*$`) : pas de paramètre d'adresse, on mène à la page.
 * L'identité de la personne à l'origine se lit par `personne_id` ; quand l'événement n'en porte pas (le tri d'une
 * candidature), la phrase s'en passe.
 */
@Component
class EvenementsEnNotifications {

	private final Notifications notifications;

	EvenementsEnNotifications(Notifications notifications) {
		this.notifications = notifications;
	}

	// ---------------------------------------------------------------- missions

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	void proposition(PropositionRecue e) {
		notifications.notifier(e.clientId(), "proposition_recue", e.developpeurId(), null, "/missions/" + e.missionId());
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	void devis(DevisEnvoye e) {
		notifications.notifier(e.clientId(), "devis_recu", e.developpeurId(), null, "/missions/" + e.missionId());
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	void attribution(MissionAttribuee e) {
		notifications.notifier(e.developpeurId(), "mission_attribuee", e.clientId(), null, "/missions/" + e.missionId());
	}

	// ---------------------------------------------------------------- hackathons

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	void candidature(InscriptionRecue e) {
		notifications.notifier(e.organisateurId(), "candidature_recue", e.utilisateurId(), null, "/hackathons/" + e.hackathonId());
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	void tri(InscriptionTriee e) {
		// `acceptee`, `refusee` ou `attente` : un type par décision, pour que la phrase dise laquelle.
		notifications.notifier(e.utilisateurId(), "candidature_" + e.statut(), null, null, "/hackathons/" + e.hackathonId());
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	void invitation(InvitationEquipe e) {
		notifications.notifier(e.inviteId(), "invitation_equipe", e.chefId(), null, "/hackathons/" + e.hackathonId());
	}

}
