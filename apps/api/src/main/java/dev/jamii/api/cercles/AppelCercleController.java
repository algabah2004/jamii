package dev.jamii.api.cercles;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import dev.jamii.api.auth.Annuaire;
import dev.jamii.api.auth.Compte;
import dev.jamii.api.auth.UtilisateurConnecte;
import dev.jamii.api.cercles.CerclesController.PersonneResume;

/**
 * La salle WebRTC de l'appel du cercle (contracts/cercles.yaml, #157). Réservée aux membres, et seulement pendant un
 * appel en cours (lancé par `POST /api/cercles/{id}/salon/appel`). Rien n'est mis en cache : les réponses portent des
 * descriptions SDP.
 */
@RestController
class AppelCercleController {

	record PresentVue(PersonneResume personne, Instant arriveLe) {
	}

	record SignalVue(UUID de, String type, String sdp) {
	}

	record Salle(UUID appelId, List<PresentVue> presents, List<SignalVue> signaux) {
	}

	record NouveauSignal(UUID a, String type, String sdp) {
	}

	private final GestionCercles gestion;

	private final SalonCercle salon;

	private final SalleAppelCercle salle;

	private final Annuaire annuaire;

	AppelCercleController(GestionCercles gestion, SalonCercle salon, SalleAppelCercle salle, Annuaire annuaire) {
		this.gestion = gestion;
		this.salon = salon;
		this.salle = salle;
		this.annuaire = annuaire;
	}

	/** Je suis dans l'appel (toutes les 1,5 s) : qui est là, et les offres ou réponses qui m'attendent. */
	@PostMapping("/api/cercles/{cercleId}/salon/appel/presence")
	ResponseEntity<Salle> present(@PathVariable UUID cercleId, @AuthenticationPrincipal UtilisateurConnecte moi) {
		Instant maintenant = Instant.now();
		var appel = appelEnCours(cercleId, moi.id(), maintenant);
		var etat = salle.present(appel.id(), moi.id(), maintenant);
		Map<UUID, Compte> comptes = annuaire.lire(etat.presents().stream().map(SalleAppelCercle.Present::id).toList());
		var presents = etat.presents().stream()
			.map(p -> new PresentVue(comptes.containsKey(p.id()) ? CerclesController.personne(comptes.get(p.id()))
					: new PersonneResume(p.id(), "", null), p.arriveLe()))
			.toList();
		var signaux = etat.signaux().stream().map(s -> new SignalVue(s.de(), s.type(), s.sdp())).toList();
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Salle(appel.id(), presents, signaux));
	}

	/** Une offre ou une réponse pour un autre présent. */
	@PostMapping("/api/cercles/{cercleId}/salon/appel/signaux")
	ResponseEntity<Void> envoyer(@PathVariable UUID cercleId, @AuthenticationPrincipal UtilisateurConnecte moi,
			@RequestBody(required = false) NouveauSignal corps) {
		if (corps == null || corps.a() == null) {
			throw CerclesController.validation("a", "obligatoire");
		}
		Instant maintenant = Instant.now();
		var appel = appelEnCours(cercleId, moi.id(), maintenant);
		salle.envoyer(appel.id(), moi.id(), corps.a(), corps.type(), corps.sdp(), maintenant);
		return ResponseEntity.noContent().build();
	}

	/** Je quitte l'appel ; le dernier à partir le termine. Sans effet s'il est déjà fini. */
	@DeleteMapping("/api/cercles/{cercleId}/salon/appel/presence")
	ResponseEntity<Void> quitter(@PathVariable UUID cercleId, @AuthenticationPrincipal UtilisateurConnecte moi) {
		Instant maintenant = Instant.now();
		membre(cercleId, moi.id());
		salon.appelEnCours(cercleId, maintenant).ifPresent(a -> salle.quitter(a.id(), moi.id(), maintenant));
		return ResponseEntity.noContent().build();
	}

	@ExceptionHandler(ErreurCercle.class)
	ResponseEntity<ProblemDetail> erreur(ErreurCercle e) {
		return CerclesController.probleme(e);
	}

	private SalonCercle.Appel appelEnCours(UUID cercleId, UUID moi, Instant maintenant) {
		membre(cercleId, moi);
		return salon.appelEnCours(cercleId, maintenant)
			.orElseThrow(() -> new ErreurCercle(HttpStatus.NOT_FOUND, "pas_d_appel", "Aucun appel en cours dans ce cercle"));
	}

	private void membre(UUID cercleId, UUID moi) {
		var vue = gestion.lire(cercleId).orElseThrow(ErreurCercle::introuvable);
		if (vue.membres().stream().noneMatch(m -> m.utilisateurId().equals(moi))) {
			throw new ErreurCercle(HttpStatus.FORBIDDEN, "pas_membre", "L'appel est réservé aux membres du cercle");
		}
	}

}
