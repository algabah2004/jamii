package dev.jamii.api.communautes;

import java.util.Optional;

/**
 * Le service qui propose une réponse à une question (AGENTS.md §3 : aucun appel direct à un fournisseur dans le code
 * métier). Choisi par la configuration `COMMUNAUTES_IA` :
 * <ul>
 * <li>`simulee` : une réponse prudente, sans réseau (local, tests, démo) ;</li>
 * <li>`aucune` : pas de proposition, la question reste ouverte aux humains.</li>
 * </ul>
 * L'adaptateur du service IA de l'équipe (tâche `reponse_question`, #69) s'ajoutera ici quand il existera.
 */
interface ReponsesIa {

	/** Ce que reçoit l'IA : le texte de la question et le thème de la communauté. Aucune donnée personnelle. */
	record Question(String titre, String contenu, String theme) {
	}

	/** Le texte proposé, ou vide si l'IA n'a rien de fiable à proposer. Peut lever une exception si le service tombe. */
	Optional<String> proposer(Question question);

}
