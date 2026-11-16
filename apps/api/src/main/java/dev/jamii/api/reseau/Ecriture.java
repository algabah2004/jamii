package dev.jamii.api.reseau;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * « … est en train d'écrire » dans une discussion privée (#157). Une information éphémère : gardée en mémoire quelques
 * secondes, jamais en base. Le front la signale au plus toutes les 3 secondes pendant la frappe, et l'autre personne
 * la lit toutes les 2 secondes tant que la conversation est ouverte.
 *
 * <p>Gardé en mémoire de l'API : avec une seule API (le serveur Systalink), c'est juste. Avec plusieurs, il faudrait un
 * stockage partagé (Postgres ou un cache), sous peine de voir l'indicateur clignoter.
 */
@Component
class Ecriture {

	/** Sans nouveau signal pendant ce délai, la personne n'écrit plus. */
	static final Duration DUREE = Duration.ofSeconds(6);

	/** Au-delà, on fait le ménage des signaux expirés avant d'en ajouter. */
	static final int TAILLE_MAX = 10_000;

	private record Paire(UUID de, UUID a) {
	}

	private final Map<Paire, Instant> signaux = new ConcurrentHashMap<>();

	private final Clock horloge;

	@Autowired
	Ecriture() {
		this(Clock.systemUTC());
	}

	Ecriture(Clock horloge) {
		this.horloge = horloge;
	}

	/** J'écris à cette personne. */
	void signaler(UUID moi, UUID autre) {
		Instant maintenant = horloge.instant();
		if (signaux.size() >= TAILLE_MAX) {
			signaux.values().removeIf(t -> t.plus(DUREE).isBefore(maintenant));
		}
		signaux.put(new Paire(moi, autre), maintenant);
	}

	/** Mon message est parti : je n'écris plus. */
	void arreter(UUID moi, UUID autre) {
		signaux.remove(new Paire(moi, autre));
	}

	/** Cette personne est-elle en train de m'écrire ? */
	boolean ecrit(UUID autre, UUID moi) {
		Instant dernier = signaux.get(new Paire(autre, moi));
		return dernier != null && dernier.plus(DUREE).isAfter(horloge.instant());
	}

	int taille() {
		return signaux.size();
	}

}
