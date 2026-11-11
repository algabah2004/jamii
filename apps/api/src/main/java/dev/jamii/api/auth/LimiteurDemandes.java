package dev.jamii.api.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * Limite par clé (ici l'adresse IP) en fenêtre fixe, en mémoire. Suffisant pour une seule instance de l'API.
 * Avec plusieurs instances, chacune compte pour elle : la limite par e-mail, elle, vit en base (JetonsConnexion).
 * Le nombre de clés suivies est borné, pour qu'un attaquant ne puisse pas remplir la mémoire.
 */
@Component
class LimiteurDemandes {

	static final int CLES_MAX = 10_000;

	private record Fenetre(Instant debut, int compte) {
	}

	private final Map<String, Fenetre> fenetres = new ConcurrentHashMap<>();

	int taille() {
		return fenetres.size();
	}

	/** Renvoie 0 si la demande est permise (et la compte), sinon le nombre de secondes à attendre. */
	long essayer(String cle, int max, Duration duree, Instant maintenant) {
		if (fenetres.size() >= CLES_MAX) {
			fenetres.values().removeIf(f -> !f.debut().plus(duree).isAfter(maintenant));
		}
		long[] attente = { 0 };
		fenetres.compute(cle, (k, f) -> {
			if (f == null || !f.debut().plus(duree).isAfter(maintenant)) {
				return new Fenetre(maintenant, 1);
			}
			if (f.compte() >= max) {
				attente[0] = Math.max(1, Duration.between(maintenant, f.debut().plus(duree)).toSeconds());
				return f;
			}
			return new Fenetre(f.debut(), f.compte() + 1);
		});
		return attente[0];
	}

}
