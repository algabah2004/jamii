package dev.jamii.api.cercles;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/**
 * L'activité du dépôt d'un tour (contracts/cercles.yaml, `activiteDuTour`), gardée 1 minute par dépôt : toute une salle
 * qui regarde le même tour pendant la démo ne fait qu'un appel au fournisseur par minute, et le quota de GitHub tient.
 */
@Service
class ActiviteDesTours {

	static final Duration DUREE_CACHE = Duration.ofMinutes(1);

	/** Au-delà, on vide le cache plutôt que de le laisser grossir (un dépôt par tour, 6 tours par cercle). */
	private static final int TAILLE_MAX = 500;

	enum Etat {
		DISPONIBLE, INDISPONIBLE, AUCUN_DEPOT
	}

	record Lecture(Etat etat, DepotCode.DepotDuTour depot, Instant luLe, List<DepotCode.Commit> commits,
			List<DepotCode.DemandeDeFusion> demandesDeFusion) {
	}

	private final ObjectProvider<DepotCode> fournisseur;

	private final Clock horloge;

	private final Map<String, Lecture> cache = new ConcurrentHashMap<>();

	ActiviteDesTours(ObjectProvider<DepotCode> fournisseur, ObjectProvider<Clock> horloge) {
		this.fournisseur = fournisseur;
		this.horloge = horloge.getIfAvailable(Clock::systemUTC);
	}

	/** Les dépôts sont-ils privés ? Sans fournisseur, il n'y a pas de dépôt à protéger. */
	boolean prives() {
		var f = fournisseur.getIfAvailable();
		return f != null && f.prives();
	}

	Lecture lire(DepotCode.DepotDuTour depot) {
		Instant maintenant = horloge.instant();
		if (depot == null) {
			return new Lecture(Etat.AUCUN_DEPOT, null, maintenant, List.of(), List.of());
		}
		var frais = Optional.ofNullable(cache.get(depot.url()))
			.filter(l -> l.luLe().plus(DUREE_CACHE).isAfter(maintenant));
		if (frais.isPresent()) {
			return frais.get();
		}
		var f = fournisseur.getIfAvailable();
		var activite = f == null ? Optional.<DepotCode.Activite>empty() : f.activite(depot);
		Lecture lecture = activite
			.map(a -> new Lecture(Etat.DISPONIBLE, depot, maintenant, a.commits(), a.demandesDeFusion()))
			.orElseGet(() -> new Lecture(Etat.INDISPONIBLE, depot, maintenant, List.of(), List.of()));
		if (cache.size() >= TAILLE_MAX) {
			cache.clear();
		}
		// Une panne aussi est gardée 1 minute : un fournisseur en panne n'est pas rappelé à chaque affichage.
		cache.put(depot.url(), lecture);
		return lecture;
	}

}
