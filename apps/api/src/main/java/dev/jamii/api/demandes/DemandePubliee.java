package dev.jamii.api.demandes;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Événement public : une personne vient de publier sa demande, et la communauté peut la voir. Le module des
 * communautés l'affiche dans un salon ; il n'a pas besoin de lire les tables des demandes (docs/equipe.md : entre
 * deux modules, seulement un contrat ou un événement public).
 *
 * <p>Il ne porte que ce que l'auteur a choisi de montrer : le titre, le résumé qu'il a relu et le contexte du besoin.
 * Jamais le texte d'origine, jamais l'orientation complète, jamais une adresse e-mail ou un téléphone. Pour afficher
 * la personne, on passe par l'identifiant {@code auteurId} et l'annuaire des comptes.
 *
 * @param demandeId la demande publiée
 * @param auteurId son auteur
 * @param titre le titre de la demande
 * @param resume le résumé relu par l'auteur, sans coordonnées (600 caractères au plus)
 * @param secteur le secteur d'activité, ou null
 * @param ville la ville, ou null
 * @param pays le pays (ISO 3166-1 alpha-2), ou null
 * @param dureeSemaines la durée souhaitée, ou null
 * @param competences les compétences nécessaires, dans l'ordre de l'orientation (peut être vide)
 * @param publieeLe l'instant de la publication
 */
public record DemandePubliee(UUID demandeId, UUID auteurId, String titre, String resume, String secteur, String ville,
		String pays, Integer dureeSemaines, List<String> competences, Instant publieeLe) {

	public DemandePubliee {
		competences = List.copyOf(competences);
	}

}
