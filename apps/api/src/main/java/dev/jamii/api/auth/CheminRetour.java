package dev.jamii.api.auth;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Le chemin du front où revenir après la connexion (paramètre « retour »). Seul un chemin relatif du front
 * est accepté : sinon, un lien piégé « jamii…/api/auth/github?retour=https://faux-site » enverrait
 * l'utilisateur, fraîchement connecté, sur un site tiers (redirection ouverte).
 */
final class CheminRetour {

	static final String PAR_DEFAUT = "/";

	private static final Pattern AUTORISE = Pattern.compile("^/(?![/\\\\])[^\\s\\\\]*$");

	private static final int LONGUEUR_MAX = 512;

	private CheminRetour() {
	}

	/** Le chemin s'il est sûr, sinon {@link #PAR_DEFAUT}. On vérifie aussi la forme décodée (%2F%2F donne //). */
	static String valider(String retour) {
		if (retour == null || retour.length() > LONGUEUR_MAX || !AUTORISE.matcher(retour).matches()) {
			return PAR_DEFAUT;
		}
		try {
			String decode = URLDecoder.decode(retour, StandardCharsets.UTF_8);
			if (!AUTORISE.matcher(decode).matches() || URI.create(retour).getHost() != null) {
				return PAR_DEFAUT;
			}
		}
		catch (IllegalArgumentException e) {
			return PAR_DEFAUT;
		}
		return retour;
	}

}
