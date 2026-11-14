package dev.jamii.api.hackathons;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.jamii.api.auth.Annuaire;
import dev.jamii.api.auth.Compte;
import dev.jamii.api.auth.UtilisateurConnecte;

/**
 * S'inscrire, former les équipes, trier les candidatures (contracts/hackathons.yaml, tag « inscriptions »). Seule la
 * liste des équipes est publique ; tout le reste demande une connexion.
 */
@RestController
@RequestMapping("/api/hackathons/{hackathonId}")
class InscriptionsController {

	record PersonneResume(UUID id, String nom, String avatarUrl) {
	}

	record InscriptionVue(UUID id, PersonneResume personne, String statut, boolean chercheEquipe, UUID equipeId,
			List<String> competences, Instant creeLe) {
	}

	record EquipeVue(UUID id, String nom, PersonneResume chef, List<PersonneResume> membres, int tailleMax, boolean complete,
			Instant creeLe) {
	}

	record PageInscriptions(List<InscriptionVue> elements, String curseurSuivant) {
	}

	record PageEquipes(List<EquipeVue> elements, String curseurSuivant) {
	}

	private static final Pattern COMPETENCE = Pattern.compile("^[a-z0-9][a-z0-9.+#-]*$");

	private static final Set<String> STATUTS = Set.of("en_attente", "acceptee", "refusee", "attente");

	private final Inscriptions inscriptions;

	private final Annuaire annuaire;

	InscriptionsController(Inscriptions inscriptions, Annuaire annuaire) {
		this.inscriptions = inscriptions;
		this.annuaire = annuaire;
	}

	@PostMapping("/inscriptions")
	ResponseEntity<InscriptionVue> sInscrire(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable UUID hackathonId,
			@RequestBody Map<String, Object> corps) {
		Corps c = new Corps(corps, Set.of("chercheEquipe", "competences"));
		boolean cherche = c.booleen("chercheEquipe", true);
		List<String> competences = competences(c, corps.get("competences"));
		c.verifier();
		Inscriptions.Inscription i = inscriptions.sInscrire(hackathonId, moi.id(), cherche, competences);
		return ResponseEntity.status(HttpStatus.CREATED).body(vues(List.of(i)).getFirst());
	}

	@GetMapping("/inscriptions")
	PageInscriptions lister(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable UUID hackathonId,
			@RequestParam(required = false) String statut, @RequestParam(defaultValue = "20") int limite,
			@RequestParam(required = false) String curseur) {
		if (statut != null && !STATUTS.contains(statut)) {
			throw ErreurHackathons.validation("statut", "valeur_inconnue");
		}
		Inscriptions.PageInscriptions page = inscriptions.lister(hackathonId, moi.id(), statut, limite(limite), curseur);
		return new PageInscriptions(vues(page.elements()), page.curseurSuivant());
	}

	@PatchMapping(path = "/inscriptions/{inscriptionId}", consumes = { "application/merge-patch+json", "application/json" })
	InscriptionVue trier(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable UUID hackathonId,
			@PathVariable UUID inscriptionId, @RequestBody Map<String, Object> corps) {
		Corps c = new Corps(corps, Set.of("statut"));
		String statut = c.choix("statut", List.of("acceptee", "refusee", "attente"), true);
		c.verifier();
		return vues(List.of(inscriptions.trier(hackathonId, inscriptionId, moi.id(), statut))).getFirst();
	}

	@GetMapping("/sans-equipe")
	PageInscriptions sansEquipe(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable UUID hackathonId,
			@RequestParam(defaultValue = "20") int limite, @RequestParam(required = false) String curseur) {
		Inscriptions.PageInscriptions page = inscriptions.sansEquipe(hackathonId, moi.id(), limite(limite), curseur);
		return new PageInscriptions(vues(page.elements()), page.curseurSuivant());
	}

