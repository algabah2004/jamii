package dev.jamii.api.reseau;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.jamii.api.auth.Annuaire;
import dev.jamii.api.auth.Compte;
import dev.jamii.api.auth.UtilisateurConnecte;
import dev.jamii.api.profils.ProfilsPublies;

/** Les routes de contracts/reseau.yaml déjà utilisées par le front : recommandations, messages et pastilles. */
@RestController
class ReseauController {

	record PersonneResume(UUID id, String nom, String avatarUrl) {
	}

	record Personne(UUID id, String nom, String avatarUrl, String ville) {
	}

	record CercleResume(UUID id, String nom, String ville, int taille, String statut, int nombreMembres,
			PersonneResume createur, Instant creeLe) {
	}

	record CercleRecommande(CercleResume cercle, List<String> raisons, List<String> competences) {
	}

	record ProfilResume(UUID utilisateurId, String nom, String avatarUrl, String pays, String ville, String disponibilite,
			Integer heuresParSemaine, List<String> competences, int projetsEnLigne) {
	}

	record ProfilRecommande(ProfilResume profil, List<String> raisons, List<String> competences, boolean invite,
			String lien) {
	}

	record Liste<T>(List<T> elements) {
	}

	record ProfilsRecommandes(List<String> competencesManquantes, List<ProfilRecommande> elements) {
	}

	record NouveauMessage(String texte, UUID cercleId) {
	}

	record Invitation(CercleResume cercle) {
	}

	record Message(UUID id, boolean deMoi, String texte, Invitation invitation, Instant envoyeLe, Instant luLe) {
	}

	record Conversation(Personne personne, Message dernierMessage, int nonLus) {
	}

	record Page<T>(List<T> elements, String curseurSuivant) {
	}

	record Fil(Personne personne, List<Message> elements, String curseurSuivant) {
	}

	record Activite(int notificationsNonLues, int messagesNonLus, int demandesContact) {
	}

	private final ProfilsPublies profils;

	private final LectureCercles cercles;

	private final Messagerie messagerie;

	private final Annuaire annuaire;

	ReseauController(ProfilsPublies profils, LectureCercles cercles, Messagerie messagerie, Annuaire annuaire) {
		this.profils = profils;
		this.cercles = cercles;
		this.messagerie = messagerie;
		this.annuaire = annuaire;
	}

	// ---------------------------------------------------------------- recommandations

	@GetMapping("/api/recommandations/cercles")
	Liste<CercleRecommande> cerclesRecommandes(@AuthenticationPrincipal UtilisateurConnecte moi,
			@RequestParam(defaultValue = "6") String limite) {
		int n = limite(limite, 20);
		var monProfil = profils.publie(moi.id());
		if (monProfil.isEmpty()) {
			return new Liste<>(List.of());
		}
		var ouverts = cercles.ouvertsSans(moi.id());
		var competences = profils.competences(membres(ouverts));
		var recos = Recommandations.cerclesPour(monProfil.get(), ouverts, competences, n);
		Map<UUID, Compte> comptes = annuaire.lire(recos.stream().map(r -> r.cercle().createurId()).toList());
		return new Liste<>(recos.stream()
			.map(r -> new CercleRecommande(resume(r.cercle(), comptes), r.raisons(), r.competences()))
			.toList());
	}

	@GetMapping("/api/recommandations/profils")
	ProfilsRecommandes profilsRecommandes(@AuthenticationPrincipal UtilisateurConnecte moi,
			@RequestParam(required = false) String cercleId, @RequestParam(defaultValue = "6") String limite) {
		int n = limite(limite, 20);
		var cercle = cercles.lire(uuid(cercleId, "cercleId")).orElseThrow(ErreurReseau::introuvable);
		if (!cercle.membres().contains(moi.id())) {
			throw ErreurReseau.interdit();
		}
		if (!cercle.accueille()) {
			return new ProfilsRecommandes(List.of(), List.of());
		}
		var competences = profils.competences(cercle.membres());
		var candidats = profils.disponibles(cercle.membres());
		var recos = Recommandations.profilsPour(cercle, competences, candidats, n);
		Set<UUID> invites = new HashSet<>(messagerie.invites(cercle.id()));
		Map<UUID, Compte> comptes = annuaire.lire(recos.stream().map(r -> r.profil().utilisateurId()).toList());
		List<ProfilRecommande> elements = recos.stream().filter(r -> comptes.containsKey(r.profil().utilisateurId())).map(r -> {
			var p = r.profil();
			var compte = comptes.get(p.utilisateurId());
			return new ProfilRecommande(new ProfilResume(p.utilisateurId(), compte.nom(), compte.avatarUrl(), p.pays(),
					p.ville(), p.disponibilite(), p.heuresParSemaine(), p.competences(), 0), r.raisons(), r.competences(),
					invites.contains(p.utilisateurId()), "aucun");
		}).toList();
		return new ProfilsRecommandes(Recommandations.manquantes(cercle, competences, candidats), elements);
	}

	// ---------------------------------------------------------------- messages

	@GetMapping("/api/conversations")
	Page<Conversation> conversations(@AuthenticationPrincipal UtilisateurConnecte moi,
			@RequestParam(defaultValue = "20") String limite, @RequestParam(required = false) String curseur) {
		var page = messagerie.conversations(moi.id(), limite(limite, 100), curseur);
		Map<UUID, Compte> comptes = annuaire.lire(page.elements().stream().map(Messagerie.Fil::autre).toList());
		var invitations = invitations(page.elements().stream().map(Messagerie.Fil::dernier).toList());
		return new Page<>(page.elements()
			.stream()
			.filter(f -> comptes.containsKey(f.autre()))
			.map(f -> new Conversation(personne(comptes.get(f.autre())), message(f.dernier(), moi.id(), invitations),
					f.nonLus()))
			.toList(), page.curseurSuivant());
	}

