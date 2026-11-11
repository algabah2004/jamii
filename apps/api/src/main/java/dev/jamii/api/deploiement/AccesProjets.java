package dev.jamii.api.deploiement;

import java.util.UUID;

/**
 * Ce que le module deploiement demande au module projets (#17), sans lire ses tables : un utilisateur peut-il voir ce
 * projet, et fait-il partie de son équipe ?
 * <p>
 * Le module projets l'implémente avec un bean Spring. Tant qu'il ne l'a pas fait, tout projet est
 * {@link Acces#INTROUVABLE} : on refuse plutôt que d'ouvrir la mise en ligne à n'importe qui.
 */
public interface AccesProjets {

	Acces acces(UUID projetId, UUID utilisateurId);

	enum Acces {

		/** Le projet n'existe pas, ou l'utilisateur n'a pas le droit de savoir qu'il existe (réponse 404). */
		INTROUVABLE,

		/** L'utilisateur voit le projet, mais n'est pas dans son équipe (lecture seule). */
		LECTEUR,

		/** L'utilisateur fait partie de l'équipe : il peut mettre le projet en ligne. */
		MEMBRE

	}

}
