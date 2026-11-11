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

	record CompetenceProfil(String competence, String source) {
	}

	record DepotGithub(String nom, String url, String description, String langage, int etoiles, Instant misAJourLe) {
	}

	/** Schéma Profil : ce que les autres voient d'un profil publié. Jamais l'e-mail. */
	record ProfilPublic(UUID utilisateurId, String nom, String avatarUrl, String loginGithub, String bio, String resumeIa,
			String pays, String ville, String disponibilite, Integer heuresParSemaine, List<Langage> langages,
			List<CompetenceProfil> competences, List<Object> projets, List<DepotGithub> depotsGithub,
			List<Object> fiabilite, Instant misAJourLe) {

		static ProfilPublic depuis(dev.jamii.api.profils.Profil p, Compte compte) {
			return new ProfilPublic(p.utilisateurId(), compte.nom(), compte.avatarUrl(), compte.loginGithub(), p.bio(),
					p.afficherResumeIa() ? p.resumeIa() : null, p.pays(), p.ville(), p.disponibilite(),
					p.heuresParSemaine(), p.langages().stream().map(l -> new Langage(l.nom(), l.part())).toList(),
					competences(p), List.of(),
					p.depots().stream()
						.map(d -> new DepotGithub(d.nom(), d.url(), d.description(), d.langage(), d.etoiles(),
								d.misAJourLe()))
						.toList(),
					// Projets et preuves arrivent avec les événements des modules projets et deploiement (PR suivante).
					List.of(), p.misAJourLe());
		}

		/** Une compétence n'apparaît qu'une fois : si GitHub la montre, c'est la source la plus parlante. */
		private static List<CompetenceProfil> competences(dev.jamii.api.profils.Profil p) {
			var parNom = new java.util.TreeMap<String, String>();
			p.competences().forEach(c -> parNom.merge(c.competence(), c.source(),
					(a, b) -> "github".equals(a) || "github".equals(b) ? "github" : a));
			return parNom.entrySet().stream()
				.sorted(Comparator.comparing((java.util.Map.Entry<String, String> e) -> !"github".equals(e.getValue()))
					.thenComparing(java.util.Map.Entry::getKey))
				.map(e -> new CompetenceProfil(e.getKey(), e.getValue()))
				.toList();
		}

	}

	/** Schéma MonProfil : le profil, plus ce que moi seul vois. */
	record MonProfil(@JsonUnwrapped ProfilPublic profil, boolean publie, boolean afficherResumeIa, String resumeIaPropose,
			Generation generation, int version) {

		static MonProfil depuis(dev.jamii.api.profils.Profil p, Compte compte) {
			return new MonProfil(ProfilPublic.depuis(p, compte), p.publie(), p.afficherResumeIa(), p.resumeIa(),
					Generation.depuis(p), p.version());
		}

	}

}
