package dev.jamii.api.systalink;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import dev.jamii.api.auth.Annuaire;

/**
 * Calcule le tableau de bord et le garde 5 minutes par période : si toute la salle ouvre la page pendant la démo,
 * la base ne reçoit qu'une série de requêtes toutes les 5 minutes.
 */
@Service
class CalculTableauDeBord {

	static final Duration DUREE_CACHE = Duration.ofMinutes(5);

	private final JdbcClient base;

	private final Annuaire annuaire;

	private final ObjectProvider<ChiffresDesModules.PaysDesInscrits> pays;

	private final ObjectProvider<ChiffresDesModules.Projets> projets;

	private final ObjectProvider<ChiffresDesModules.Deploiements> deploiements;

	private final Clock horloge;

	private final Map<Periode, TableauDeBord> cache = new ConcurrentHashMap<>();

	CalculTableauDeBord(JdbcClient base, Annuaire annuaire, ObjectProvider<ChiffresDesModules.PaysDesInscrits> pays,
			ObjectProvider<ChiffresDesModules.Projets> projets,
			ObjectProvider<ChiffresDesModules.Deploiements> deploiements, ObjectProvider<Clock> horloge) {
		this.base = base;
		this.annuaire = annuaire;
		this.pays = pays;
		this.projets = projets;
		this.deploiements = deploiements;
		this.horloge = horloge.getIfAvailable(Clock::systemUTC);
	}

	TableauDeBord pour(Periode periode) {
		Instant maintenant = horloge.instant();
		return cache.compute(periode, (cle, ancien) -> encoreFrais(ancien, maintenant) ? ancien
				: calculer(periode, maintenant));
	}

	void viderCache() {
		cache.clear();
	}

	private static boolean encoreFrais(TableauDeBord ancien, Instant maintenant) {
		return ancien != null && ancien.calculeLe().plus(DUREE_CACHE).isAfter(maintenant);
	}

	/** Un module qui n'a pas encore branché son compteur compte pour 0 : jamais de chiffre inventé. */
	private TableauDeBord calculer(Periode periode, Instant maintenant) {
		Instant debut = periode.debut(maintenant);
		var lesPays = pays.getIfAvailable();
		var p = projets.getIfAvailable();
		var d = deploiements.getIfAvailable();
		// Tant que les profils (#19) ne donnent pas les pays, tous les inscrits sont comptés dans « autres ».
		var lesInscrits = TableauDeBord.Inscrits.anonymiser(annuaire.compterInscrits(null),
				annuaire.compterInscrits(debut), lesPays == null ? Map.of() : lesPays.parPays());
		var lesProjets = new TableauDeBord.Projets(p == null ? 0 : p.compter(null), p == null ? 0 : p.compter(debut),
				d == null ? 0 : d.projetsEnLigne(), d == null ? 0 : d.projetsSurDatacloud());
		return new TableauDeBord(periode, maintenant, lesInscrits, lesProjets, moments(debut));
	}

	private List<TableauDeBord.Moment> moments(Instant debut) {
		return base.sql("""
				SELECT service, count(*) AS proposes, count(accepte_le) AS acceptes
				FROM moment_systalink
				WHERE propose_le >= :debut
				GROUP BY service
				ORDER BY proposes DESC, service""")
			// « Depuis le début » : une date plus ancienne que tout moment possible.
			.param("debut", (debut == null ? Instant.EPOCH : debut).atOffset(ZoneOffset.UTC))
			.query((ligne, n) -> new TableauDeBord.Moment(ServiceSystalink.depuisCode(ligne.getString("service")),
					ligne.getLong("proposes"), ligne.getLong("acceptes")))
			.list();
	}

}
