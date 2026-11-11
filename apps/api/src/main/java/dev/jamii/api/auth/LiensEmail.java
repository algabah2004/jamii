package dev.jamii.api.auth;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/** La connexion par lien e-mail (contrat : /api/auth/lien-email). Limites de docs/api-conventions.md §6. */
@Service
class LiensEmail {

	static final int MAX_PAR_EMAIL = 5;

	static final int MAX_PAR_IP = 20;

	static final Duration FENETRE = Duration.ofHours(1);

	private static final Pattern ADRESSE = Pattern.compile("^[^\\s@]{1,64}@[^\\s@.]+(\\.[^\\s@.]+)+$");

	private static final Logger journal = LoggerFactory.getLogger(LiensEmail.class);

	/** Trop de demandes : à reprendre dans `attenteSecondes` (en-tête Retry-After). */
	static final class TropDeDemandes extends RuntimeException {

		final long attenteSecondes;

		TropDeDemandes(long attenteSecondes) {
			super(null, null, false, false);
			this.attenteSecondes = attenteSecondes;
		}

	}

	private final JetonsConnexion jetons;

	private final ComptesEmail comptes;

	private final LimiteurDemandes limiteur;

	private final EnvoiEmail envoi;

	private final String webUrl;

	LiensEmail(JetonsConnexion jetons, ComptesEmail comptes, LimiteurDemandes limiteur, EnvoiEmail envoi,
			@Value("${jamii.web-url}") String webUrl) {
		this.jetons = jetons;
		this.comptes = comptes;
		this.limiteur = limiteur;
		this.envoi = envoi;
		this.webUrl = webUrl;
	}

	/** Minuscules et sans espaces autour. Renvoie vide si ce n'est pas une adresse plausible. */
	static Optional<String> normaliser(String brut) {
		if (brut == null) {
			return Optional.empty();
		}
		String email = brut.strip().toLowerCase(Locale.ROOT);
		return email.length() <= 254 && ADRESSE.matcher(email).matches() ? Optional.of(email) : Optional.empty();
	}

	/**
	 * Envoie un lien à cette adresse (déjà normalisée), qu'elle soit inscrite ou non : on ne le sait même pas,
	 * donc la réponse ne peut pas le révéler. Une panne d'envoi n'est pas non plus visible de l'extérieur.
	 */
	void demander(String email, String retour, String ip, Instant maintenant) {
		long attente = limiteur.essayer("ip:" + ip, MAX_PAR_IP, FENETRE, maintenant);
		if (attente > 0) {
			throw new TropDeDemandes(attente);
		}
		if (jetons.demandesRecentes(email, maintenant.minus(FENETRE)) >= MAX_PAR_EMAIL) {
			throw new TropDeDemandes(FENETRE.toSeconds());
		}
		String jeton = jetons.creer(email, maintenant);
		String lien = webUrl + "/connexion/email?jeton=" + jeton
				+ (CheminRetour.PAR_DEFAUT.equals(retour) ? "" : "&retour=" + URLEncoder.encode(retour, StandardCharsets.UTF_8));
		try {
			envoi.envoyer(email, "Ton lien de connexion à Jamii", """
					Bonjour,

					Voici ton lien de connexion à Jamii. Il est valable 15 minutes et ne sert qu'une fois :

					%s

					Si tu n'as rien demandé, ignore ce message : personne ne peut se connecter sans ce lien.
					""".formatted(lien));
		}
		catch (RuntimeException e) {
			// Ni l'adresse ni le lien dans les journaux.
			journal.error("Envoi du lien de connexion impossible : {}", e.getClass().getSimpleName());
		}
	}

	/**
	 * Échange le jeton contre un compte (créé à la première connexion). Vide si le jeton est inconnu, déjà utilisé
	 * ou expiré. Deux premières connexions simultanées de la même adresse : la seconde réessaie une fois.
	 */
	Optional<UtilisateurConnecte> confirmer(String jeton, Instant maintenant) {
		try {
			return comptes.confirmer(jeton, maintenant);
		}
		catch (DataIntegrityViolationException conflit) {
			return comptes.confirmer(jeton, maintenant);
		}
	}

}
