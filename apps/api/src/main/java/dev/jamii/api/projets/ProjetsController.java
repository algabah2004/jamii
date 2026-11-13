package dev.jamii.api.projets;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import dev.jamii.api.auth.Annuaire;
import dev.jamii.api.auth.Compte;
import dev.jamii.api.auth.UtilisateurConnecte;
import dev.jamii.api.demandes.Demandes;

/** POST /api/projets et GET /api/projets/{projetId} (contracts/projets.yaml). */
@RestController
class ProjetsController {

	record CreationProjet(String titre, String description, String origine, UUID demandeId) {
	}

	record PersonneResume(UUID id, String nom, String avatarUrl) {
	}

	record MembreEquipe(PersonneResume personne, String role, boolean estPorteur, Instant rejointLe) {
	}

	record Moi(boolean estPorteur, boolean estMembre) {
	}

	/** Schéma `Projet` du contrat. Rôles et alertes arrivent avec leurs routes : listes vides en attendant. */
	record Projet(UUID id, String titre, String origine, String statut, PersonneResume porteur,
			List<String> competencesRecherchees, int nombreMembres, Instant creeLe, String description, UUID demandeId,
			UUID projetRepris, List<Object> roles, List<MembreEquipe> equipe, List<Object> alertes,
			Instant derniereActiviteLe, int version, Moi moi) {
	}

	private final GestionProjets gestion;

	private final Projets projets;

	private final Annuaire annuaire;

	private final Demandes demandes;

	ProjetsController(GestionProjets gestion, Projets projets, Annuaire annuaire, Demandes demandes) {
		this.gestion = gestion;
		this.projets = projets;
		this.annuaire = annuaire;
		this.demandes = demandes;
	}

	@PostMapping("/api/projets")
	ResponseEntity<?> creer(@RequestBody CreationProjet demande, @AuthenticationPrincipal UtilisateurConnecte moi) {
		String titre = demande.titre() == null ? "" : demande.titre().strip();
		String description = demande.description() == null ? "" : demande.description().strip();
		List<Map<String, String>> erreurs = new java.util.ArrayList<>();
		if (titre.length() < 3 || titre.length() > 120) {
			erreurs.add(Map.of("champ", "titre", "code", "longueur_invalide"));
		}
		if (description.length() < 10 || description.length() > 5000) {
			erreurs.add(Map.of("champ", "description", "code", "longueur_invalide"));
		}
		boolean idee = "idee".equals(demande.origine());
		if (!idee && !"demande".equals(demande.origine())) {
			erreurs.add(Map.of("champ", "origine", "code", "valeur_invalide"));
		}
		if (idee && demande.demandeId() != null) {
			erreurs.add(Map.of("champ", "demandeId", "code", "interdit"));
		}
		if ("demande".equals(demande.origine()) && demande.demandeId() == null) {
			erreurs.add(Map.of("champ", "demandeId", "code", "obligatoire"));
		}
		if (!erreurs.isEmpty()) {
			return probleme(HttpStatus.BAD_REQUEST, "validation", "Certains champs sont invalides", erreurs);
		}
		UUID id;
		if (idee) {
			id = gestion.creerDepuisUneIdee(moi.id(), titre, description, Instant.now());
		}
		else {
			// Une demande n'est visible que de son auteur : pour tout autre, elle est inconnue (contrat : 404).
			if (!demandes.estAuteur(demande.demandeId(), moi.id())) {
				return introuvable();
			}
			try {
				id = gestion.creerDepuisUneDemande(moi.id(), titre, description, demande.demandeId(), Instant.now());
			}
			catch (DuplicateKeyException dejaPrise) {
				return probleme(HttpStatus.CONFLICT, "demande_deja_prise", "Une équipe a déjà un projet actif sur cette demande", null);
			}
		}
		return ResponseEntity.created(URI.create("/api/projets/" + id)).body(charger(id, moi.id()));
	}

	@GetMapping("/api/projets/{projetId}")
	ResponseEntity<?> lire(@PathVariable UUID projetId, @AuthenticationPrincipal UtilisateurConnecte moi) {
		if (projets.acces(projetId, moi.id()) == AccesAuProjet.INTROUVABLE) {
			return introuvable();
		}
		return ResponseEntity.ok(charger(projetId, moi.id()));
	}

	private Projet charger(UUID projetId, UUID moi) {
		var fiche = gestion.lire(projetId).orElseThrow();
		var equipe = gestion.equipe(projetId);
		Map<UUID, Compte> comptes = annuaire.lire(equipe.stream().map(GestionProjets.Membre::utilisateurId).toList());
		List<MembreEquipe> membres = equipe.stream()
			.filter(m -> comptes.containsKey(m.utilisateurId()))
			.map(m -> new MembreEquipe(personne(comptes.get(m.utilisateurId())), null,
					m.utilisateurId().equals(fiche.porteurId()), m.rejointLe()))
			.toList();
		PersonneResume porteur = annuaire.lire(fiche.porteurId()).map(ProjetsController::personne).orElseThrow();
		boolean estMembre = equipe.stream().anyMatch(m -> m.utilisateurId().equals(moi));
		return new Projet(fiche.id(), fiche.titre(), fiche.origine(), fiche.statut(), porteur, List.of(), membres.size(),
				fiche.creeLe(), fiche.description(), fiche.demandeId(), fiche.projetReprisId(), List.of(), membres, List.of(),
				fiche.derniereActiviteLe(), fiche.version(), new Moi(fiche.porteurId().equals(moi), estMembre));
	}

	private static PersonneResume personne(Compte compte) {
		return new PersonneResume(compte.id(), compte.nom(), compte.avatarUrl());
	}

	private static ResponseEntity<ProblemDetail> introuvable() {
		return probleme(HttpStatus.NOT_FOUND, "introuvable", "Ressource introuvable", null);
	}

	private static ResponseEntity<ProblemDetail> probleme(HttpStatus statut, String code, String titre,
			List<Map<String, String>> erreurs) {
		ProblemDetail erreur = ProblemDetail.forStatus(statut);
		erreur.setType(URI.create("urn:jamii:erreur:" + code));
		erreur.setTitle(titre);
		erreur.setProperty("code", code);
		if (erreurs != null) {
			erreur.setProperty("erreurs", erreurs);
		}
		return ResponseEntity.status(statut).body(erreur);
	}

}
