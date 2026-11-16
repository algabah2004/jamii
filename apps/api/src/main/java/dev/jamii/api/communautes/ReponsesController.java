package dev.jamii.api.communautes;

import java.time.Instant;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import dev.jamii.api.auth.Annuaire;
import dev.jamii.api.auth.UtilisateurConnecte;

/**
 * Répondre et accepter une réponse (contracts/communautes.yaml). Les droits sont
 * vérifiés ici, à chaque appel : le front n'affiche les boutons que par confort.
 */
@RestController
class ReponsesController {

	record NouvelleReponse(String contenu) {
	}

	private final GestionCommunautes communautes;

	private final GestionReponses reponses;

	private final VuesPublications lecture;

	private final Annuaire annuaire;

	private final ApplicationEventPublisher evenements;

	ReponsesController(GestionCommunautes communautes, GestionReponses reponses, VuesPublications lecture,
			Annuaire annuaire, ApplicationEventPublisher evenements) {
		this.communautes = communautes;
		this.reponses = reponses;
		this.lecture = lecture;
		this.annuaire = annuaire;
		this.evenements = evenements;
	}

	@PostMapping("/api/publications/{publicationId}/reponses")
	ResponseEntity<?> repondre(@PathVariable UUID publicationId, @RequestBody NouvelleReponse demande,
			@AuthenticationPrincipal UtilisateurConnecte moi) {
		String contenu = demande.contenu() == null ? "" : demande.contenu().strip();
		if (contenu.length() < 10 || contenu.length() > 10000) {
			return Problemes.validation("contenu", "longueur_invalide");
		}
		var publication = communautes.lirePublication(publicationId);
		if (publication.isEmpty()) {
			return Problemes.introuvable();
		}
		if (!communautes.estMembre(publication.get().communauteId(), moi.id())) {
			return Problemes.interdit("pas_membre", "Rejoignez la communauté pour répondre");
		}
		Instant maintenant = Instant.now();
		long attente = reponses.attenteAvantReponse(moi.id(), maintenant);
		if (attente > 0) {
			return Problemes.tropDeRequetes(attente, "Trop de réponses en peu de temps");
		}
		UUID id = reponses.repondre(publicationId, moi.id(), contenu, maintenant);
		var auteur = annuaire.lire(moi.id()).map(c -> new CommunautesController.PersonneResume(c.id(), c.nom(), c.avatarUrl())).orElse(null);
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(new CommunautesController.Reponse(id, contenu, auteur, false, maintenant));
	}

	@PostMapping("/api/reponses/{reponseId}/accepter")
	ResponseEntity<?> accepter(@PathVariable UUID reponseId, @AuthenticationPrincipal UtilisateurConnecte moi) {
		var trouvee = reponses.cible(reponseId);
		if (trouvee.isEmpty()) {
			return Problemes.introuvable();
		}
		var r = trouvee.get();
		if (!moi.id().equals(r.auteurQuestion())) {
			return Problemes.interdit("pas_auteur_question", "Seul l'auteur de la question accepte une réponse");
		}
		if (!"question".equals(r.type())) {
			return Problemes.conflit("pas_une_question", "Seule une question a une réponse acceptée");
		}
		// Sans cette règle, on gagnerait une preuve sur son profil en répondant à sa propre question.
		if (moi.id().equals(r.auteurReponse())) {
			return Problemes.conflit("propre_reponse", "On n'accepte pas sa propre réponse");
		}
		Instant maintenant = Instant.now();
		if (!reponses.accepter(r.publicationId(), reponseId, maintenant)) {
			return Problemes.conflit("deja_acceptee", "Cette question a déjà une réponse acceptée");
		}
		if (r.auteurReponse() != null) {
			evenements.publishEvent(new ReponseAcceptee(reponseId, r.publicationId(), r.auteurReponse(), maintenant));
		}
		return ResponseEntity.ok(lecture.publication(r.publicationId(), moi.id()));
	}

}
