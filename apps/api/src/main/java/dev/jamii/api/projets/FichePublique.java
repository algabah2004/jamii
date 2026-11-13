package dev.jamii.api.projets;

import java.util.List;
import java.util.UUID;

/**
 * Ce que la vitrine a le droit de montrer d'un projet : rien de privé (ni description complète, ni e-mail).
 * `equipe` : le porteur d'abord, puis les autres dans l'ordre d'arrivée. Les pays viennent du module profils.
 */
public record FichePublique(UUID id, String titre, String accroche, String origine, List<Personne> equipe) {

	/** Une personne de l'équipe, sans e-mail. */
	public record Personne(UUID id, String nom, String avatarUrl) {
	}

}
