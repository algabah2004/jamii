package dev.jamii.api.profils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.jamii.api.auth.Annuaire;
import dev.jamii.api.auth.Compte;
import dev.jamii.api.auth.UtilisateurConnecte;

/** Chercher des coéquipiers : GET /api/profils (contrat : tag « profils » de contracts/profils.yaml). */
@RestController
class RechercheController {

	private static final Set<String> CODES_PAYS = Set.of(Locale.getISOCountries());

	private final Annuaire comptes;

	private final RechercheProfils recherche;

	RechercheController(Annuaire comptes, RechercheProfils recherche) {
		this.comptes = comptes;
		this.recherche = recherche;
	}

	@GetMapping("/api/profils")
	Vues.Page<Vues.ProfilResume> chercher(@AuthenticationPrincipal UtilisateurConnecte moi,
			@RequestParam(name = "competence", required = false) List<String> competences,
			@RequestParam(required = false) List<String> pays, @RequestParam(required = false) String disponibilite,
			@RequestParam(defaultValue = "20") String limite, @RequestParam(required = false) String curseur) {
		List<ErreurProfil.Champ> erreurs = new ArrayList<>();
		List<String> competencesPropres = new ArrayList<>();
		List<String> brutes = competences == null ? List.of() : competences;
		if (brutes.size() > 5) {
			erreurs.add(new ErreurProfil.Champ("competence", "trop_d_elements"));
		}
		for (String c : brutes) {
			Competences.normaliser(c).ifPresentOrElse(competencesPropres::add,
					() -> erreurs.add(new ErreurProfil.Champ("competence", "format_invalide")));
		}
		List<String> paysPropres = pays == null ? List.of() : pays;
		if (paysPropres.size() > 10 || !CODES_PAYS.containsAll(paysPropres)) {
			erreurs.add(new ErreurProfil.Champ("pays", "format_invalide"));
		}
		if (disponibilite != null && !ModificationProfil.DISPONIBILITES.contains(disponibilite)) {
			erreurs.add(new ErreurProfil.Champ("disponibilite", "format_invalide"));
		}
		int taille = 0;
		try {
			taille = Integer.parseInt(limite);
		}
		catch (NumberFormatException e) {
			// signalé juste en dessous
		}
		if (taille < 1 || taille > 100) {
			erreurs.add(new ErreurProfil.Champ("limite", "hors_limites"));
		}
		if (!erreurs.isEmpty()) {
			throw ErreurProfil.validation(erreurs);
		}

		var criteres = new RechercheProfils.Criteres(competencesPropres.stream().distinct().toList(), paysPropres,
				disponibilite, moi.id(), taille, curseur == null ? null : RechercheProfils.Curseur.decoder(curseur));
		List<RechercheProfils.Ligne> lignes = recherche.chercher(criteres);
		boolean suite = lignes.size() > taille;
		List<RechercheProfils.Ligne> page = suite ? lignes.subList(0, taille) : lignes;

		List<UUID> ids = page.stream().map(RechercheProfils.Ligne::id).toList();
		Map<UUID, Compte> parId = comptes.lire(ids);
		Map<UUID, List<String>> competencesParId = recherche.competences(ids);
		List<Vues.ProfilResume> elements = page.stream()
			.filter(l -> parId.containsKey(l.id()))
			.map(l -> new Vues.ProfilResume(l.id(), parId.get(l.id()).nom(), parId.get(l.id()).avatarUrl(), l.pays(),
					l.ville(), l.disponibilite(), l.heuresParSemaine(), competencesParId.getOrDefault(l.id(), List.of()),
					0))
			.toList();
		RechercheProfils.Ligne derniere = page.isEmpty() ? null : page.get(page.size() - 1);
		String suivant = suite ? new RechercheProfils.Curseur(derniere.rang(), derniere.misAJourLe(), derniere.id()).encoder()
				: null;
		return new Vues.Page<>(elements, suivant);
	}

}
