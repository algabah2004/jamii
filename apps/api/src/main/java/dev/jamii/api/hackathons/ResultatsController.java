package dev.jamii.api.hackathons;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.jamii.api.auth.Annuaire;
import dev.jamii.api.auth.Compte;
import dev.jamii.api.auth.UtilisateurConnecte;

/** Confirmer et lire les résultats d'un hackathon (contracts/hackathons.yaml). La lecture est publique une fois publiés. */
@RestController
@RequestMapping("/api/hackathons/{hackathonId}/resultats")
class ResultatsController {

	record GagnantVue(Hackathons.Prix prix, InscriptionsController.EquipeVue equipe) {
	}

	record LigneVue(int rang, InscriptionsController.EquipeVue equipe, RendusController.RenduVue rendu, List<String> retourDuJury) {
	}

	record ResultatsVue(Instant publieLe, List<GagnantVue> gagnants, List<LigneVue> classement) {
	}

	private final Resultats resultats;

	private final Inscriptions inscriptions;

	private final Rendus rendus;

	private final Annuaire annuaire;

	ResultatsController(Resultats resultats, Inscriptions inscriptions, Rendus rendus, Annuaire annuaire) {
		this.resultats = resultats;
		this.inscriptions = inscriptions;
		this.rendus = rendus;
		this.annuaire = annuaire;
	}

	@GetMapping
	ResultatsVue lire(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable UUID hackathonId) {
		return vue(resultats.lire(hackathonId, moi == null ? null : moi.id()));
	}

	@PostMapping
	ResultatsVue confirmer(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable UUID hackathonId,
			@RequestBody Map<String, Object> corps) {
		Corps c = new Corps(corps, Set.of("version", "gagnants"));
		int version = c.entier("version", 0, Integer.MAX_VALUE, true);
		List<Map<String, Object>> objets = c.objets("gagnants", 0, 10, true);
		List<Resultats.Gagnant> gagnants = new ArrayList<>();
		if (objets != null) {
			for (int i = 0; i < objets.size(); i++) {
				Corps g = c.sous(objets.get(i), Set.of("prixId", "equipeId", "raison"), "gagnants[" + i + "].");
				UUID prix = g.identifiant("prixId", true);
				UUID equipe = g.identifiant("equipeId", true);
				String raison = g.texte("raison", 1, 1000, false);
				gagnants.add(new Resultats.Gagnant(prix, equipe, raison));
			}
		}
		c.verifier();
		return vue(resultats.confirmer(hackathonId, moi.id(), version, gagnants));
	}

	// ---------------------------------------------------------------- vues

	private ResultatsVue vue(Resultats.Publies p) {
		List<UUID> ids = Stream.concat(p.gagnants().stream().map(Resultats.Gagnant::equipeId), p.classement().stream().map(Resultats.Ligne::equipeId))
			.distinct().toList();
		Map<UUID, InscriptionsController.EquipeVue> equipes = equipes(inscriptions.lireEquipes(ids)).stream()
			.collect(Collectors.toMap(InscriptionsController.EquipeVue::id, Function.identity()));
		Map<UUID, Hackathons.Prix> prix = p.prix().stream().collect(Collectors.toMap(Hackathons.Prix::id, Function.identity()));
		Map<UUID, Rendus.Rendu> parRendu = rendus.lire(p.classement().stream().map(Resultats.Ligne::renduId).toList()).stream()
			.collect(Collectors.toMap(Rendus.Rendu::id, Function.identity()));
		return new ResultatsVue(p.publieLe(),
				p.gagnants().stream().map(g -> new GagnantVue(prix.get(g.prixId()), equipes.get(g.equipeId()))).toList(),
				p.classement().stream()
					.map(l -> new LigneVue(l.rang(), equipes.get(l.equipeId()), RendusController.vue(parRendu.get(l.renduId())), l.retourDuJury()))
					.toList());
	}

	private List<InscriptionsController.EquipeVue> equipes(List<Inscriptions.Equipe> liste) {
		Map<UUID, Compte> comptes = annuaire.lire(liste.stream()
			.flatMap(e -> Stream.concat(Stream.of(e.chefId()), e.membres().stream())).distinct().toList());
		return liste.stream()
			.map(e -> new InscriptionsController.EquipeVue(e.id(), e.nom(), personne(e.chefId(), comptes.get(e.chefId())),
					e.membres().stream().map(m -> personne(m, comptes.get(m))).toList(), e.tailleMax(), e.membres().size() >= e.tailleMax(),
					e.creeLe()))
			.toList();
	}

	private static InscriptionsController.PersonneResume personne(UUID id, Compte compte) {
		return new InscriptionsController.PersonneResume(id, compte == null ? "Compte supprimé" : compte.nom(),
				compte == null ? null : compte.avatarUrl());
	}

}
