package dev.jamii.api.systalink;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import dev.jamii.api.auth.Annuaire;
import dev.jamii.api.auth.Compte;
import dev.jamii.api.auth.Role;
import dev.jamii.api.auth.UtilisateurConnecte;

/**
 * Le rôle `systalink` (contracts/systalink.yaml) : un admin, ou un login GitHub ou un e-mail listé dans
 * `SYSTALINK_ACCES`. Tout l'espace Systalink lui est réservé : tableau de bord, tendances et clients suivis.
 * Liste vide : seuls les admins entrent.
 */
@Component
public class AccesSystalink {

	private final Annuaire annuaire;

	private final Set<String> acces;

	AccesSystalink(Annuaire annuaire, @Value("${jamii.clients-systalink.acces:}") String acces) {
		this.annuaire = annuaire;
		this.acces = Arrays.stream(acces.split(","))
			.map(a -> a.strip().toLowerCase(Locale.ROOT))
			.filter(a -> !a.isEmpty())
			.collect(Collectors.toUnmodifiableSet());
	}

	public boolean aAcces(UtilisateurConnecte moi) {
		if (moi == null) {
			return false;
		}
		if (moi.roles().contains(Role.ADMIN)) {
			return true;
		}
		if (acces.isEmpty()) {
			return false;
		}
		Optional<String> login = annuaire.lire(moi.id()).map(Compte::loginGithub);
		Optional<String> email = annuaire.emailDeContact(moi.id());
		return login.map(l -> acces.contains(l.toLowerCase(Locale.ROOT))).orElse(false)
				|| email.map(e -> acces.contains(e.toLowerCase(Locale.ROOT))).orElse(false);
	}

}
