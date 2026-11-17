package dev.jamii.api.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Les façons de se connecter que ce serveur propose (contracts/socle.yaml) : le site ne montre « Continuer avec
 * Google » que si l'application Google est configurée. Rien de secret ici : seulement oui ou non.
 */
@RestController
class FournisseursController {

	record Fournisseurs(boolean github, boolean google, boolean email, boolean demo) {
	}

	private final Fournisseurs fournisseurs;

	FournisseursController(@Value("${spring.security.oauth2.client.registration.github.client-id:}") String github,
			@Value("${spring.security.oauth2.client.registration.google.client-id:}") String google,
			@Value("${jamii.demo.connexion:false}") boolean demo, Environment environnement) {
		// Le compte démo (#157) n'existe qu'avec le profil demo : le bouton n'apparaît que là.
		this.fournisseurs = new Fournisseurs(configure(github), configure(google), true, demo && environnement.matchesProfiles("demo"));
	}

	/** Les valeurs par défaut de application.yaml (`a-configurer`) veulent dire « pas configuré ». */
	static boolean configure(String clientId) {
		return clientId != null && !clientId.isBlank() && !SecuriteConfig.NON_CONFIGURE.equals(clientId);
	}

	@GetMapping("/api/auth/fournisseurs")
	ResponseEntity<Fournisseurs> lister() {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(fournisseurs);
	}

}
