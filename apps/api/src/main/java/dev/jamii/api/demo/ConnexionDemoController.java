package dev.jamii.api.demo;

import java.net.URI;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.jamii.api.auth.Role;
import dev.jamii.api.auth.UtilisateurConnecte;

/**
 * POST /api/auth/demo (contracts/socle.yaml) : ouvre une session sur le compte démo ({@link CompteDemo}), sans mot de
 * passe, pour tout visiteur. N'existe qu'avec le profil `demo`, et seulement si `jamii.demo.connexion` est vrai.
 */
@RestController
@Profile("demo")
class ConnexionDemoController {

	private final HttpSessionSecurityContextRepository contextes = new HttpSessionSecurityContextRepository();

	private final JdbcClient base;

	private final boolean ouverte;

	ConnexionDemoController(JdbcClient base, @Value("${jamii.demo.connexion:false}") boolean ouverte) {
		this.base = base;
		this.ouverte = ouverte;
	}

	@PostMapping("/api/auth/demo")
	ResponseEntity<?> entrer(HttpServletRequest requete, HttpServletResponse reponse) {
		boolean existe = base.sql("SELECT EXISTS (SELECT 1 FROM utilisateur WHERE id = :id)").param("id", CompteDemo.ID)
			.query(Boolean.class).single();
		if (!ouverte || !existe) {
			ProblemDetail erreur = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
			erreur.setType(URI.create("urn:jamii:erreur:introuvable"));
			erreur.setTitle("Pas de compte démo sur ce serveur");
			return ResponseEntity.status(HttpStatus.NOT_FOUND).body(erreur);
		}
		// Session neuve : on change l'identifiant de session s'il y en avait déjà une (contre la fixation de session).
		if (requete.getSession(false) != null) {
			requete.changeSessionId();
		}
		var connecte = new UtilisateurConnecte(CompteDemo.ID, Set.of(Role.MEMBRE));
		SecurityContext contexte = SecurityContextHolder.createEmptyContext();
		contexte.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(connecte, null, connecte.getAuthorities()));
		SecurityContextHolder.setContext(contexte);
		contextes.saveContext(contexte, requete, reponse);
		return ResponseEntity.noContent().build();
	}

}
