package dev.jamii.api.reseau;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import dev.jamii.api.auth.Annuaire;
import dev.jamii.api.auth.UtilisateurConnecte;

/**
 * Les appels WebRTC d'une discussion privée (contracts/reseau.yaml, #157). Aucune réponse n'est mise en cache : elles
 * portent des descriptions SDP et des identifiants TURN.
 */
@RestController
class AppelsController {

	record NouvelAppel(String offre, Boolean video) {
	}

	record Reponse(String reponse) {
	}

	record Ice(List<ServeursIce.ServeurIce> iceServers, boolean relais) {
	}

	/**
	 * `offre` n'est donnée qu'à la personne appelée tant que ça sonne, `reponse` qu'à l'appelant : chacun ne reçoit
	 * que ce dont son navigateur a besoin.
	 */
	record VueAppel(UUID id, String statut, boolean video, boolean deMoi, ReseauController.PersonneResume personne,
			String offre, String reponse, Instant creeLe, Instant reponduLe, Instant termineLe) {
	}

	private final Appels appels;

	private final ServeursIce ice;

	private final Annuaire annuaire;

	AppelsController(Appels appels, ServeursIce ice, Annuaire annuaire) {
		this.appels = appels;
		this.ice = ice;
		this.annuaire = annuaire;
	}

	@GetMapping("/api/appels/ice")
	ResponseEntity<Ice> ice(@AuthenticationPrincipal UtilisateurConnecte moi) {
		return sansCache(new Ice(ice.pour(moi.id()), ice.relaisConfigure()));
	}

	@PostMapping("/api/conversations/{personneId}/appels")
	ResponseEntity<VueAppel> lancer(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable String personneId,
			@RequestBody(required = false) NouvelAppel corps) {
		if (corps == null) {
			throw ErreurReseau.validation("offre", "obligatoire");
		}
		ErreurReseau.verifierDemo(annuaire, moi.id(), uuid(personneId));
		var appel = appels.lancer(moi.id(), uuid(personneId), corps.offre(), Boolean.TRUE.equals(corps.video()));
		return ResponseEntity.created(URI.create("/api/appels/" + appel.id())).cacheControl(CacheControl.noStore())
			.body(vue(appel, moi.id()));
	}

	@GetMapping("/api/appels/entrant")
	ResponseEntity<VueAppel> entrant(@AuthenticationPrincipal UtilisateurConnecte moi) {
		return appels.entrant(moi.id()).map(a -> sansCache(vue(a, moi.id())))
			.orElseGet(() -> ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build());
	}

	@GetMapping("/api/appels/{appelId}")
	ResponseEntity<VueAppel> lire(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable String appelId) {
		return sansCache(vue(appels.lire(moi.id(), uuid(appelId)), moi.id()));
	}

	@PostMapping("/api/appels/{appelId}/reponse")
	ResponseEntity<VueAppel> repondre(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable String appelId,
			@RequestBody(required = false) Reponse corps) {
		return sansCache(vue(appels.repondre(moi.id(), uuid(appelId), corps == null ? null : corps.reponse()), moi.id()));
	}

	@PostMapping("/api/appels/{appelId}/refus")
	ResponseEntity<VueAppel> refuser(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable String appelId) {
		return sansCache(vue(appels.refuser(moi.id(), uuid(appelId)), moi.id()));
	}

	@PostMapping("/api/appels/{appelId}/fin")
	ResponseEntity<VueAppel> terminer(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable String appelId) {
		return sansCache(vue(appels.terminer(moi.id(), uuid(appelId)), moi.id()));
	}

	private VueAppel vue(Appels.Appel a, UUID moi) {
		UUID autre = a.autre(moi);
		var personne = annuaire.lire(autre)
			.map(c -> new ReseauController.PersonneResume(c.id(), c.nom(), c.avatarUrl()))
			.orElse(new ReseauController.PersonneResume(autre, "", null));
		boolean appelant = a.appelantId().equals(moi);
		return new VueAppel(a.id(), a.statut(), a.video(), appelant, personne,
				!appelant && "sonne".equals(a.statut()) ? a.offre() : null, appelant ? a.reponse() : null, a.creeLe(),
				a.reponduLe(), a.termineLe());
	}

	@org.springframework.web.bind.annotation.ExceptionHandler(ErreurReseau.class)
	ResponseEntity<org.springframework.http.ProblemDetail> erreur(ErreurReseau e) {
		return ReseauController.probleme(e);
	}

	private static <T> ResponseEntity<T> sansCache(T corps) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(corps);
	}

	/** Un identifiant mal formé : la même réponse qu'un appel ou une personne inconnus. */
	private static UUID uuid(String valeur) {
		try {
			return UUID.fromString(valeur);
		}
		catch (IllegalArgumentException e) {
			throw ErreurReseau.introuvable();
		}
	}

}
