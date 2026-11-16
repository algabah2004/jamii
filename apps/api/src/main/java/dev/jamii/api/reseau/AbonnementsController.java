package dev.jamii.api.reseau;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.jamii.api.auth.Annuaire;
import dev.jamii.api.auth.Compte;
import dev.jamii.api.auth.UtilisateurConnecte;
import dev.jamii.api.cercles.CercleCree;
import dev.jamii.api.communautes.PublicationCreee;
import dev.jamii.api.profils.ProfilsPublies;

/** Suivre quelqu'un (contracts/reseau.yaml, tag `abonnes`, #157). */
@RestController
class AbonnementsController {

	private static final Logger journal = LoggerFactory.getLogger(AbonnementsController.class);

	record Abonne(ReseauController.Personne personne, Instant depuis) {
	}

	record ListeAbonnes(List<Abonne> elements, long total) {
	}

	private final Abonnements abonnements;

	private final Annuaire annuaire;

	private final ProfilsPublies profils;

	AbonnementsController(Abonnements abonnements, Annuaire annuaire, ProfilsPublies profils) {
		this.abonnements = abonnements;
		this.annuaire = annuaire;
		this.profils = profils;
	}

	@GetMapping("/api/personnes/{personneId}/abonnement")
	Abonnements.Suivi lire(@PathVariable UUID personneId, @AuthenticationPrincipal UtilisateurConnecte moi) {
		if (!abonnements.existe(personneId)) {
			throw ErreurReseau.introuvable();
		}
		return abonnements.suivi(moi.id(), personneId);
	}

	@PutMapping("/api/personnes/{personneId}/abonnement")
	Abonnements.Suivi suivre(@PathVariable UUID personneId, @AuthenticationPrincipal UtilisateurConnecte moi) {
		abonnements.suivre(moi.id(), personneId, Instant.now());
		return abonnements.suivi(moi.id(), personneId);
	}

	@DeleteMapping("/api/personnes/{personneId}/abonnement")
	Abonnements.Suivi neplusSuivre(@PathVariable UUID personneId, @AuthenticationPrincipal UtilisateurConnecte moi) {
		abonnements.neplusSuivre(moi.id(), personneId);
		return abonnements.suivi(moi.id(), personneId);
	}

	@GetMapping("/api/personnes/{personneId}/abonnes")
	ListeAbonnes abonnes(@PathVariable UUID personneId, @RequestParam(defaultValue = "50") int limite) {
		return liste(personneId, true, limite);
	}

	@GetMapping("/api/personnes/{personneId}/abonnements")
	ListeAbonnes suivis(@PathVariable UUID personneId, @RequestParam(defaultValue = "50") int limite) {
		return liste(personneId, false, limite);
	}

	private ListeAbonnes liste(UUID personne, boolean abonnes, int limite) {
		if (limite < 1 || limite > Abonnements.LISTE_MAX) {
			throw ErreurReseau.validation("limite", "hors_limites");
		}
		if (!abonnements.existe(personne)) {
			throw ErreurReseau.introuvable();
		}
		List<Abonnements.Lien> liens = abonnements.lister(personne, abonnes, limite);
		Map<UUID, Compte> comptes = annuaire.lire(liens.stream().map(Abonnements.Lien::personneId).toList());
		List<Abonne> elements = liens.stream().filter(l -> comptes.containsKey(l.personneId())).map(l -> {
			Compte c = comptes.get(l.personneId());
			String ville = profils.publie(c.id()).map(ProfilsPublies.Resume::ville).orElse(null);
			return new Abonne(new ReseauController.Personne(c.id(), c.nom(), c.avatarUrl(), ville), l.depuis());
		}).toList();
		Abonnements.Suivi s = abonnements.suivi(personne, personne);
		return new ListeAbonnes(elements, abonnes ? s.abonnes() : s.abonnements());
	}

	// ---------------------------------------------------------------- prévenir les abonnés

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	void cercle(CercleCree e) {
		try {
			abonnements.prevenirAbonnes(e.createurId(), "suivi_cercle", e.cercleId(), "/cercles/" + e.cercleId(), e.creeLe());
		}
		catch (RuntimeException panne) {
			journal.warn("Abonnés non prévenus du cercle {}", e.cercleId(), panne);
		}
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	void publication(PublicationCreee e) {
		try {
			abonnements.prevenirAbonnes(e.auteurId(), "suivi_publication", null, "/publications/" + e.publicationId(), e.creeLe());
		}
		catch (RuntimeException panne) {
			journal.warn("Abonnés non prévenus de la publication {}", e.publicationId(), panne);
		}
	}

	@ExceptionHandler(ErreurReseau.class)
	ResponseEntity<ProblemDetail> erreur(ErreurReseau e) {
		return ReseauController.probleme(e);
	}

}
