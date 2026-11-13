package dev.jamii.api.auth;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * La seule porte d'entrée des autres modules vers les comptes (docs/modele-donnees.md §6) :
 * ils ne lisent jamais la table utilisateur. Un compte supprimé n'est simplement plus trouvé.
 */
@Service
public class Annuaire {

	private final UtilisateurRepository utilisateurs;

	Annuaire(UtilisateurRepository utilisateurs) {
		this.utilisateurs = utilisateurs;
	}

	@Transactional(readOnly = true)
	public Optional<Compte> lire(UUID id) {
		return utilisateurs.findById(id).map(Annuaire::enCompte);
	}

	/** Une seule requête pour une liste. Les identifiants inconnus ou supprimés sont absents du résultat. */
	@Transactional(readOnly = true)
	public Map<UUID, Compte> lire(Collection<UUID> ids) {
		Map<UUID, Compte> comptes = new HashMap<>();
		if (ids.isEmpty()) {
			return comptes;
		}
		utilisateurs.findAllById(ids).forEach(u -> comptes.put(u.id(), enCompte(u)));
		return comptes;
	}

	/** Comptes créés à partir de `depuis` (tous si `depuis` est null), pour le tableau de bord Systalink. */
	@Transactional(readOnly = true)
	public long compterInscrits(Instant depuis) {
		return depuis == null ? utilisateurs.count() : utilisateurs.countByCreeLeGreaterThanEqual(depuis);
	}

	/**
	 * L'e-mail d'un compte, **seulement** pour le joindre avec son accord explicite (les clients suivis de Systalink,
	 * contracts/systalink.yaml) ou pour un contrôle d'accès. Vide pour un compte GitHub sans e-mail connu. Ne jamais
	 * l'afficher à un autre utilisateur.
	 */
	@Transactional(readOnly = true)
	public Optional<String> emailDeContact(UUID id) {
		return utilisateurs.findById(id).map(Utilisateur::email).filter(e -> !e.isBlank());
	}

	private static Compte enCompte(Utilisateur u) {
		return new Compte(u.id(), u.nom(), u.avatarUrl(), u.loginGithub());
	}

}
