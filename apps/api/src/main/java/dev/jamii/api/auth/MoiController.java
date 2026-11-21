package dev.jamii.api.auth;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.ServletException;

import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/me (contrat : opération « moi » de contracts/socle.yaml). */
@RestController
class MoiController {

	private final UtilisateurRepository utilisateurs;

	MoiController(UtilisateurRepository utilisateurs) {
		this.utilisateurs = utilisateurs;
	}

	@GetMapping("/api/me")
	Moi moi(@AuthenticationPrincipal UtilisateurConnecte connecte, HttpServletRequest requete) throws ServletException {
		var utilisateur = utilisateurs.findById(connecte.id());
		if (utilisateur.isEmpty()) {
			// Compte supprimé pendant que la session vivait : on ferme la session.
			requete.logout();
			throw new InsufficientAuthenticationException("Compte introuvable");
		}
		return Moi.depuis(utilisateur.get());
	}

	/**
	 * GET /api/auth/session (contrat : opération « session »). Toujours 200 : un visiteur sans compte n'est pas une
	 * erreur, et la console de son navigateur reste propre. Un compte supprimé pendant que la session vivait est
	 * déconnecté, comme pour GET /api/me.
	 */
	@GetMapping("/api/auth/session")
	Session session(@AuthenticationPrincipal UtilisateurConnecte connecte, HttpServletRequest requete) throws ServletException {
		if (connecte == null) {
			return Session.ANONYME;
		}
		var utilisateur = utilisateurs.findById(connecte.id());
		if (utilisateur.isEmpty()) {
			requete.logout();
			return Session.ANONYME;
		}
		return new Session(true, Moi.depuis(utilisateur.get()));
	}

	record Session(boolean connecte, Moi moi) {

		static final Session ANONYME = new Session(false, null);

	}

	/** Schéma Moi du contrat. L'e-mail n'apparaît que dans cette réponse, jamais dans le profil vu par les autres. */
	record Moi(UUID id, String nom, String email, String avatarUrl, String loginGithub, List<String> methodesConnexion,
			Set<Role> roles, Instant creeLe) {

		static Moi depuis(Utilisateur u) {
			List<String> methodes = new ArrayList<>();
			if (u.githubId() != null) {
				methodes.add("github");
			}
			if (u.email() != null) {
				methodes.add("email");
			}
			return new Moi(u.id(), u.nom(), u.email(), u.avatarUrl(), u.loginGithub(), methodes, u.roles(), u.creeLe());
		}

	}

}
