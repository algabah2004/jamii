package dev.jamii.api.auth;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Routes /api/auth/lien-email et /api/auth/lien-email/confirmation (contracts/socle.yaml). */
@RestController
class LienEmailController {

	private final LiensEmail liens;

	private final HttpSessionSecurityContextRepository contextes = new HttpSessionSecurityContextRepository();

	LienEmailController(LiensEmail liens) {
		this.liens = liens;
	}

	record DemandeLienEmail(String email, String retour) {
	}

	record ConfirmationLienEmail(String jeton) {
	}

	@PostMapping("/api/auth/lien-email")
	ResponseEntity<?> demander(@RequestBody DemandeLienEmail demande, HttpServletRequest requete) {
		var email = LiensEmail.normaliser(demande.email());
		if (email.isEmpty()) {
			return invalide("email");
		}
		// Un chemin de retour douteux est remplacé par « / », comme pour la connexion GitHub.
		String retour = CheminRetour.valider(demande.retour());
		try {
			liens.demander(email.get(), retour, requete.getRemoteAddr(), Instant.now());
		}
		catch (LiensEmail.TropDeDemandes trop) {
			return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
				.header(HttpHeaders.RETRY_AFTER, String.valueOf(trop.attenteSecondes))
				.body(probleme(HttpStatus.TOO_MANY_REQUESTS, "trop_de_requetes", "Trop de demandes, réessaie plus tard", null));
		}
		return ResponseEntity.accepted().build();
	}

	@PostMapping("/api/auth/lien-email/confirmation")
	ResponseEntity<?> confirmer(@RequestBody ConfirmationLienEmail confirmation, HttpServletRequest requete,
			HttpServletResponse reponse) {
		String jeton = confirmation.jeton();
		if (jeton == null || jeton.length() < 32 || jeton.length() > 128) {
			return invalide("jeton");
		}
		var connecte = liens.confirmer(jeton, Instant.now());
		if (connecte.isEmpty()) {
			// Inconnu, déjà utilisé ou expiré : même réponse, on ne précise pas lequel.
			return ResponseEntity.badRequest()
				.body(probleme(HttpStatus.BAD_REQUEST, "lien_invalide", "Ce lien n'est plus valable", null));
		}
		ouvrirSession(connecte.get(), requete, reponse);
		return ResponseEntity.noContent().build();
	}

	/** Session neuve : on change l'identifiant de session s'il y en avait déjà une (contre la fixation de session). */
	private void ouvrirSession(UtilisateurConnecte connecte, HttpServletRequest requete, HttpServletResponse reponse) {
		HttpSession existante = requete.getSession(false);
		if (existante != null) {
			requete.changeSessionId();
		}
		var authentification = UsernamePasswordAuthenticationToken.authenticated(connecte, null, connecte.getAuthorities());
		SecurityContext contexte = SecurityContextHolder.createEmptyContext();
		contexte.setAuthentication(authentification);
		SecurityContextHolder.setContext(contexte);
		contextes.saveContext(contexte, requete, reponse);
	}

	private static ResponseEntity<ProblemDetail> invalide(String champ) {
		return ResponseEntity.badRequest()
			.body(probleme(HttpStatus.BAD_REQUEST, "validation", "Certains champs sont invalides",
					List.of(Map.of("champ", champ, "code", "format_invalide"))));
	}

	private static ProblemDetail probleme(HttpStatus statut, String code, String titre, List<Map<String, String>> erreurs) {
		ProblemDetail erreur = ProblemDetail.forStatus(statut);
		erreur.setType(URI.create("urn:jamii:erreur:" + code));
		erreur.setTitle(titre);
		erreur.setProperty("code", code);
		if (erreurs != null) {
			erreur.setProperty("erreurs", erreurs);
		}
		return erreur;
	}

}
