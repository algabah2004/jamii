package dev.jamii.api.reseau;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import dev.jamii.api.auth.UtilisateurConnecte;

/** « En ligne » et « Vu il y a … » (contracts/reseau.yaml, tag `presence`, #157). */
@RestController
class PresenceController implements WebMvcConfigurer {

	static final int PERSONNES_MAX = 50;

	record Visibilite(Boolean visible) {
	}

	private final Presence presence;

	PresenceController(Presence presence) {
		this.presence = presence;
	}

	/** Chaque requête d'une personne connectée compte comme une activité (Presence.signaler n'écrit qu'une fois par minute). */
	@Override
	public void addInterceptors(InterceptorRegistry registre) {
		registre.addInterceptor(new HandlerInterceptor() {
			@Override
			public boolean preHandle(HttpServletRequest requete, HttpServletResponse reponse, Object handler) {
				var authentification = SecurityContextHolder.getContext().getAuthentication();
				if (authentification != null && authentification.getPrincipal() instanceof UtilisateurConnecte qui) {
					presence.signaler(qui.id(), Instant.now());
				}
				return true;
			}
		}).addPathPatterns("/api/**");
	}

	@GetMapping("/api/presence")
	ResponseEntity<Presence.Lecture> lire(@AuthenticationPrincipal UtilisateurConnecte moi, @RequestParam("personnes") String personnes) {
		List<UUID> ids = new ArrayList<>(new LinkedHashSet<>(ids(personnes)));
		if (ids.isEmpty() || ids.size() > PERSONNES_MAX) {
			throw ErreurReseau.validation("personnes", ids.isEmpty() ? "obligatoire" : "trop_nombreux");
		}
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(presence.lire(moi.id(), ids, Instant.now()));
	}

	@GetMapping("/api/me/presence")
	ResponseEntity<Visibilite> maVisibilite(@AuthenticationPrincipal UtilisateurConnecte moi) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Visibilite(presence.visible(moi.id())));
	}

	@PutMapping("/api/me/presence")
	Visibilite changer(@AuthenticationPrincipal UtilisateurConnecte moi, @RequestBody(required = false) Visibilite corps) {
		if (corps == null || corps.visible() == null) {
			throw ErreurReseau.validation("visible", "obligatoire");
		}
		presence.changerVisibilite(moi.id(), corps.visible());
		return new Visibilite(corps.visible());
	}

	private static List<UUID> ids(String texte) {
		List<UUID> ids = new ArrayList<>();
		for (String morceau : texte.split(",")) {
			if (morceau.isBlank()) continue;
			try {
				ids.add(UUID.fromString(morceau.strip()));
			}
			catch (IllegalArgumentException e) {
				throw ErreurReseau.validation("personnes", "format_invalide");
			}
		}
		return ids;
	}

	@ExceptionHandler(ErreurReseau.class)
	ResponseEntity<org.springframework.http.ProblemDetail> erreur(ErreurReseau e) {
		return ReseauController.probleme(e);
	}

}