	@GetMapping("/equipes")
	PageEquipes equipes(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable UUID hackathonId,
			@RequestParam(defaultValue = "20") int limite, @RequestParam(required = false) String curseur) {
		Inscriptions.PageEquipes page = inscriptions.equipes(hackathonId, moi == null ? null : moi.id(), limite(limite), curseur);
		return new PageEquipes(equipes(page.elements()), page.curseurSuivant());
	}

	@PostMapping("/equipes")
	ResponseEntity<EquipeVue> creerEquipe(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable UUID hackathonId,
			@RequestBody Map<String, Object> corps) {
		Corps c = new Corps(corps, Set.of("nom"));
		String nom = c.texte("nom", 2, 60, true);
		c.verifier();
		Inscriptions.Equipe e = inscriptions.creerEquipe(hackathonId, moi.id(), nom);
		return ResponseEntity.created(URI.create("/api/hackathons/" + hackathonId + "/equipes/" + e.id()))
			.body(equipes(List.of(e)).getFirst());
	}

	@PostMapping("/equipes/{equipeId}/membres")
	EquipeVue inviter(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable UUID hackathonId, @PathVariable UUID equipeId,
			@RequestBody Map<String, Object> corps) {
		Corps c = new Corps(corps, Set.of("utilisateurId"));
		UUID invite = c.identifiant("utilisateurId", true);
		c.verifier();
		return equipes(List.of(inscriptions.inviter(hackathonId, equipeId, moi.id(), invite))).getFirst();
	}

	// ---------------------------------------------------------------- vues

	private List<InscriptionVue> vues(List<Inscriptions.Inscription> liste) {
		Map<UUID, Compte> comptes = annuaire.lire(liste.stream().map(Inscriptions.Inscription::utilisateurId).distinct().toList());
		return liste.stream()
			.map(i -> new InscriptionVue(i.id(), personne(i.utilisateurId(), comptes.get(i.utilisateurId())), i.statut(),
					i.chercheEquipe(), i.equipeId(), i.competences(), i.creeLe()))
			.toList();
	}

	private List<EquipeVue> equipes(List<Inscriptions.Equipe> liste) {
		Map<UUID, Compte> comptes = annuaire.lire(liste.stream()
			.flatMap(e -> Stream.concat(Stream.of(e.chefId()), e.membres().stream())).distinct().toList());
		return liste.stream()
			.map(e -> new EquipeVue(e.id(), e.nom(), personne(e.chefId(), comptes.get(e.chefId())),
					e.membres().stream().map(m -> personne(m, comptes.get(m))).toList(), e.tailleMax(),
					e.membres().size() >= e.tailleMax(), e.creeLe()))
			.toList();
	}

	private static PersonneResume personne(UUID id, Compte compte) {
		return new PersonneResume(id, compte == null ? "Compte supprimé" : compte.nom(), compte == null ? null : compte.avatarUrl());
	}

	// ---------------------------------------------------------------- lecture du corps

	private static int limite(int limite) {
		if (limite < 1 || limite > 100) {
			throw ErreurHackathons.validation("limite", "hors_limites");
		}
		return limite;
	}

	/** Au plus 10 compétences, au format du contrat (`flutter`, `ux-design`), sans doublon. */
	private static List<String> competences(Corps c, Object brut) {
		if (brut == null) {
			return List.of();
		}
		if (!(brut instanceof List<?> elements)) {
			c.ajouter("competences", "type_invalide");
			return List.of();
		}
		if (elements.size() > 10) {
			c.ajouter("competences", "trop_nombreux");
		}
		List<String> competences = new ArrayList<>();
		for (Object o : elements) {
			if (o instanceof String s && s.length() <= 50 && COMPETENCE.matcher(s).matches()) {
				if (!competences.contains(s)) {
					competences.add(s);
				}
			}
			else {
				c.ajouter("competences", "format_invalide");
				break;
			}
		}
		return competences;
	}

}