	@GetMapping("/api/conversations/{personneId}/messages")
	Fil lireMessages(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable String personneId,
			@RequestParam(defaultValue = "20") String limite, @RequestParam(required = false) String curseur) {
		UUID autre = personne(personneId);
		Compte compte = annuaire.lire(autre).orElseThrow(ErreurReseau::introuvable);
		var page = messagerie.lire(moi.id(), autre, limite(limite, 100), curseur);
		var invitations = invitations(page.elements());
		return new Fil(personne(compte), page.elements().stream().map(m -> message(m, moi.id(), invitations)).toList(),
				page.curseurSuivant());
	}

	@PostMapping("/api/conversations/{personneId}/messages")
	ResponseEntity<Message> envoyer(@AuthenticationPrincipal UtilisateurConnecte moi, @PathVariable String personneId,
			@RequestBody(required = false) NouveauMessage corps) {
		if (corps == null) {
			throw ErreurReseau.validation("texte", "obligatoire");
		}
		var ligne = messagerie.envoyer(moi.id(), personne(personneId), corps.texte(), corps.cercleId());
		return ResponseEntity.status(HttpStatus.CREATED).body(message(ligne, moi.id(), invitations(List.of(ligne))));
	}

	@GetMapping("/api/me/activite")
	Activite activite(@AuthenticationPrincipal UtilisateurConnecte moi) {
		// Les notifications et les demandes de contact arrivent avec l'étape 5 (docs/priorites.md) : 0 d'ici là.
		return new Activite(0, messagerie.nonLus(moi.id()), 0);
	}

	// ---------------------------------------------------------------- aides

	private Map<UUID, CercleResume> invitations(Collection<Messagerie.Ligne> lignes) {
		var lus = cercles.lire(lignes.stream().map(Messagerie.Ligne::cercleId).filter(Objects::nonNull).distinct().toList());
		Map<UUID, Compte> comptes = annuaire.lire(lus.values().stream().map(LectureCercles.CercleLu::createurId).toList());
		Map<UUID, CercleResume> parId = new java.util.HashMap<>();
		lus.forEach((id, c) -> parId.put(id, resume(c, comptes)));
		return parId;
	}

	/** Un cercle supprimé depuis l'invitation : le message reste, sans la carte. */
	private static Message message(Messagerie.Ligne l, UUID moi, Map<UUID, CercleResume> invitations) {
		CercleResume cercle = l.cercleId() == null ? null : invitations.get(l.cercleId());
		return new Message(l.id(), l.expediteurId().equals(moi), l.texte(), cercle == null ? null : new Invitation(cercle),
				l.envoyeLe(), l.luLe());
	}

	private static CercleResume resume(LectureCercles.CercleLu c, Map<UUID, Compte> comptes) {
		Compte createur = comptes.get(c.createurId());
		return new CercleResume(c.id(), c.nom(), c.ville(), c.taille(), c.statut(), c.membres().size(),
				createur == null ? new PersonneResume(c.createurId(), "", null)
						: new PersonneResume(createur.id(), createur.nom(), createur.avatarUrl()),
				c.creeLe());
	}

	/** Le réseau ne connaît pas la ville d'une personne sans profil publié : `null` alors. */
	private Personne personne(Compte compte) {
		String ville = profils.publie(compte.id()).map(ProfilsPublies.Resume::ville).orElse(null);
		return new Personne(compte.id(), compte.nom(), compte.avatarUrl(), ville);
	}

	private static List<UUID> membres(List<LectureCercles.CercleLu> liste) {
		List<UUID> ids = new ArrayList<>();
		liste.forEach(c -> ids.addAll(c.membres()));
		return ids.stream().distinct().toList();
	}

	private static int limite(String texte, int max) {
		try {
			int n = Integer.parseInt(texte);
			if (n >= 1 && n <= max) {
				return n;
			}
		}
		catch (NumberFormatException e) {
			// signalé juste en dessous
		}
		throw ErreurReseau.validation("limite", "hors_limites");
	}

	private static UUID uuid(String texte, String champ) {
		if (texte == null) {
			throw ErreurReseau.validation(champ, "obligatoire");
		}
		try {
			return UUID.fromString(texte);
		}
		catch (IllegalArgumentException e) {
			throw ErreurReseau.validation(champ, "format_invalide");
		}
	}

	/** Un identifiant de personne invalide désigne une personne qui n'existe pas. */
	private static UUID personne(String texte) {
		try {
			return UUID.fromString(texte);
		}
		catch (IllegalArgumentException e) {
			throw ErreurReseau.introuvable();
		}
	}

	@ExceptionHandler(ErreurReseau.class)
	ResponseEntity<ProblemDetail> erreur(ErreurReseau e) {
		ProblemDetail probleme = ProblemDetail.forStatus(e.statut);
		probleme.setType(URI.create("urn:jamii:erreur:" + e.code));
		probleme.setTitle(e.getMessage());
		probleme.setProperty("code", e.code);
		if (e.erreurs != null) {
			probleme.setProperty("erreurs", e.erreurs);
		}
		var reponse = ResponseEntity.status(e.statut);
		if (e.attenteSecondes > 0) {
			reponse = reponse.header(HttpHeaders.RETRY_AFTER, String.valueOf(e.attenteSecondes));
		}
		return reponse.body(probleme);
	}

}
