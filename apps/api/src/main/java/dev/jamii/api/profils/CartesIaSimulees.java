package dev.jamii.api.profils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Sans service IA (local, tests, démo sans réseau) : la même règle que le repli du service IA
 * (services/ia, analyse_profil.repli). Chaque langage principal devient une compétence, prouvée par les dépôts qui
 * l'utilisent ; le niveau dépend du nombre de ces dépôts.
 */
class CartesIaSimulees implements CartesIa {

	@Override
	public List<Proposee> proposer(UUID cle, Entree entree) {
		List<Proposee> carte = new ArrayList<>();
		entree.langages().stream().limit(6).forEach(langage -> {
			List<String> preuves = entree.depots()
				.stream()
				.filter(d -> langage.nom().equals(d.langage()))
				.map(Depot::nom)
				.toList();
			if (!preuves.isEmpty()) {
				String niveau = preuves.size() >= 4 ? "confirme" : preuves.size() >= 2 ? "intermediaire" : "debutant";
				carte.add(new Proposee(langage.nom().toLowerCase(Locale.ROOT), niveau, preuves,
						"Langage principal de " + preuves.size() + " dépôt(s) public(s)."));
			}
		});
		return CartesIa.nettoyer(carte, entree);
	}

}
