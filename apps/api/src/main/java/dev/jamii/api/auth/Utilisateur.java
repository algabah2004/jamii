package dev.jamii.api.auth;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;

/** Un compte Jamii (table utilisateur, migration V3). */
@Entity
class Utilisateur {

	static final int NOM_MAX = 100;

	@Id
	private UUID id;

	private String nom;

	/** Adresse vérifiée uniquement. Jamais l'e-mail public de GitHub, que chacun peut choisir librement. */
	private String email;

	private String avatarUrl;

	private Long githubId;

	private String loginGithub;

	@ElementCollection(fetch = FetchType.EAGER)
	@CollectionTable(name = "utilisateur_role", joinColumns = @JoinColumn(name = "utilisateur_id"))
	@Column(name = "role")
	private Set<Role> roles = EnumSet.noneOf(Role.class);

	private Instant creeLe;

	@Column(name = "mis_a_jour_le")
	private Instant misAJourLe;

	protected Utilisateur() {
	}

	static Utilisateur depuisGithub(long githubId, Instant maintenant) {
		Utilisateur u = new Utilisateur();
		u.id = UUID.randomUUID();
		u.githubId = githubId;
		u.roles.add(Role.MEMBRE);
		u.creeLe = maintenant;
		return u;
	}

	/**
	 * Compte créé par un lien de connexion. Le nom est neutre, volontairement : le début de l'adresse
	 * (awa.diallo@…) serait montré aux autres membres. La personne le choisit ensuite dans son profil.
	 */
	static Utilisateur depuisEmail(String email, Instant maintenant) {
		Utilisateur u = new Utilisateur();
		u.id = UUID.randomUUID();
		u.email = email;
		u.nom = "Nouveau membre";
		u.roles.add(Role.MEMBRE);
		u.creeLe = maintenant;
		u.misAJourLe = maintenant;
		return u;
	}

	/** Recopie ce que GitHub dit du compte à chaque connexion : le login, le nom et l'avatar peuvent changer. */
	void majDepuisGithub(String login, String nomAffiche, String avatarUrl, Instant maintenant) {
		this.loginGithub = login;
		String nomPropre = nomAffiche == null || nomAffiche.isBlank() ? login : nomAffiche.strip();
		this.nom = nomPropre.length() > NOM_MAX ? nomPropre.substring(0, NOM_MAX) : nomPropre;
		this.avatarUrl = avatarUrl;
		this.misAJourLe = maintenant;
	}

	UUID id() {
		return id;
	}

	String nom() {
		return nom;
	}

	String email() {
		return email;
	}

	String avatarUrl() {
		return avatarUrl;
	}

	Long githubId() {
		return githubId;
	}

	String loginGithub() {
		return loginGithub;
	}

	Set<Role> roles() {
		return Set.copyOf(roles);
	}

	Instant creeLe() {
		return creeLe;
	}

}
