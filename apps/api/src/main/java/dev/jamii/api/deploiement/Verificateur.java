package dev.jamii.api.deploiement;

import java.net.URI;
import java.util.Locale;

/** Appelle une adresse déclarée et dit si elle répond. Remplaçable dans les tests (aucun appel réseau). */
interface Verificateur {

	Resultat verifier(URI adresse);

	/** Le résultat d'un essai (champ `dernierEssai.resultat` du contrat). */
	enum Resultat {

		REPOND, ERREUR_SERVEUR, DELAI_DEPASSE, CONNEXION_REFUSEE, CERTIFICAT_INVALIDE, ADRESSE_REFUSEE, NOM_INCONNU;

		String code() {
			return name().toLowerCase(Locale.ROOT);
		}

		static Resultat depuisCode(String code) {
			return valueOf(code.toUpperCase(Locale.ROOT));
		}

	}

}
