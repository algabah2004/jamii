package dev.jamii.api.communautes;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.jamii.api.auth.Annuaire;
import dev.jamii.api.auth.Compte;
import dev.jamii.api.auth.UtilisateurConnecte;
import dev.jamii.api.reactions.Reactions;

/**
 * Les salons d'une communauté (contracts/communautes.yaml v0.7, #155). Lire : tout utilisateur connecté. Écrire, lancer
 * un appel et recevoir son adresse : les membres seulement. Terminer un appel : celui qui l'a lancé, ou un modérateur.
 * Réagir et répondre : les membres. Corriger son message : son auteur, 15 minutes. Supprimer : son auteur, ou un
 * modérateur de la communauté (#157).
 */
@RestController
class SalonsController {

	record AppelSalon(UUID id, CommunautesController.PersonneResume lancePar, Instant lanceLe, String url) {
	}

	record Salon(UUID id, String slug, String nom, String sujet, AppelSalon appel) {
	}

	record AppelDuMessage(UUID id, boolean enCours) {
	}

	record MessageSalon(UUID id, long numero, CommunautesController.PersonneResume auteur, String texte, Instant envoyeLe,
			AppelDuMessage appel, List<dev.jamii.api.fichiers.Fichiers.Piece> piecesJointes, long version, Citation reponseA,
			Instant modifieLe, String supprime, List<ReactionVue> reactions) {
	}

	/** Le message auquel on répond : qui l'a écrit et son début. */
	record Citation(UUID id, CommunautesController.PersonneResume auteur, String texte, boolean supprime) {
	}

	/** 👍 3 : l'emoji, combien, si j'en suis, et les prénoms au survol (10 au plus, « moi » pour moi). */
	record ReactionVue(String emoji, int nombre, boolean deMoi, List<String> noms) {
	}

	record Modification(String texte) {
	}

	record NouvelleReaction(String emoji, Boolean active) {
	}

	private record Contexte(Map<UUID, List<ReactionVue>> reactions, Map<UUID, Salons.Message> cites) {
	}

	private static final int CITATION_MAX = 140;

	record Liste<T>(List<T> elements) {
	}

	record NouveauMessage(String texte, List<UUID> pieces, UUID reponseA) {
	}

	private final GestionCommunautes communautes;

	private final Salons salons;

	private final Annuaire annuaire;

	private final String appels;

	private final dev.jamii.api.fichiers.Fichiers fichiers;

	private Reactions reactions;

	@org.springframework.beans.factory.annotation.Autowired
	void reactions(Reactions reactions) {
		this.reactions = reactions;
	}

	SalonsController(GestionCommunautes communautes, Salons salons, Annuaire annuaire, dev.jamii.api.fichiers.Fichiers fichiers,
			@Value("${jamii.appels.url:https://meet.jit.si}") String appels) {
		this.fichiers = fichiers;
		this.communautes = communautes;
		this.salons = salons;
		this.annuaire = annuaire;
		this.appels = appels.endsWith("/") ? appels.substring(0, appels.length() - 1) : appels;
	}

	@GetMapping("/api/communautes/{slug}/salons")
	ResponseEntity<?> lister(@PathVariable String slug, @AuthenticationPrincipal UtilisateurConnecte moi) {
		var fiche = communautes.lire(slug, moi.id(), Instant.now());
		if (fiche.isEmpty()) {
			return Problemes.introuvable();
		}
		boolean membre = fiche.get().membre();
		Instant maintenant = Instant.now();
		var liste = salons.lister(fiche.get().id());
		var enCours = liste.stream().map(s -> salons.appelEnCours(s.id(), maintenant)).toList();
		Map<UUID, Compte> comptes = annuaire.lire(enCours.stream().flatMap(java.util.Optional::stream)
			.map(Salons.Appel::lancePar).filter(id -> id != null).distinct().toList());
		List<Salon> reponse = new java.util.ArrayList<>();
		for (int i = 0; i < liste.size(); i++) {
			var s = liste.get(i);
			reponse.add(new Salon(s.id(), s.slug(), s.nom(), s.sujet(), enCours.get(i).map(a -> appel(a, comptes, membre)).orElse(null)));
		}
		return ResponseEntity.ok(new Liste<>(reponse));
	}

