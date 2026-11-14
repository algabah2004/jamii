package dev.jamii.api.profils;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.jamii.api.auth.Annuaire;
import dev.jamii.api.auth.DonneesPersonnelles;

/**
 * Le profil d'un compte dans l'export (demande #113) : exactement ce que « Mon profil » montre à son auteur, bio,
 * compétences avec leurs preuves, langages, dépôts, et la carte proposée par l'IA même non publiée. `null` si le
 * profil n'a jamais été généré. Rien à faire avant la suppression : le profil part en cascade avec le compte.
 */
@Component
class DonneesProfils implements DonneesPersonnelles {

	private final ProfilRepository profils;

	private final Annuaire comptes;

	DonneesProfils(ProfilRepository profils, Annuaire comptes) {
		this.profils = profils;
		this.comptes = comptes;
	}

	@Override
	public String section() {
		return "profils";
	}

	@Override
	@Transactional(readOnly = true)
	public Vues.MonProfil exporter(UUID utilisateurId) {
		return profils.findById(utilisateurId)
			.flatMap(p -> comptes.lire(utilisateurId).map(compte -> Vues.MonProfil.depuis(p, compte)))
			.orElse(null);
	}

}
