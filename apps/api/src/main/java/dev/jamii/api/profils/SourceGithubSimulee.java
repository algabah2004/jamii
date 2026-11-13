package dev.jamii.api.profils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Données GitHub factices et toujours identiques : pour les tests, la démo sans réseau, ou quand GitHub est indisponible.
 * Le login « introuvable » simule un compte supprimé, « panne » une panne de GitHub.
 */
class SourceGithubSimulee implements SourceGithub {

	@Override
	public List<Depot> depotsPublics(String login) {
		if ("introuvable".equals(login)) {
			throw new CompteIntrouvable(login);
		}
		if ("panne".equals(login)) {
			throw new IllegalStateException("GitHub simulé en panne");
		}
		Instant maintenant = Instant.parse("2026-11-10T12:00:00Z");
		String base = "https://github.com/" + login + "/";
		return List.of(
				new Depot("marche-facile", base + "marche-facile", "Commandes WhatsApp pour les vendeuses du marché", "Dart",
						42, 5200, false, List.of("flutter", "firebase"), maintenant.minus(2, ChronoUnit.DAYS)),
				new Depot("api-paiements", base + "api-paiements", "Paiement mobile, avec reprise si le réseau coupe",
						"TypeScript", 17, 2100, false, List.of("nodejs", "postgres"), maintenant.minus(9, ChronoUnit.DAYS)),
				new Depot("agri-alertes", base + "agri-alertes", "Alertes météo par SMS pour les agriculteurs", "Dart", 8,
						1800, false, List.of("flutter"), maintenant.minus(20, ChronoUnit.DAYS)),
				new Depot("portfolio", base + "portfolio", null, "HTML", 1, 300, false, List.of(),
						maintenant.minus(90, ChronoUnit.DAYS)),
				new Depot("flutter-samples", base + "flutter-samples", "Fork d'exemples", "Dart", 0, 9000, true, List.of(),
						maintenant.minus(300, ChronoUnit.DAYS)));
	}

}