	@GetMapping("/api/communautes/{slug}/salons/{salon}/messages")
	ResponseEntity<?> lire(@PathVariable String slug, @PathVariable String salon, @RequestParam(required = false) String apres,
			@RequestParam(defaultValue = "50") String limite, @AuthenticationPrincipal UtilisateurConnecte moi) {
		Long depuis = entier(apres, 0, Long.MAX_VALUE);
		Long taille = entier(limite, 1, 100);
		if ((apres != null && depuis == null) || taille == null) {
			return Problemes.validation(taille == null ? "limite" : "apres", "valeur_invalide");
		}
		var fiche = communautes.lire(slug, moi.id(), Instant.now());
		var leSalon = fiche.flatMap(f -> salons.lire(f.id(), salon));
		if (leSalon.isEmpty()) {
			return Problemes.introuvable();
		}
		Instant maintenant = Instant.now();
		var messages = salons.messages(leSalon.get().id(), depuis, taille.intValue(), maintenant);
		Set<UUID> ids = new HashSet<>(messages.stream().map(Salons.Message::auteurId).toList());
		Contexte contexte = contexte(leSalon.get().id(), messages, moi.id(), maintenant, ids);
		Map<UUID, Compte> comptes = annuaire.lire(ids);
		return ResponseEntity.ok()
			.cacheControl(CacheControl.noStore())
			.body(new Liste<>(messages.stream().map(m -> message(m, comptes, contexte, moi.id())).toList()));
	}

	@PostMapping("/api/communautes/{slug}/salons/{salon}/messages")
	ResponseEntity<?> ecrire(@PathVariable String slug, @PathVariable String salon, @RequestBody NouveauMessage demande,
			@AuthenticationPrincipal UtilisateurConnecte moi) {
		String texte = demande.texte() == null ? "" : demande.texte().strip();
		boolean avecPieces = demande.pieces() != null && !demande.pieces().isEmpty();
		if ((texte.isEmpty() && !avecPieces) || texte.length() > 2000) {
			return Problemes.validation("texte", "longueur_invalide");
		}
		Instant maintenant = Instant.now();
		var fiche = communautes.lire(slug, moi.id(), maintenant);
		var leSalon = fiche.flatMap(f -> salons.lire(f.id(), salon));
		if (leSalon.isEmpty()) {
			return Problemes.introuvable();
		}
		if (!fiche.get().membre()) {
			return Problemes.interdit("pas_membre", "Rejoignez la communauté pour écrire dans ses salons");
		}
		if (demande.reponseA() != null && salons.message(leSalon.get().id(), demande.reponseA(), maintenant).isEmpty()) {
			return Problemes.validation("reponseA", "inconnu");
		}
		long attente = salons.attenteAvantMessage(moi.id(), maintenant);
		if (attente > 0) {
			return Problemes.tropDeRequetes(attente, "Trop de messages en peu de temps");
		}
		List<UUID> pieces;
		try {
			pieces = fichiers.verifierPieces(demande.pieces(), moi.id());
		}
		catch (dev.jamii.api.fichiers.ErreurFichier e) {
			return dev.jamii.api.fichiers.FichiersController.probleme(e);
		}
		var message = salons.ecrire(leSalon.get().id(), moi.id(), texte, pieces, maintenant);
		if (demande.reponseA() != null) {
			salons.repondA(message.id(), demande.reponseA());
		}
		return ResponseEntity.status(HttpStatus.CREATED).body(relire(leSalon.get().id(), message.id(), moi.id(), maintenant));
	}

	// ---------------------------------------------------------------- corriger, supprimer, réagir (#157)

	/** Le salon et ma fiche dans la communauté, ou la réponse d'erreur. */
	private record Lieu(GestionCommunautes.Fiche fiche, Salons.Salon salon, Salons.Message message) {
	}

	private Object trouver(String slug, String salon, UUID messageId, UUID moi, Instant maintenant) {
		var fiche = communautes.lire(slug, moi, maintenant);
		var leSalon = fiche.flatMap(f -> salons.lire(f.id(), salon));
		var message = leSalon.flatMap(s -> salons.message(s.id(), messageId, maintenant));
		if (message.isEmpty()) {
			return Problemes.introuvable();
		}
		if (!fiche.get().membre()) {
			return Problemes.interdit("pas_membre", "Rejoignez la communauté pour agir sur ses messages");
		}
		return new Lieu(fiche.get(), leSalon.get(), message.get());
	}

	@PatchMapping("/api/communautes/{slug}/salons/{salon}/messages/{messageId}")
	ResponseEntity<?> modifier(@PathVariable String slug, @PathVariable String salon, @PathVariable UUID messageId,
			@RequestBody(required = false) Modification corps, @AuthenticationPrincipal UtilisateurConnecte moi) {
		Instant maintenant = Instant.now();
		Object trouve = trouver(slug, salon, messageId, moi.id(), maintenant);
		if (!(trouve instanceof Lieu lieu)) {
			return (ResponseEntity<?>) trouve;
		}
		var m = lieu.message();
		if (m.supprimeLe() != null) {
			return Problemes.conflit("message_supprime", "Ce message a été supprimé");
		}
		if (!moi.id().equals(m.auteurId()) || m.appelId() != null) {
			return Problemes.conflit("non_modifiable", "On ne corrige que ses propres messages");
		}
		if (m.envoyeLe().plus(Salons.DELAI_MODIFICATION).isBefore(maintenant)) {
			return Problemes.conflit("delai_depasse", "Un message se corrige pendant 15 minutes");
		}
		String texte = corps == null || corps.texte() == null ? "" : corps.texte().strip();
		if ((texte.isEmpty() && m.pieces().isEmpty()) || texte.length() > 2000) {
			return Problemes.validation("texte", "longueur_invalide");
		}
		salons.modifier(messageId, texte, maintenant);
		return ResponseEntity.ok(relire(lieu.salon().id(), messageId, moi.id(), maintenant));
	}

