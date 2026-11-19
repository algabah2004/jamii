package dev.jamii.api.demo;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.jamii.api.auth.Annuaire;
import dev.jamii.api.auth.Role;
import dev.jamii.api.auth.UtilisateurConnecte;

/**
 * POST /api/auth/demo (contracts/socle.yaml) : ouvre une session sur un compte démo à soi ({@link VisiteursDemo}), sans
 * mot de passe. Un visiteur déjà dans son compte démo y reste. N'existe qu'avec le profil `demo`, et seulement si
 * `jamii.demo.connexion` est vrai.
 */
@RestController
@Profile("demo")
class ConnexionDemoController {

	/**
	 * Par adresse IP : de quoi ouvrir la démo sur plusieurs appareils, pas de quoi remplir la base. Valeur par défaut de
	 * {@code jamii.demo.par-adresse} ; seuls les tests de parcours (e2e/compose.yml), qui viennent tous d'une même
	 * machine, la relèvent.
	 */
	static final int PAR_IP = 5;

	static final Duration FENETRE = Duration.ofHours(1);

	private record Fenetre(Instant debut, int compte) {
	}

	private final Map<String, Fenetre> fenetres = new ConcurrentHashMap<>();

	private final HttpSessionSecurityContextRepository contextes = new HttpSessionSecurityContextRepository();

	private final JdbcClient base;

	private final VisiteursDemo visiteurs;

	private final boolean ouverte;

	private final int parAdresse;

	ConnexionDemoController(JdbcClient base, VisiteursDemo visiteurs, @Value("${jamii.demo.connexion:false}") boolean ouverte,
			@Value("${jamii.demo.par-adresse:" + PAR_IP + "}") int parAdresse) {
		this.base = base;
		this.visiteurs = visiteurs;
		this.ouverte = ouverte;
		this.parAdresse = parAdresse;
	}

	@PostMapping("/api/auth/demo")
	ResponseEntity<?> entrer(@AuthenticationPrincipal UtilisateurConnecte deja, HttpServletRequest requete, HttpServletResponse reponse) {
		if (!ouverte) {
			return erreur(HttpStatus.NOT_FOUND, "introuvable", "Pas de compte démo sur ce serveur");
		}
		if (deja != null && estVisiteur(deja.id())) {
			return ResponseEntity.noContent().build();
		}
		Instant maintenant = Instant.now();
		long attente = essayer(requete.getRemoteAddr(), maintenant);
		if (attente > 0) {
			return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, String.valueOf(attente))
				.body(probleme(HttpStatus.TOO_MANY_REQUESTS, "trop_de_demandes", "Trop de comptes démo ouverts depuis cette adresse"));
		}
		Optional<UUID> visiteur = visiteurs.creer(maintenant);
		if (visiteur.isEmpty()) {
			return erreur(HttpStatus.SERVICE_UNAVAILABLE, "demo_complete", "Trop de visiteurs en même temps : réessaie dans un moment");
		}
		// Session neuve : on change l'identifiant de session s'il y en avait déjà une (contre la fixation de session).
		if (requete.getSession(false) != null) {
			requete.changeSessionId();
		}
		var connecte = new UtilisateurConnecte(visiteur.get(), Set.of(Role.MEMBRE));
		SecurityContext contexte = SecurityContextHolder.createEmptyContext();
		contexte.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(connecte, null, connecte.getAuthorities()));
		SecurityContextHolder.setContext(contexte);
		contextes.saveContext(contexte, requete, reponse);
		return ResponseEntity.noContent().build();
	}

	private boolean estVisiteur(UUID id) {
		return base.sql("SELECT EXISTS (SELECT 1 FROM utilisateur WHERE id = :id AND email LIKE :v)").param("id", id)
			.param("v", Annuaire.VISITEURS_DEMO).query(Boolean.class).single();
	}

	/** Fenêtre fixe, en mémoire (une seule instance de l'API). 0 si permis, sinon les secondes à attendre. */
	private long essayer(String ip, Instant maintenant) {
		if (fenetres.size() > 10_000) {
			fenetres.values().removeIf(f -> !f.debut().plus(FENETRE).isAfter(maintenant));
		}
		long[] attente = { 0 };
		fenetres.compute(ip, (k, f) -> {
			if (f == null || !f.debut().plus(FENETRE).isAfter(maintenant)) {
				return new Fenetre(maintenant, 1);
			}
			if (f.compte() >= parAdresse) {
				attente[0] = Math.max(1, Duration.between(maintenant, f.debut().plus(FENETRE)).toSeconds());
				return f;
			}
			return new Fenetre(f.debut(), f.compte() + 1);
		});
		return attente[0];
	}

	private static ProblemDetail probleme(HttpStatus statut, String code, String titre) {
		ProblemDetail erreur = ProblemDetail.forStatus(statut);
		erreur.setType(URI.create("urn:jamii:erreur:" + code));
		erreur.setTitle(titre);
		return erreur;
	}

	private static ResponseEntity<ProblemDetail> erreur(HttpStatus statut, String code, String titre) {
		return ResponseEntity.status(statut).body(probleme(statut, code, titre));
	}

}
