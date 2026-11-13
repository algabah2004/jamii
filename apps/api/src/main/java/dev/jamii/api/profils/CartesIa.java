package dev.jamii.api.profils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * La carte de compétences proposée par l'IA (contracts/ia.yaml, tâche `analyse_profil`). L'IA propose, la personne
 * garde ou retire chaque compétence : rien n'est public sans son choix (contracts/profils.yaml).
 * <p>
 * Seuls des faits publics partent vers l'IA : ni login, ni nom, ni e-mail, ni URL (elle contient le login).
 */
interface CartesIa {

	int DEPOTS_MAX = 12;

	int COMPETENCES_MAX = 12;

	Set<String> NIVEAUX = Set.of("debutant", "intermediaire", "confirme");

	record Langage(String nom, double part) {
	}

	record Depot(String nom, String description, String langage, List<String> sujets, int etoiles, Instant misAJourLe) {
	}

	record Entree(List<Langage> langages, List<Depot> depots, List<String> competencesDeclarees) {
	}

	record Proposee(String competence, String niveau, List<String> preuves, String raison) {
	}

	/**
	 * La carte, déjà nettoyée. Bloquant mais borné dans le temps ; appelé en tâche de fond seulement. Lève une
	 * exception si le service ne répond pas : le profil reste alors sans proposition.
	 */
	List<Proposee> proposer(UUID cle, Entree entree);

	/** Les faits envoyés : les dépôts personnels les plus récents, sans les forks. */
	static Entree entree(List<SourceGithub.Depot> depots, List<Profil.Langage> langages, Set<String> declarees) {
		List<Depot> choisis = depots.stream()
			.filter(d -> !d.fork())
			.sorted(Comparator.comparing(SourceGithub.Depot::pousseLe, Comparator.nullsLast(Comparator.reverseOrder())))
			.limit(DEPOTS_MAX)
			.map(d -> new Depot(d.nom(), coupe(d.description(), 300), coupe(d.langage(), 40),
					d.sujets() == null ? List.of() : d.sujets().stream().map(s -> coupe(s, 50)).limit(10).toList(),
					Math.max(0, d.etoiles()), d.pousseLe()))
			.toList();
		List<Langage> parts = langages.stream()
			.limit(15)
			.map(l -> new Langage(coupe(l.nom(), 40), l.part().doubleValue()))
			.toList();
		return new Entree(parts, choisis, declarees.stream().sorted().limit(20).toList());
	}

	/**
	 * Ce que l'API accepte de l'IA, même si le service IA l'a déjà vérifié : une compétence valide, un niveau connu,
	 * des preuves qui sont des dépôts de l'entrée (jamais inventés), pas de doublon, 12 au plus.
	 */
	static List<Proposee> nettoyer(List<Proposee> brutes, Entree entree) {
		Set<String> depots = new LinkedHashSet<>();
		entree.depots().forEach(d -> depots.add(d.nom()));
		Set<String> vues = new LinkedHashSet<>();
		List<Proposee> propres = new ArrayList<>();
		for (Proposee p : brutes) {
			if (p == null || propres.size() == COMPETENCES_MAX) {
				continue;
			}
			var nom = Competences.normaliser(p.competence());
			List<String> preuves = p.preuves() == null ? List.of()
					: p.preuves().stream().filter(depots::contains).distinct().limit(3).toList();
			if (nom.isEmpty() || !NIVEAUX.contains(p.niveau()) || preuves.isEmpty() || !vues.add(nom.get())) {
				continue;
			}
			propres.add(new Proposee(nom.get(), p.niveau(), preuves, coupe(p.raison(), 200)));
		}
		return List.copyOf(propres);
	}

	private static String coupe(String texte, int max) {
		return texte == null || texte.length() <= max ? texte : texte.substring(0, max);
	}

	/** Stockage de la carte proposée dans le profil (colonne texte) : JSON, lu et écrit ici seulement. */
	final class Stockage {

		private static final JsonMapper JSON = new JsonMapper();

		private Stockage() {
		}

		static String ecrire(List<Proposee> carte) {
			return carte.isEmpty() ? null : JSON.writeValueAsString(carte);
		}

		static List<Proposee> lire(String texte) {
			return texte == null ? List.of() : JSON.readValue(texte, new TypeReference<List<Proposee>>() {
			});
		}

	}

}
