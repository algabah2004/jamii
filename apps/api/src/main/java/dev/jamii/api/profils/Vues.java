package dev.jamii.api.profils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import dev.jamii.api.auth.Compte;

/** Les réponses JSON, au plus près des schémas de contracts/profils.yaml. */
final class Vues {

	private Vues() {
	}

	record Generation(String statut, String erreur, Instant lanceeLe, Instant termineeLe) {

		static Generation depuis(dev.jamii.api.profils.Profil p) {
			return new Generation(p.generationStatut(), p.generationErreur(), p.generationLanceeLe(),
					p.generationTermineeLe());
		}

	}

	record Langage(String nom, BigDecimal part) {
	}

	/** `niveau` et `preuves` : seulement pour la source `ia`, absents sinon. */
	@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY)
	record CompetenceProfil(String competence, String source, String niveau, List<String> preuves) {
	}

	record DepotGithub(String nom, String url, String description, String langage, int etoiles, Instant misAJourLe) {
	}

	/** Une réalisation déclarée hors de Jamii : le front l'étiquette « Déclaré », jamais « Vérifié ». */
	record Realisation(String titre, String role, String description, List<String> technologies, String url,
			Integer annee) {
	}

	/** Schéma Profil : ce que les autres voient d'un profil publié. Jamais l'e-mail. */
	record ProfilPublic(UUID utilisateurId, String nom, String avatarUrl, String loginGithub, String titre, String bio,
			String resumeIa, String pays, String ville, String disponibilite, Integer heuresParSemaine,
			String linkedin, String siteWeb, List<String> langues, List<String> competencesPhares,
			List<Realisation> realisations, List<Langage> langages, List<CompetenceProfil> competences,
			List<ProjetDuProfil> projets, List<DepotGithub> depotsGithub, List<PointFiabilite> fiabilite, Instant misAJourLe) {

		/** Sans projets ni preuves (l'export des données personnelles : ce sont des données d'autres modules). */
		static ProfilPublic depuis(dev.jamii.api.profils.Profil p, Compte compte) {
			return depuis(p, compte, Preuves.Resume.VIDE);
		}

		/** Avec ses projets Jamii et ses preuves, calculés par les modules ({@link Preuves}). */
		static ProfilPublic depuis(dev.jamii.api.profils.Profil p, Compte compte, Preuves.Resume travail) {
			return new ProfilPublic(p.utilisateurId(), compte.nom(), compte.avatarUrl(), compte.loginGithub(), p.titre(),
					p.bio(),
					p.afficherResumeIa() ? p.resumeIa() : null, p.pays(), p.ville(), p.disponibilite(),
					p.heuresParSemaine(), p.linkedin(), p.siteWeb(), p.langues(), p.competencesPhares(),
					p.realisations()
						.stream()
						.map(r -> new Realisation(r.titre(), r.role(), r.description(), r.listeTechnologies(), r.url(),
								r.annee()))
						.toList(),
					p.langages().stream().map(l -> new Langage(l.nom(), l.part())).toList(),
					competences(p), travail.projets(),
					p.depots().stream()
						.map(d -> new DepotGithub(d.nom(), d.url(), d.description(), d.langage(), d.etoiles(),
								d.misAJourLe()))
						.toList(),
					travail.fiabilite(), p.misAJourLe());
		}

		/**
		 * Une compétence n'apparaît qu'une fois, avec la source la plus parlante : celle gardée de la carte IA (niveau
		 * et preuves), puis GitHub, puis la déclaration.
		 */
		private static List<CompetenceProfil> competences(dev.jamii.api.profils.Profil p) {
			var parNom = new java.util.TreeMap<String, dev.jamii.api.profils.Profil.CompetenceProfil>();
			p.competences().forEach(c -> parNom.merge(c.competence(), c, (a, b) -> rang(a) <= rang(b) ? a : b));
			return parNom.values()
				.stream()
				.sorted(Comparator.comparingInt(ProfilPublic::rang)
					.thenComparing(dev.jamii.api.profils.Profil.CompetenceProfil::competence))
				.map(c -> new CompetenceProfil(c.competence(), c.source(), c.niveau(), c.listePreuves()))
				.toList();
		}

		private static int rang(dev.jamii.api.profils.Profil.CompetenceProfil c) {
			return switch (c.source()) {
				case "ia" -> 0;
				case "github" -> 1;
				default -> 2;
			};
		}

	}

	/** Schéma MonProfil : le profil, plus ce que moi seul vois. */
	record MonProfil(@JsonUnwrapped ProfilPublic profil, boolean publie, boolean afficherResumeIa, String resumeIaPropose,
			List<CartesIa.Proposee> competencesProposees, Generation generation, int version) {

		static MonProfil depuis(dev.jamii.api.profils.Profil p, Compte compte) {
			return depuis(p, compte, Preuves.Resume.VIDE);
		}

		static MonProfil depuis(dev.jamii.api.profils.Profil p, Compte compte, Preuves.Resume travail) {
			return new MonProfil(ProfilPublic.depuis(p, compte, travail), p.publie(), p.afficherResumeIa(), p.resumeIa(),
					p.competencesProposees(), Generation.depuis(p), p.version());
		}

	}

	record ProfilResume(UUID utilisateurId, String nom, String avatarUrl, String pays, String ville,
			String disponibilite, Integer heuresParSemaine, List<String> competences, int projetsEnLigne) {
	}

	/** Schéma ProjetDuProfil : un projet Jamii de la personne, en ligne ou non. */
	record ProjetDuProfil(UUID projetId, String titre, String role, List<String> paysEquipe, boolean enLigne, String url,
			String hebergeur, Instant verifieLe) {
	}

	/** Schéma PointFiabilite. `contestees` : toujours 0 tant que la contestation n'existe pas. */
	record PointFiabilite(String type, int nombre, Integer sur, int contestees) {
	}

	record SourcePreuve(String module, UUID id, String titre) {
	}

	record PersonneResume(UUID id, String nom, String avatarUrl) {

		static PersonneResume depuis(Compte c) {
			return new PersonneResume(c.id(), c.nom(), c.avatarUrl());
		}
	}

	/** Schéma Preuve. */
	record Preuve(UUID id, String type, SourcePreuve source, String detail, PersonneResume auteur, boolean contestee,
			Instant creeLe) {
	}

	record Page<T>(List<T> elements, String curseurSuivant) {
	}

}
