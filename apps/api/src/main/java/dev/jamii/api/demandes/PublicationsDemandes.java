package dev.jamii.api.demandes;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;

/**
 * La publication d'une demande. Une seule transaction : la demande est marquée publiée **et** l'événement
 * {@link DemandePubliee} est publié, ou rien. Les écouteurs reçoivent l'événement après la validation de la
 * transaction et leurs pannes ne la défont pas.
 */
@Service
class PublicationsDemandes {

	/** La publication a déjà eu lieu : on ne la refait pas, et l'événement ne repart pas. */
	static final class DejaPubliee extends RuntimeException {

		DejaPubliee() {
			super(null, null, false, false);
		}

	}

	private final JdbcClient base;

	private final ApplicationEventPublisher evenements;

	PublicationsDemandes(JdbcClient base, ApplicationEventPublisher evenements) {
		this.base = base;
		this.evenements = evenements;
	}

	/**
	 * @param resume le résumé relu par l'auteur, déjà sans coordonnées
	 * @param orientation le résultat de l'orientation (le plan est prêt), d'où viennent les compétences
	 * @throws DejaPubliee si la demande était déjà publiée (y compris par une requête simultanée)
	 */
	@Transactional
	DemandePubliee publier(GestionDemandes.Fiche demande, String resume, JsonNode orientation, Instant maintenant) {
		// La condition `publiee_le IS NULL` départage deux publications simultanées : une seule met à jour une ligne.
		int lignes = base.sql("""
				UPDATE demande SET publiee_le = :t, publiee_resume = :resume
				WHERE id = :id AND auteur_id = :auteur AND publiee_le IS NULL
				""")
			.param("t", Timestamp.from(maintenant)).param("resume", resume)
			.param("id", demande.id()).param("auteur", demande.auteurId())
			.update();
		if (lignes == 0) {
			throw new DejaPubliee();
		}
		List<String> competences = new ArrayList<>();
		orientation.path("competences").forEach(c -> competences.add(c.asString("")));
		// Le secteur que la personne a écrit prime ; à défaut, celui que l'orientation a reconnu.
		String secteur = demande.secteur() != null ? demande.secteur()
				: orientation.path("secteur").isTextual() ? orientation.path("secteur").asString() : null;
		DemandePubliee evenement = new DemandePubliee(demande.id(), demande.auteurId(), demande.titre(), resume,
				secteur, demande.ville(), demande.pays(), demande.dureeSemaines(), competences, maintenant);
		evenements.publishEvent(evenement);
		return evenement;
	}

}
