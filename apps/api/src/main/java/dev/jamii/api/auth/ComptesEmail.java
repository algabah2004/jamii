package dev.jamii.api.auth;

import java.time.Instant;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consomme le jeton et trouve ou crée le compte, dans UNE transaction : si la création échoue, le jeton n'est pas
 * perdu. Reconnu par l'adresse, jamais fusionné avec un compte GitHub (dont l'e-mail n'est pas stocké) : prouver
 * une adresse ne donne donc jamais accès à un compte qui existait avant.
 */
@Service
class ComptesEmail {

	private final JetonsConnexion jetons;

	private final UtilisateurRepository utilisateurs;

	ComptesEmail(JetonsConnexion jetons, UtilisateurRepository utilisateurs) {
		this.jetons = jetons;
		this.utilisateurs = utilisateurs;
	}

	@Transactional
	Optional<UtilisateurConnecte> confirmer(String jeton, Instant maintenant) {
		return jetons.consommer(jeton, maintenant).map(email -> {
			Utilisateur utilisateur = utilisateurs.findByEmailIgnoreCase(email)
				.orElseGet(() -> utilisateurs.saveAndFlush(Utilisateur.depuisEmail(email, maintenant)));
			return new UtilisateurConnecte(utilisateur.id(), utilisateur.roles());
		});
	}

}
