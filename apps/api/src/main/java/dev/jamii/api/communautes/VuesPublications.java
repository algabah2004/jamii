package dev.jamii.api.communautes;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import dev.jamii.api.auth.Annuaire;
import dev.jamii.api.auth.Compte;

/** Construit la vue d'une publication (schéma `Publication` du contrat), partagée par les routes qui la renvoient. */
@Component
class VuesPublications {

	private final GestionCommunautes communautes;

	private final Annuaire annuaire;

	private final dev.jamii.api.fichiers.Fichiers fichiers;

	VuesPublications(GestionCommunautes communautes, Annuaire annuaire, dev.jamii.api.fichiers.Fichiers fichiers) {
		this.communautes = communautes;
		this.annuaire = annuaire;
		this.fichiers = fichiers;
	}

	CommunautesController.Publication publication(UUID id, UUID moi) {
		var detail = communautes.lirePublication(id).orElseThrow();
		var reponses = communautes.reponses(id);
		Set<UUID> personnes = new HashSet<>();
		Stream.concat(Stream.of(detail.resume().auteurId()),
				reponses.stream().map(GestionCommunautes.LigneReponse::auteurId))
			.filter(p -> p != null).forEach(personnes::add);
		Map<UUID, Compte> comptes = annuaire.lire(personnes);
		var r = resume(detail.resume(), comptes, fichiers);
		List<CommunautesController.Reponse> liste = reponses.stream()
			.map(x -> new CommunautesController.Reponse(x.id(), x.contenu(), personne(x.auteurId(), comptes), x.id().equals(detail.reponseAccepteeId()), x.creeLe()))
			.toList();
		return new CommunautesController.Publication(r.id(), r.communaute(), r.type(), r.titre(), r.auteur(), r.nombreReponses(), r.accepteeLe(),
				r.creeLe(), detail.contenu(), detail.projetId(), detail.reponseAccepteeId(), liste,
				new CommunautesController.Moi(moi.equals(detail.resume().auteurId()), communautes.estMembre(detail.communauteId(), moi)),
				r.piecesJointes());
	}

	static CommunautesController.PublicationResume resume(GestionCommunautes.Resume r, Map<UUID, Compte> comptes,
			dev.jamii.api.fichiers.Fichiers fichiers) {
		return new CommunautesController.PublicationResume(r.id(), r.communaute(), r.type(), r.titre(), personne(r.auteurId(), comptes),
				r.nombreReponses(), r.accepteeLe(), r.creeLe(), r.contenu(), fichiers.decrire(r.pieces()));
	}

	static CommunautesController.PersonneResume personne(UUID id, Map<UUID, Compte> comptes) {
		Compte c = id == null ? null : comptes.get(id);
		return c == null ? null : new CommunautesController.PersonneResume(c.id(), c.nom(), c.avatarUrl());
	}

}
