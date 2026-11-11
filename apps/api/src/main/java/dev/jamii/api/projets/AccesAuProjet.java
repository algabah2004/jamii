package dev.jamii.api.projets;

/** Ce qu'un utilisateur peut faire d'un projet. Les autres modules n'ont pas besoin d'en savoir plus. */
public enum AccesAuProjet {

	/** Le projet n'existe pas, ou l'utilisateur n'a pas le droit de savoir qu'il existe (réponse 404). */
	INTROUVABLE,

	/** L'utilisateur voit le projet (tout utilisateur connecté le voit) mais n'est pas dans son équipe. */
	LECTEUR,

	/** L'utilisateur fait partie de l'équipe actuelle, porteur compris. Un membre parti n'en fait plus partie. */
	MEMBRE

}
