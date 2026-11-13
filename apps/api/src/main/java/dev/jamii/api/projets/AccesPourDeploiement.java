package dev.jamii.api.projets;

import java.util.UUID;

import org.springframework.stereotype.Component;

import dev.jamii.api.deploiement.AccesProjets;

/**
 * Répond au module deploiement (demande #62) : voit-on ce projet, et fait-on partie de son équipe ?
 * Un membre parti n'est plus MEMBRE : il ne peut plus mettre le projet en ligne.
 */
@Component
class AccesPourDeploiement implements AccesProjets {

	private final Projets projets;

	AccesPourDeploiement(Projets projets) {
		this.projets = projets;
	}

	@Override
	public Acces acces(UUID projetId, UUID utilisateurId) {
		return switch (projets.acces(projetId, utilisateurId)) {
			case INTROUVABLE -> Acces.INTROUVABLE;
			case LECTEUR -> Acces.LECTEUR;
			case MEMBRE -> Acces.MEMBRE;
		};
	}

}
