package dev.jamii.api.cercles;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Le dépôt Git d'un tour. Le module cercles ne parle à aucun fournisseur : il appelle cette interface, et un adaptateur
 * (Gitea, GitHub) l'implémente avec un bean Spring. Le choix se fait par la configuration (`DEPOT_CODE`), jamais en
 * réécrivant le code (AGENTS.md, §3). Sans bean, les tours n'ont simplement pas de dépôt (`depot: null`).
 * <p>
 * Appelée <b>après</b> la transaction du tirage ou de la livraison, jamais pendant : un fournisseur lent ou en panne ne
 * bloque ni ne défait un tirage. Une exception, ou une réponse vide, laisse le tour sans dépôt ; elle peut être rejouée.
 */
public interface DepotCode {

	Optional<DepotDuTour> creerDepot(DemandeDepot demande);

	/** `loginsGithub` : les membres du cercle qui ont un compte GitHub (les autres n'y figurent pas). */
	record DemandeDepot(UUID cercleId, String cercleNom, int numero, String titre, List<String> loginsGithub) {
	}

	/** `fournisseur` vaut `gitea` ou `github`. Les deux adresses sont en `https`. */
	record DepotDuTour(String fournisseur, String url, String cloneUrl) {
	}

}
