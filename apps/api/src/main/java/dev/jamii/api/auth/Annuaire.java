package dev.jamii.api.auth;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
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

	/**
	 * Les comptes dont le nom ou le login GitHub contient `texte` (majuscules ignorées), `max` au plus. Pour la recherche
	 * de profils : le module profils filtre ensuite sur les profils publiés. Ne renvoie que des identifiants.
	 */
	@Transactional(readOnly = true)
	public List<UUID> chercherParNom(String texte, int max) {
		String echappe = texte.strip().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
		return utilisateurs.idsDontLeNomContient("%" + echappe + "%", PageRequest.of(0, max));
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

	/** Le domaine réservé des comptes de la démo : personne ne le reçoit, et aucun vrai compte ne l'utilise. */
	public static final String DOMAINE_DEMO = "@demo.jamii.test";

	/**
	 * Motif SQL (LIKE) des comptes jetables qu'ouvre « Entrer en démo » : un par visiteur, effacé après 24 heures. Leur
	 * parcours mêle les personnes de la démo ; les autres modules s'en servent pour ne pas le compter chez elles.
	 */
	public static final String VISITEURS_DEMO = "visiteur-%" + DOMAINE_DEMO;

	/**
	 * Vrai pour un compte de la démo : ses personnes fictives et les comptes jetables des visiteurs ({@link #VISITEURS_DEMO}).
	 * Ces derniers n'écrivent qu'aux comptes de la démo : un visiteur anonyme n'importune personne.
	 */
	/** La personne de la démo qu'on propose au visiteur pour essayer la messagerie, quand il vise un vrai compte (#157). */
	public static final String CONTACT_DEMO = "kofi" + DOMAINE_DEMO;

	/** Le compte de {@link #CONTACT_DEMO}, absent hors démo. */
	@Transactional(readOnly = true)
	public Optional<Compte> contactDeDemo() {
		return utilisateurs.findByEmailIgnoreCase(CONTACT_DEMO).map(Annuaire::enCompte);
	}

	@Transactional(readOnly = true)
	public boolean estDeDemonstration(UUID id) {
		return utilisateurs.findById(id).map(Utilisateur::email).filter(e -> e != null && e.endsWith(DOMAINE_DEMO)).isPresent();
	}

	private static Compte enCompte(Utilisateur u) {
		return new Compte(u.id(), u.nom(), u.avatarUrl(), u.loginGithub());
	}

}
