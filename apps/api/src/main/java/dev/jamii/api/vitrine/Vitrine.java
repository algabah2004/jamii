package dev.jamii.api.vitrine;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import dev.jamii.api.deploiement.DeploiementsEnLigne;
import dev.jamii.api.deploiement.DeploiementsEnLigne.ProjetEnLigne;
import dev.jamii.api.deploiement.Hebergeur;
import dev.jamii.api.profils.ProfilsPublies;
import dev.jamii.api.projets.FichePublique;
import dev.jamii.api.projets.FichesPubliques;

/**
 * La vitrine (contracts/vitrine.yaml) : les projets vraiment en ligne. Ce n'est pas une table : elle compose ce que les
 * autres modules veulent bien dire (docs/modele-donnees.md) : l'adresse et l'hébergeur viennent du module `deploiement`
 * (déjà limité aux adresses qui ont répondu il y a moins de 24 heures), le titre, l'accroche et l'équipe de `projets`,
 * le pays des seuls profils **publiés** de `profils`.
 */
@Service
class Vitrine {

	record ProjetEnVitrine(UUID projetId, String titre, String accroche, String origine, String url, Hebergeur hebergeur,
			Instant misEnLigneLe, Instant verifieLe, List<String> paysEquipe, List<FichePublique.Personne> equipe) {
	}

	record Page(List<ProjetEnVitrine> elements, String curseurSuivant) {
	}

	/** Le curseur : où s'arrêtait la page précédente, dans l'ordre « mis en ligne le plus récemment d'abord ». */
	record Curseur(Instant misEnLigneLe, UUID projetId) {

		String texte() {
			return Base64.getUrlEncoder().withoutPadding()
				.encodeToString((misEnLigneLe + "|" + projetId).getBytes(StandardCharsets.UTF_8));
		}

		static Optional<Curseur> lire(String texte) {
			try {
				String[] parties = new String(Base64.getUrlDecoder().decode(texte), StandardCharsets.UTF_8).split("\\|");
				return Optional.of(new Curseur(Instant.parse(parties[0]), UUID.fromString(parties[1])));
			}
			catch (RuntimeException e) {
				return Optional.empty();
			}
		}

	}

	private static final Comparator<ProjetEnLigne> PLUS_RECENT_D_ABORD = Comparator
		.comparing(ProjetEnLigne::misEnLigneLe, Comparator.reverseOrder())
		.thenComparing(ProjetEnLigne::projetId, Comparator.reverseOrder());

	private final DeploiementsEnLigne deploiements;

	private final FichesPubliques fiches;

	private final ProfilsPublies profils;

	Vitrine(DeploiementsEnLigne deploiements, FichesPubliques fiches, ProfilsPublies profils) {
		this.deploiements = deploiements;
		this.fiches = fiches;
		this.profils = profils;
	}

	/**
	 * Une page de la vitrine.
	 * @param hebergeur seulement cet hébergeur, ou null
	 * @param pays seulement les équipes dont un membre (au profil publié) vient de ce pays, ou null
	 * @param apres la fin de la page précédente, ou null
	 */
	Page lister(Hebergeur hebergeur, String pays, int limite, Curseur apres) {
		List<ProjetEnLigne> enLigne = deploiements.lister().stream()
			.filter(p -> hebergeur == null || p.hebergeur() == hebergeur)
			.sorted(PLUS_RECENT_D_ABORD)
			.toList();
		// Un projet dont la fiche a disparu (supprimé) ne peut pas s'afficher : on l'écarte avant de découper les pages.
		Map<UUID, FichePublique> fichesDesProjets = fiches.lire(enLigne.stream().map(ProjetEnLigne::projetId).toList());
		List<ProjetEnLigne> candidats = enLigne.stream()
			.filter(p -> fichesDesProjets.containsKey(p.projetId()))
			.filter(p -> apres == null || apresLe(p, apres))
			.toList();

		// Avec un filtre de pays, il faut connaître celui de chaque équipe avant de découper ; sans filtre, la page suffit.
		Map<UUID, String> paysDesMembres = new HashMap<>();
		List<ProjetEnLigne> retenus = new ArrayList<>();
		for (ProjetEnLigne p : candidats) {
			if (pays != null) {
				paysDe(fichesDesProjets.get(p.projetId()), paysDesMembres);
				if (!paysEquipe(fichesDesProjets.get(p.projetId()), paysDesMembres).contains(pays)) {
					continue;
				}
			}
			retenus.add(p);
			if (retenus.size() > limite) {
				break;
			}
		}
		boolean suite = retenus.size() > limite;
		List<ProjetEnLigne> page = suite ? retenus.subList(0, limite) : retenus;

		List<ProjetEnVitrine> elements = new ArrayList<>();
		for (ProjetEnLigne p : page) {
			FichePublique f = fichesDesProjets.get(p.projetId());
			paysDe(f, paysDesMembres);
			elements.add(new ProjetEnVitrine(p.projetId(), f.titre(), f.accroche(), f.origine(), p.url(), p.hebergeur(),
					p.misEnLigneLe(), p.verifieLe(), paysEquipe(f, paysDesMembres), f.equipe()));
		}
		String suivant = suite ? new Curseur(page.getLast().misEnLigneLe(), page.getLast().projetId()).texte() : null;
		return new Page(elements, suivant);
	}

	private static boolean apresLe(ProjetEnLigne p, Curseur apres) {
		return PLUS_RECENT_D_ABORD.compare(p, new ProjetEnLigne(apres.projetId(), null, null, null, apres.misEnLigneLe(), null)) > 0;
	}

	/** Mémorise le pays des membres de cette équipe, une fois par personne : un profil privé ou sans pays n'en donne pas. */
	private void paysDe(FichePublique fiche, Map<UUID, String> dejaLus) {
		for (FichePublique.Personne m : fiche.equipe()) {
			dejaLus.computeIfAbsent(m.id(), id -> profils.publie(id).map(ProfilsPublies.Resume::pays).orElse(""));
		}
	}

	/** Les pays de l'équipe, sans doublon, dans l'ordre des membres. */
	private static List<String> paysEquipe(FichePublique fiche, Map<UUID, String> paysDesMembres) {
		Set<String> pays = new LinkedHashSet<>();
		for (FichePublique.Personne m : fiche.equipe()) {
			String p = paysDesMembres.get(m.id());
			if (p != null && !p.isEmpty()) {
				pays.add(p);
			}
		}
		return List.copyOf(pays);
	}

}
