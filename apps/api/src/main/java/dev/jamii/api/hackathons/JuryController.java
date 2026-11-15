package dev.jamii.api.hackathons;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.jamii.api.auth.Annuaire;
import dev.jamii.api.auth.Compte;
import dev.jamii.api.auth.UtilisateurConnecte;

/** Le jury, les notes, le suivi de la notation et l'export CSV (contracts/hackathons.yaml, tag « jury »). Connexion requise. */
@RestController
@RequestMapping("/api/hackathons/{hackathonId}")
class JuryController {

	record PersonneResume(UUID id, String nom, String avatarUrl) {
	}

	record JureVue(PersonneResume personne, String groupe, int rendusNotes, int rendusATraiter) {
	}

	record PageRendus(List<RendusController.RenduVue> elements, String curseurSuivant) {
	}

	record NotationVue(List<JureVue> jures, List<Jury.Classe> classement) {
	}

	private final Jury jury;

	private final Rendus rendus;

	private final Annuaire annuaire;

	JuryController(Jury jury, Rendus rendus, Annuaire annuaire) {
		this.jury = jury;
		this.rendus = rendus;
		this.annuaire = annuaire;
	}

	@GetMapping("/jures")
	List<JureVue> jures(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable UUID hackathonId) {
		return vues(jury.lister(hackathonId, moi.id()));
	}

	@PostMapping("/jures")
	ResponseEntity<JureVue> inviter(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable UUID hackathonId,
			@RequestBody Map<String, Object> corps) {
		Corps c = new Corps(corps, Set.of("utilisateurId", "groupe"));
		UUID utilisateur = c.identifiant("utilisateurId", true);
		String groupe = c.texte("groupe", 1, 40, false);
		c.verifier();
		if (annuaire.lire(utilisateur).isEmpty()) {
			throw ErreurHackathons.validation("utilisateurId", "introuvable");
		}
		return ResponseEntity.status(HttpStatus.CREATED).body(vues(List.of(jury.inviter(hackathonId, moi.id(), utilisateur, groupe))).getFirst());
	}

	/** Les rendus visibles de la personne ; le curseur est l'identifiant du dernier rendu de la page. */
	@GetMapping("/rendus")
	PageRendus rendus(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable UUID hackathonId,
			@RequestParam(defaultValue = "20") int limite, @RequestParam(required = false) String curseur) {
		if (limite < 1 || limite > 100) {
			throw ErreurHackathons.validation("limite", "hors_limites");
		}
		List<UUID> ids = jury.rendusVisibles(hackathonId, moi.id());
		int debut = 0;
		if (curseur != null) {
			int i = ids.indexOf(identifiant(curseur));
			if (i < 0) {
				throw ErreurHackathons.validation("curseur", "invalide");
			}
			debut = i + 1;
		}
		List<UUID> page = ids.subList(Math.min(debut, ids.size()), Math.min(debut + limite, ids.size()));
		String suivant = debut + limite < ids.size() ? page.getLast().toString() : null;
		return new PageRendus(rendus.lire(page).stream().map(RendusController::vue).toList(), suivant);
	}

	/** Les notes que j'ai déjà données, en tant que juré. */
	@GetMapping("/mes-notes")
	List<Jury.NotesDuJure> mesNotes(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable UUID hackathonId) {
		return jury.mesNotes(hackathonId, moi.id());
	}

	@PutMapping("/rendus/{renduId}/notes")
	Jury.NotesDuJure noter(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable UUID hackathonId, @PathVariable UUID renduId,
			@RequestBody Map<String, Object> corps) {
		Corps c = new Corps(corps, Set.of("notes", "commentaire"));
		List<Map<String, Object>> objets = c.objets("notes", 1, 20, true);
		String commentaire = c.texte("commentaire", 1, 2000, false);
		List<Jury.Note> notes = new ArrayList<>();
		if (objets != null) {
			for (int i = 0; i < objets.size(); i++) {
				Corps n = c.sous(objets.get(i), Set.of("critereId", "valeur"), "notes[" + i + "].");
				UUID critere = n.identifiant("critereId", true);
				int valeur = n.entier("valeur", 1, 5, true);
				if (critere != null && valeur > 0) {
					notes.add(new Jury.Note(critere, valeur));
				}
			}
		}
		c.verifier();
		return jury.noter(hackathonId, renduId, moi.id(), notes, commentaire);
	}

	@GetMapping("/notation")
	NotationVue notation(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable UUID hackathonId) {
		Jury.Notation n = jury.suivre(hackathonId, moi.id());
		return new NotationVue(vues(n.jures()), n.classement());
	}

	@GetMapping(path = "/notes.csv", produces = "text/csv")
	ResponseEntity<String> exporter(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable UUID hackathonId) {
		List<Jury.LigneExport> lignes = jury.exporter(hackathonId, moi.id());
		Map<UUID, Compte> jures = annuaire.lire(lignes.stream().map(Jury.LigneExport::jure).distinct().toList());
		StringBuilder csv = new StringBuilder("equipe,rendu,jure,critere,valeur,commentaire\r\n");
		for (Jury.LigneExport l : lignes) {
			Compte jure = jures.get(l.jure());
			csv.append(String.join(",", cellule(l.equipe()), cellule(l.rendu()), cellule(jure == null ? "Compte supprimé" : jure.nom()),
					cellule(l.critere()), String.valueOf(l.valeur()), cellule(l.commentaire()))).append("\r\n");
		}
		return ResponseEntity.ok()
			.contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
			.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"notes.csv\"")
			.cacheControl(CacheControl.noStore())
			.body(csv.toString());
	}

	// ---------------------------------------------------------------- aides

	/**
	 * Une cellule CSV (RFC 4180) : entre guillemets si besoin, guillemets doublés. Une cellule qui commence par `=`, `+`,
	 * `-` ou `@` est préfixée d'une apostrophe, pour qu'un tableur ne l'exécute pas comme une formule.
	 */
	static String cellule(String valeur) {
		if (valeur == null) {
			return "";
		}
		String v = !valeur.isEmpty() && "=+-@\t\r".indexOf(valeur.charAt(0)) >= 0 ? "'" + valeur : valeur;
		boolean citer = v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r") || v.startsWith("'");
		return citer ? "\"" + v.replace("\"", "\"\"") + "\"" : v;
	}

	private List<JureVue> vues(List<Jury.Jure> jures) {
		Map<UUID, Compte> comptes = annuaire.lire(jures.stream().map(Jury.Jure::utilisateurId).toList());
		return jures.stream().map(j -> {
			Compte c = comptes.get(j.utilisateurId());
			return new JureVue(new PersonneResume(j.utilisateurId(), c == null ? "Compte supprimé" : c.nom(), c == null ? null : c.avatarUrl()),
					j.groupe(), j.rendusNotes(), j.rendusATraiter());
		}).toList();
	}

	private static UUID identifiant(String texte) {
		try {
			return UUID.fromString(texte);
		}
		catch (IllegalArgumentException e) {
			throw ErreurHackathons.validation("curseur", "invalide");
		}
	}

}