	/** Son auteur le supprime pour tout le monde ; un modérateur supprime aussi le message d'un autre (abus, spam). */
	@DeleteMapping("/api/communautes/{slug}/salons/{salon}/messages/{messageId}")
	ResponseEntity<?> supprimer(@PathVariable String slug, @PathVariable String salon, @PathVariable UUID messageId,
			@AuthenticationPrincipal UtilisateurConnecte moi) {
		Instant maintenant = Instant.now();
		Object trouve = trouver(slug, salon, messageId, moi.id(), maintenant);
		if (!(trouve instanceof Lieu lieu)) {
			return (ResponseEntity<?>) trouve;
		}
		var m = lieu.message();
		if (m.supprimeLe() == null) {
			boolean auteur = moi.id().equals(m.auteurId());
			if ((!auteur && !lieu.fiche().moderateur()) || m.appelId() != null) {
				return Problemes.interdit("interdit", "On ne supprime que ses propres messages");
			}
			salons.supprimer(messageId, moi.id(), maintenant);
			reactions.effacer(Reactions.Table.SALON, messageId);
			fichiers.supprimerOrphelins(m.pieces(), m.auteurId());
		}
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/api/communautes/{slug}/salons/{salon}/messages/{messageId}/reactions")
	ResponseEntity<?> reagir(@PathVariable String slug, @PathVariable String salon, @PathVariable UUID messageId,
			@RequestBody(required = false) NouvelleReaction corps, @AuthenticationPrincipal UtilisateurConnecte moi) {
		if (corps == null || corps.active() == null) {
			return Problemes.validation(corps == null ? "emoji" : "active", "obligatoire");
		}
		if (!Reactions.estUnEmoji(corps.emoji())) {
			return Problemes.validation("emoji", "format_invalide");
		}
		Instant maintenant = Instant.now();
		Object trouve = trouver(slug, salon, messageId, moi.id(), maintenant);
		if (!(trouve instanceof Lieu lieu)) {
			return (ResponseEntity<?>) trouve;
		}
		if (lieu.message().supprimeLe() != null) {
			return Problemes.conflit("message_supprime", "On ne réagit pas à ce message");
		}
		if (reactions.changer(Reactions.Table.SALON, messageId, moi.id(), corps.emoji(), corps.active(), maintenant) == Reactions.Resultat.TROP) {
			return Problemes.conflit("trop_de_reactions", "Dix réactions au plus par message");
		}
		salons.toucher(messageId);
		return ResponseEntity.ok(relire(lieu.salon().id(), messageId, moi.id(), maintenant));
	}

	private MessageSalon relire(UUID salonId, UUID messageId, UUID moi, Instant maintenant) {
		var m = salons.message(salonId, messageId, maintenant).orElseThrow();
		Set<UUID> ids = new HashSet<>(java.util.Arrays.asList(m.auteurId(), m.supprimePar()));
		Contexte contexte = contexte(salonId, List.of(m), moi, maintenant, ids);
		ids.remove(null);
		return message(m, annuaire.lire(ids), contexte, moi);
	}

	/** Les réactions de ces messages et les messages qu'ils citent ; ajoute à `ids` les personnes à lire. */
	private Contexte contexte(UUID salonId, List<Salons.Message> messages, UUID moi, Instant maintenant, Set<UUID> ids) {
		var brutes = reactions.pour(Reactions.Table.SALON, messages.stream().map(Salons.Message::id).toList(), moi);
		Map<UUID, Salons.Message> cites = new HashMap<>();
		for (var m : messages) {
			ids.add(m.supprimePar());
			if (m.reponseA() != null && !cites.containsKey(m.reponseA())) {
				salons.message(salonId, m.reponseA(), maintenant).ifPresent(c -> {
					cites.put(c.id(), c);
					ids.add(c.auteurId());
				});
			}
		}
		brutes.values().forEach(l -> l.forEach(r -> ids.addAll(r.personnes())));
		ids.remove(null);
		Map<UUID, Compte> comptes = annuaire.lire(ids);
		Map<UUID, List<ReactionVue>> vues = new HashMap<>();
		brutes.forEach((id, liste) -> vues.put(id, liste.stream().map(r -> new ReactionVue(r.emoji(), r.nombre(), r.deMoi(),
				r.personnes().stream().map(comptes::get).filter(Objects::nonNull)
					.map(c -> c.id().equals(moi) ? "moi" : c.nom()).toList())).toList()));
		return new Contexte(vues, cites);
	}

	/** Lancer un appel, ou rejoindre celui qui est en cours : un seul à la fois par salon (contrat v0.7). */
	@PostMapping("/api/communautes/{slug}/salons/{salon}/appel")
	@org.springframework.transaction.annotation.Transactional
	ResponseEntity<?> lancerAppel(@PathVariable String slug, @PathVariable String salon, @AuthenticationPrincipal UtilisateurConnecte moi) {
		Instant maintenant = Instant.now();
		var fiche = communautes.lire(slug, moi.id(), maintenant);
		var leSalon = fiche.flatMap(f -> salons.lire(f.id(), salon));
		if (leSalon.isEmpty()) {
			return Problemes.introuvable();
		}
		if (!fiche.get().membre()) {
			return Problemes.interdit("pas_membre", "Rejoignez la communauté pour lancer un appel");
		}
		var enCours = salons.appelEnCours(leSalon.get().id(), maintenant);
		if (enCours.isPresent()) {
			var a = enCours.get();
			return ResponseEntity.ok(appel(a, annuaire.lire(a.lancePar() == null ? List.of() : List.of(a.lancePar())), true));
		}
		long attente = salons.attenteAvantAppel(moi.id(), maintenant);
		if (attente > 0) {
			return Problemes.tropDeRequetes(attente, "Trop d'appels lancés en peu de temps");
		}
		var a = salons.lancer(leSalon.get().id(), slug, salon, moi.id(), "a lancé un appel", maintenant);
		return ResponseEntity.status(HttpStatus.CREATED).body(appel(a, annuaire.lire(List.of(moi.id())), true));
	}

	@DeleteMapping("/api/communautes/{slug}/salons/{salon}/appel")
	ResponseEntity<?> terminerAppel(@PathVariable String slug, @PathVariable String salon, @AuthenticationPrincipal UtilisateurConnecte moi) {
		Instant maintenant = Instant.now();
		var fiche = communautes.lire(slug, moi.id(), maintenant);
		var leSalon = fiche.flatMap(f -> salons.lire(f.id(), salon));
		var enCours = leSalon.flatMap(s -> salons.appelEnCours(s.id(), maintenant));
		if (enCours.isEmpty()) {
			return Problemes.introuvable();
		}
		if (!moi.id().equals(enCours.get().lancePar()) && !fiche.get().moderateur()) {
			return Problemes.interdit("pas_lanceur", "Seuls celui qui a lancé l'appel et les modérateurs le terminent");
		}
		salons.terminer(enCours.get().id(), maintenant);
		return ResponseEntity.noContent().build();
	}

	private AppelSalon appel(Salons.Appel a, Map<UUID, Compte> comptes, boolean membre) {
		return new AppelSalon(a.id(), VuesPublications.personne(a.lancePar(), comptes), a.lanceLe(),
				membre ? appels + "/" + a.salle() : null);
	}

	/**
	 * `supprime` : `null`, `auteur` (supprimé par son auteur) ou `moderation` (par un modérateur), pour que tout le
	 * monde sache pourquoi le message a disparu.
	 */
	private MessageSalon message(Salons.Message m, Map<UUID, Compte> comptes, Contexte contexte, UUID moi) {
		Citation citation = null;
		var cite = m.reponseA() == null ? null : contexte.cites().get(m.reponseA());
		if (cite != null) {
			String debut = cite.supprimeLe() != null || cite.texte() == null ? "" : cite.texte();
			if (debut.length() > CITATION_MAX) debut = debut.substring(0, CITATION_MAX - 1) + "…";
			citation = new Citation(cite.id(), VuesPublications.personne(cite.auteurId(), comptes), debut, cite.supprimeLe() != null);
		}
		String supprime = m.supprimeLe() == null ? null : Objects.equals(m.supprimePar(), m.auteurId()) || m.supprimePar() == null ? "auteur" : "moderation";
		return new MessageSalon(m.id(), m.numero(), VuesPublications.personne(m.auteurId(), comptes), supprime == null ? m.texte() : "",
				m.envoyeLe(), m.appelId() == null ? null : new AppelDuMessage(m.appelId(), m.appelEnCours()),
				supprime == null ? fichiers.decrire(m.pieces()) : List.of(), m.version(), citation, m.modifieLe(), supprime,
				supprime == null ? contexte.reactions().getOrDefault(m.id(), List.of()) : List.of());
	}

	private static Long entier(String valeur, long min, long max) {
		if (valeur == null) {
			return null;
		}
		try {
			long n = Long.parseLong(valeur);
			return n >= min && n <= max ? n : null;
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

}
