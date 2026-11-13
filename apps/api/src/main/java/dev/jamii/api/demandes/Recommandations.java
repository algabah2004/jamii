package dev.jamii.api.demandes;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import dev.jamii.api.auth.Annuaire;
import dev.jamii.api.auth.Compte;
import dev.jamii.api.profils.ProfilsPublies;

/**
 * Les développeurs qui peuvent répondre à un besoin : un score calculé, **sans IA**, sur les profils publiés et
 * disponibles. Gratuit, instantané, testable, et chaque recommandation dit pourquoi (contracts/demandes.yaml).
 * <ul>
 * <li>compétences demandées couvertes : 70 points au plus (le critère principal) ;</li>
 * <li>même ville : 20 points, sinon même pays : 10 ;</li>
 * <li>10 heures ou plus par semaine : 10 points.</li>
 * </ul>
 * Un profil qui ne couvre aucune compétence demandée n'est jamais recommandé.
 */
@Component
class Recommandations {

	static final int MAX = 5;

	record Recommandation(UUID utilisateurId, String nom, String avatarUrl, String ville, String pays, int score,
			List<String> competencesCouvertes, List<String> raisons) {
	}

	private final ProfilsPublies profils;

	private final Annuaire annuaire;

	Recommandations(ProfilsPublies profils, Annuaire annuaire) {
		this.profils = profils;
		this.annuaire = annuaire;
	}

	List<Recommandation> pour(List<String> competencesDemandees, String ville, String pays, UUID auteur) {
		List<String> demandees = competencesDemandees.stream().map(Recommandations::normaliser).distinct().toList();
		if (demandees.isEmpty()) {
			return List.of();
		}
		record Candidat(ProfilsPublies.Resume profil, int score, List<String> couvertes, List<String> raisons) {
		}
		List<Candidat> candidats = new ArrayList<>();
		for (ProfilsPublies.Resume profil : profils.disponibles(Set.of(auteur))) {
			List<String> siennes = profil.competences().stream().map(Recommandations::normaliser).toList();
			List<String> couvertes = demandees.stream().filter(d -> siennes.stream().anyMatch(s -> proches(d, s))).toList();
			if (couvertes.isEmpty()) {
				continue;
			}
			int score = Math.round(70f * couvertes.size() / demandees.size());
			List<String> raisons = new ArrayList<>();
			raisons.add("Compétences : " + String.join(", ", couvertes));
			raisons.add("Disponible");
			if (ville != null && ville.equalsIgnoreCase(profil.ville())) {
				score += 20;
				raisons.add(profil.ville());
			}
			else if (pays != null && pays.equals(profil.pays())) {
				score += 10;
				raisons.add("Même pays");
			}
			if (profil.heuresParSemaine() != null && profil.heuresParSemaine() >= 10) {
				score += 10;
				raisons.add(profil.heuresParSemaine() + " h par semaine");
			}
			candidats.add(new Candidat(profil, Math.min(100, score), couvertes, raisons));
		}
		List<Candidat> meilleurs = candidats.stream()
			.sorted(Comparator.comparingInt(Candidat::score).reversed())
			.limit(MAX)
			.toList();
		Map<UUID, Compte> comptes = annuaire.lire(meilleurs.stream().map(c -> c.profil().utilisateurId()).toList());
		return meilleurs.stream()
			.filter(c -> comptes.containsKey(c.profil().utilisateurId()))
			.map(c -> {
				Compte compte = comptes.get(c.profil().utilisateurId());
				return new Recommandation(compte.id(), compte.nom(), compte.avatarUrl(), c.profil().ville(),
						c.profil().pays(), c.score(), c.couvertes(), c.raisons().stream().limit(4).toList());
			})
			.toList();
	}

	static String normaliser(String competence) {
		return competence == null ? "" : competence.strip().toLowerCase(Locale.ROOT);
	}

	/** `spring` couvre `spring-boot` et inversement : même famille, pas besoin d'IA pour le voir. */
	static boolean proches(String demandee, String possedee) {
		return !demandee.isEmpty() && (demandee.equals(possedee) || possedee.startsWith(demandee + "-")
				|| demandee.startsWith(possedee + "-"));
	}

}
