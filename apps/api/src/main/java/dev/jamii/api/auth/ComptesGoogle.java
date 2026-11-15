package dev.jamii.api.auth;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Se connecter avec Google (proposition 0005, #260). Google prouve l'adresse e-mail, comme un lien de connexion : le
 * compte est donc reconnu par cette adresse, et une personne déjà venue par un lien e-mail retrouve son compte.
 * Jamais fusionné avec un compte GitHub, dont l'e-mail n'est pas stocké : prouver une adresse ne donne pas accès à un
 * compte qui n'avait pas cette adresse. Une adresse que Google n'a pas vérifiée est refusée.
 */
@Service
class ComptesGoogle {

	private final UtilisateurRepository utilisateurs;

	ComptesGoogle(UtilisateurRepository utilisateurs) {
		this.utilisateurs = utilisateurs;
	}

	@Transactional
	UtilisateurConnecte enregistrer(String email, String nom, String avatarUrl) {
		Instant maintenant = Instant.now();
		String adresse = email.strip().toLowerCase(java.util.Locale.ROOT);
		Utilisateur utilisateur = utilisateurs.findByEmailIgnoreCase(adresse).orElseGet(() -> {
			Utilisateur nouveau = Utilisateur.depuisEmail(adresse, maintenant);
			nouveau.completerDepuisGoogle(nom, avatarUrl, maintenant);
			return utilisateurs.saveAndFlush(nouveau);
		});
		return new UtilisateurConnecte(utilisateur.id(), utilisateur.roles());
	}

}
