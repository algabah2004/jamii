package dev.jamii.api.cercles;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
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
import dev.jamii.api.cercles.CerclesController.PersonneResume;
import dev.jamii.api.fichiers.ErreurFichier;
import dev.jamii.api.fichiers.Fichiers;
import dev.jamii.api.fichiers.FichiersController;
import dev.jamii.api.reactions.Reactions;

/**
 * Le salon du cercle (contracts/cercles.yaml v0.2, #157) : réservé aux membres. On y écrit, on y fait son point du
 * jour, on y demande un coup de main, on y lance un appel ; Jamii y raconte les étapes du cercle.
 */
@RestController
class SalonCercleController {

	record Point(String fait, String prevu, String bloque) {
	}

	record Evenement(String code, Integer tour, PersonneResume personne) {
	}

	record CoupDeMain(Instant resoluLe, PersonneResume resoluPar) {
	}

	record AppelDuMessage(UUID id, boolean enCours) {
	}

	record MessageCercle(UUID id, long numero, String type, PersonneResume auteur, String texte, Point point,
			Evenement evenement, CoupDeMain coupDeMain, AppelDuMessage appel, Instant envoyeLe, List<Fichiers.Piece> piecesJointes,
			long version, Citation reponseA, Instant modifieLe, boolean supprime, List<ReactionVue> reactions) {
	}

	/** Le message auquel on répond (#157) : qui l'a écrit et son début. */
	record Citation(UUID id, PersonneResume auteur, String texte, boolean supprime) {
	}

	/** 👍 3 : l'emoji, combien, si j'en suis, et les prénoms au survol (10 au plus, « moi » pour moi). */
	record ReactionVue(String emoji, int nombre, boolean deMoi, List<String> noms) {
	}

	record Modification(String texte) {
	}

	record NouvelleReaction(String emoji, Boolean active) {
	}

	/** Ce qu'il faut en plus pour montrer des messages : les réactions et les messages cités. */
	private record Contexte(Map<UUID, List<ReactionVue>> reactions, Map<UUID, SalonCercle.Message> cites) {
		static final Contexte VIDE = new Contexte(Map.of(), Map.of());
	}

	private static final int CITATION_MAX = 140;

	/** `presents` : combien de membres sont dans l'appel WebRTC (#157). `url` : l'ancienne salle Jitsi, plus utilisée. */
	record AppelCercle(UUID id, PersonneResume lancePar, Instant lanceLe, String url, int presents) {
	}

	record Salon(List<MessageCercle> elements, List<MessageCercle> coupsDeMain, List<MessageCercle> decisions, List<UUID> pointsDuJour,
			AppelCercle appel) {
	}

	record NouveauMessage(String type, String texte, Point point, List<UUID> pieces, UUID reponseA) {
	}

	private static final int TEXTE_MAX = 2000;

	private static final int POINT_MAX = 500;

	private final GestionCercles gestion;

	private final SalonCercle salon;

	private final Annuaire annuaire;

	private final Fichiers fichiers;

	private final String appels;

	private SalleAppelCercle salleAppel;

	@org.springframework.beans.factory.annotation.Autowired
	void salleAppel(SalleAppelCercle salleAppel) {
		this.salleAppel = salleAppel;
	}

	private Reactions reactions;

	@org.springframework.beans.factory.annotation.Autowired
	void reactions(Reactions reactions) {
		this.reactions = reactions;
	}

	SalonCercleController(GestionCercles gestion, SalonCercle salon, Annuaire annuaire, Fichiers fichiers,
			@Value("${jamii.appels.url:https://meet.jit.si}") String appels) {
		this.fichiers = fichiers;
		this.gestion = gestion;
		this.salon = salon;
		this.annuaire = annuaire;
		this.appels = appels.endsWith("/") ? appels.substring(0, appels.length() - 1) : appels;
	}

	@GetMapping("/api/cercles/{cercleId}/salon")
	ResponseEntity<Salon> lire(@PathVariable UUID cercleId, @RequestParam(required = false) Long apres,
			@RequestParam(defaultValue = "50") int limite, @AuthenticationPrincipal UtilisateurConnecte moi) {
		if (limite < 1 || limite > 100) {
			throw validation("limite", "hors_limites");
		}
		if (apres != null && apres < 0) {
			throw validation("apres", "valeur_invalide");
		}
		membre(cercleId, moi.id());
		Instant maintenant = Instant.now();
		var messages = salon.messages(cercleId, apres, limite, maintenant);
		var coups = salon.coupsDeMainOuverts(cercleId, maintenant);
		var decisions = salon.decisions(cercleId, maintenant);
		// Un appel dont tout le monde est parti (onglets fermés) se termine ici : le bandeau ne promet pas un appel vide.
		var appel = salon.appelEnCours(cercleId, maintenant)
			.filter(a -> salleAppel.presents(a.id(), a.lanceLe(), maintenant) >= 0 && salon.appelEnCours(cercleId, maintenant).isPresent());
		Set<UUID> ids = new HashSet<>();
		for (var m : messages) {
			personnes(m, ids);
		}
		coups.forEach(m -> personnes(m, ids));
		decisions.forEach(m -> personnes(m, ids));
		appel.map(SalonCercle.Appel::lancePar).ifPresent(ids::add);
		ids.remove(null);
		Contexte contexte = contexte(cercleId, messages, moi.id(), maintenant, ids);
		Map<UUID, Compte> comptes = annuaire.lire(ids);
		return ResponseEntity.ok()
			.cacheControl(CacheControl.noStore())
			.body(new Salon(messages.stream().map(m -> vue(m, comptes, contexte, moi.id())).toList(), coups.stream().map(m -> vue(m, comptes)).toList(),
					decisions.stream().map(m -> vue(m, comptes)).toList(), salon.pointsDuJour(cercleId, maintenant), appel.map(a -> vue(a, comptes)).orElse(null)));
	}

	@PostMapping("/api/cercles/{cercleId}/salon/messages")
	ResponseEntity<MessageCercle> ecrire(@PathVariable UUID cercleId, @RequestBody NouveauMessage demande,
			@AuthenticationPrincipal UtilisateurConnecte moi) {
		String type = demande.type() == null ? "texte" : demande.type();
		Instant maintenant = Instant.now();
		SalonCercle.Message message;
		switch (type) {
			case "texte", "coup_de_main", "decision" -> {
				String texte = propre(demande.texte());
				var cercle = membre(cercleId, moi.id());
				List<UUID> pieces = fichiers.verifierPiecesSalonCercle(demande.pieces(), moi.id(), cercleId);
				// Un message peut n'être qu'une pièce jointe ; une demande d'aide ou une décision s'écrit toujours.
				boolean texteRequis = !"texte".equals(type) || pieces.isEmpty();
				if ((texte == null && texteRequis) || (texte != null && texte.length() > TEXTE_MAX)) {
					throw validation("texte", "longueur_invalide");
				}
				if ("decision".equals(type) && !moi.id().equals(chefDeProjet(cercle))) {
					throw new ErreurCercle(HttpStatus.FORBIDDEN, "pas_chef_de_projet", "Les décisions du tour sont consignées par son chef de projet");
				}
				attendre(moi.id(), maintenant);
				if (demande.reponseA() != null && salon.message(cercleId, demande.reponseA(), maintenant)
					.filter(m -> !"evenement".equals(m.type())).isEmpty()) {
					throw validation("reponseA", "inconnu");
				}
				message = switch (type) {
					case "texte" -> salon.ecrire(cercleId, moi.id(), texte, pieces, maintenant);
					case "decision" -> salon.decision(cercleId, moi.id(), texte, pieces, maintenant);
					default -> salon.coupDeMain(cercleId, moi.id(), texte, pieces, maintenant);
				};
			}
			case "point" -> {
				Point p = demande.point();
				String fait = p == null ? null : propre(p.fait());
				String prevu = p == null ? null : propre(p.prevu());
				String bloque = p == null ? null : propre(p.bloque());
				if (fait == null || fait.length() > POINT_MAX) {
					throw validation("point.fait", "longueur_invalide");
				}
				if (prevu == null || prevu.length() > POINT_MAX) {
					throw validation("point.prevu", "longueur_invalide");
				}
				if (bloque != null && bloque.length() > POINT_MAX) {
					throw validation("point.bloque", "longueur_invalide");
				}
				membre(cercleId, moi.id());
				if (salon.pointDejaFait(cercleId, moi.id(), maintenant)) {
					throw ErreurCercle.conflit("point_deja_fait", "Tu as déjà fait ton point aujourd'hui");
				}
				attendre(moi.id(), maintenant);
				message = salon.point(cercleId, moi.id(), fait, prevu, bloque, maintenant);
			}
			default -> throw validation("type", "valeur_invalide");
		}
		if (demande.reponseA() != null && !"point".equals(type)) {
			salon.repondA(message.id(), demande.reponseA());
		}
		var ecrit = salon.message(cercleId, message.id(), maintenant).orElse(message);
		Set<UUID> ids = new HashSet<>(List.of(moi.id()));
		Contexte contexte = contexte(cercleId, List.of(ecrit), moi.id(), maintenant, ids);
		return ResponseEntity.status(HttpStatus.CREATED).body(vue(ecrit, annuaire.lire(ids), contexte, moi.id()));
	}

	// ---------------------------------------------------------------- modifier, supprimer, réagir (#157)

	/** Corriger son message texte pendant 15 minutes. */
	@PatchMapping("/api/cercles/{cercleId}/salon/messages/{messageId}")
	MessageCercle modifier(@PathVariable UUID cercleId, @PathVariable UUID messageId, @RequestBody(required = false) Modification corps,
			@AuthenticationPrincipal UtilisateurConnecte moi) {
		membre(cercleId, moi.id());
		Instant maintenant = Instant.now();
		var m = salon.message(cercleId, messageId, maintenant).orElseThrow(ErreurCercle::introuvable);
		if (m.supprimeLe() != null) {
			throw ErreurCercle.conflit("message_supprime", "Ce message a été supprimé");
		}
		if (!moi.id().equals(m.auteurId()) || !"texte".equals(m.type())) {
			throw ErreurCercle.conflit("non_modifiable", "On ne corrige que ses propres messages");
		}
		if (m.envoyeLe().plus(SalonCercle.DELAI_MODIFICATION).isBefore(maintenant)) {
			throw ErreurCercle.conflit("delai_depasse", "Un message se corrige pendant 15 minutes");
		}
		String texte = propre(corps == null ? null : corps.texte());
		if ((texte == null && m.pieces().isEmpty()) || (texte != null && texte.length() > TEXTE_MAX)) {
			throw validation("texte", "longueur_invalide");
		}
		salon.modifier(messageId, texte, maintenant);
		return relire(cercleId, messageId, moi.id(), maintenant);
	}

	/** Supprimer son message pour tout le monde : texte et fichiers effacés, la place reste. */
	@DeleteMapping("/api/cercles/{cercleId}/salon/messages/{messageId}")
	ResponseEntity<Void> supprimer(@PathVariable UUID cercleId, @PathVariable UUID messageId, @AuthenticationPrincipal UtilisateurConnecte moi) {
		membre(cercleId, moi.id());
		Instant maintenant = Instant.now();
		var m = salon.message(cercleId, messageId, maintenant).orElseThrow(ErreurCercle::introuvable);
		if (m.supprimeLe() == null) {
			if (!moi.id().equals(m.auteurId()) || "evenement".equals(m.type()) || "appel".equals(m.type())) {
				throw new ErreurCercle(HttpStatus.FORBIDDEN, "interdit", "On ne supprime que ses propres messages");
			}
			var pieces = salon.supprimer(messageId, maintenant);
			reactions.effacer(Reactions.Table.CERCLE, messageId);
			fichiers.supprimerOrphelins(pieces, moi.id());
		}
		return ResponseEntity.noContent().build();
	}

	/** Ajouter (`active: true`) ou retirer ma réaction ; renvoie le message à jour. */
	@PostMapping("/api/cercles/{cercleId}/salon/messages/{messageId}/reactions")
	MessageCercle reagir(@PathVariable UUID cercleId, @PathVariable UUID messageId, @RequestBody(required = false) NouvelleReaction corps,
			@AuthenticationPrincipal UtilisateurConnecte moi) {
		if (corps == null || corps.active() == null) {
			throw validation(corps == null ? "emoji" : "active", "obligatoire");
		}
		if (!Reactions.estUnEmoji(corps.emoji())) {
			throw validation("emoji", "format_invalide");
		}
		membre(cercleId, moi.id());
		Instant maintenant = Instant.now();
		var m = salon.message(cercleId, messageId, maintenant).orElseThrow(ErreurCercle::introuvable);
		if (m.supprimeLe() != null || "evenement".equals(m.type())) {
			throw ErreurCercle.conflit("message_supprime", "On ne réagit pas à ce message");
		}
		if (reactions.changer(Reactions.Table.CERCLE, messageId, moi.id(), corps.emoji(), corps.active(), maintenant) == Reactions.Resultat.TROP) {
			throw ErreurCercle.conflit("trop_de_reactions", "Dix réactions au plus par message");
		}
		salon.toucher(messageId);
		return relire(cercleId, messageId, moi.id(), maintenant);
	}

	private MessageCercle relire(UUID cercleId, UUID messageId, UUID moi, Instant maintenant) {
		var m = salon.message(cercleId, messageId, maintenant).orElseThrow(ErreurCercle::introuvable);
		Set<UUID> ids = new HashSet<>();
		personnes(m, ids);
		Contexte contexte = contexte(cercleId, List.of(m), moi, maintenant, ids);
		ids.remove(null);
		return vue(m, annuaire.lire(ids), contexte, moi);
	}

	/** Les réactions de ces messages et les messages qu'ils citent ; ajoute à `ids` les personnes à lire. */
	private Contexte contexte(UUID cercleId, List<SalonCercle.Message> messages, UUID moi, Instant maintenant, Set<UUID> ids) {
		var brutes = reactions.pour(Reactions.Table.CERCLE, messages.stream().map(SalonCercle.Message::id).toList(), moi);
		Map<UUID, SalonCercle.Message> cites = new java.util.HashMap<>();
		for (var m : messages) {
			if (m.reponseA() != null && !cites.containsKey(m.reponseA())) {
				salon.message(cercleId, m.reponseA(), maintenant).ifPresent(c -> {
					cites.put(c.id(), c);
					ids.add(c.auteurId());
				});
			}
		}
		brutes.values().forEach(l -> l.forEach(r -> ids.addAll(r.personnes())));
		ids.remove(null);
		Map<UUID, Compte> comptes = annuaire.lire(ids);
		Map<UUID, List<ReactionVue>> vues = new java.util.HashMap<>();
		brutes.forEach((id, liste) -> vues.put(id, liste.stream().map(r -> new ReactionVue(r.emoji(), r.nombre(), r.deMoi(),
				r.personnes().stream().map(comptes::get).filter(java.util.Objects::nonNull)
					.map(c -> c.id().equals(moi) ? "moi" : c.nom()).toList())).toList()));
		return new Contexte(vues, cites);
	}

	/** Un coup de main est résolu : n'importe quel membre peut le dire (celui qui a aidé, ou celui qui demandait). */
	@PostMapping("/api/cercles/{cercleId}/salon/messages/{messageId}/resolu")
	MessageCercle resoudre(@PathVariable UUID cercleId, @PathVariable UUID messageId, @AuthenticationPrincipal UtilisateurConnecte moi) {
		membre(cercleId, moi.id());
		Instant maintenant = Instant.now();
		var message = salon.message(cercleId, messageId, maintenant).orElseThrow(ErreurCercle::introuvable);
		if (!"coup_de_main".equals(message.type())) {
			throw ErreurCercle.conflit("pas_un_coup_de_main", "Seul un coup de main se marque résolu");
		}
		salon.resoudre(messageId, moi.id(), maintenant);
		var apres = salon.message(cercleId, messageId, maintenant).orElseThrow(ErreurCercle::introuvable);
		Set<UUID> ids = new HashSet<>();
		personnes(apres, ids);
		ids.remove(null);
		return vue(apres, annuaire.lire(ids));
	}

	/** Lancer l'appel du cercle, ou rejoindre celui qui est en cours : un seul à la fois. */
	@PostMapping("/api/cercles/{cercleId}/salon/appel")
	@Transactional
	ResponseEntity<AppelCercle> lancerAppel(@PathVariable UUID cercleId, @AuthenticationPrincipal UtilisateurConnecte moi) {
		membre(cercleId, moi.id());
		Instant maintenant = Instant.now();
		// Un appel vide depuis plus de 30 s est clos par `presents` : on en lance alors un neuf.
		var enCours = salon.appelEnCours(cercleId, maintenant)
			.filter(a -> salleAppel.presents(a.id(), a.lanceLe(), maintenant) >= 0 && salon.appelEnCours(cercleId, maintenant).isPresent());
		if (enCours.isPresent()) {
			var a = enCours.get();
			return ResponseEntity.ok(vue(a, annuaire.lire(a.lancePar() == null ? List.of() : List.of(a.lancePar()))));
		}
		long attente = salon.attenteAvantAppel(moi.id(), maintenant);
		if (attente > 0) {
			throw new ErreurCercle(HttpStatus.TOO_MANY_REQUESTS, "trop_de_requetes", "Trop d'appels lancés en peu de temps", null, attente);
		}
		var a = salon.lancer(cercleId, moi.id(), maintenant);
		return ResponseEntity.status(HttpStatus.CREATED).body(vue(a, annuaire.lire(List.of(moi.id()))));
	}

	/** Terminer l'appel : celui qui l'a lancé, ou le créateur du cercle. */
	@DeleteMapping("/api/cercles/{cercleId}/salon/appel")
	ResponseEntity<Void> terminerAppel(@PathVariable UUID cercleId, @AuthenticationPrincipal UtilisateurConnecte moi) {
		var cercle = membre(cercleId, moi.id());
		Instant maintenant = Instant.now();
		var enCours = salon.appelEnCours(cercleId, maintenant).orElseThrow(ErreurCercle::introuvable);
		if (!moi.id().equals(enCours.lancePar()) && !moi.id().equals(cercle.cercle().createurId())) {
			throw new ErreurCercle(HttpStatus.FORBIDDEN, "pas_lanceur", "Seuls celui qui a lancé l'appel et le créateur le terminent");
		}
		salon.terminer(enCours.id(), maintenant);
		return ResponseEntity.noContent().build();
	}

	@ExceptionHandler(ErreurFichier.class)
	ResponseEntity<ProblemDetail> erreurFichier(ErreurFichier e) {
		return FichiersController.probleme(e);
	}

	@ExceptionHandler(ErreurCercle.class)
	ResponseEntity<ProblemDetail> erreur(ErreurCercle e) {
		return CerclesController.probleme(e);
	}

	// ---------------------------------------------------------------- outils

	/** Le cercle, si on en est membre. 404 s'il n'existe pas, 403 `pas_membre` sinon. */
	private GestionCercles.Vue membre(UUID cercleId, UUID moi) {
		var vue = gestion.lire(cercleId).orElseThrow(ErreurCercle::introuvable);
		if (vue.membres().stream().noneMatch(m -> m.utilisateurId().equals(moi))) {
			throw new ErreurCercle(HttpStatus.FORBIDDEN, "pas_membre", "Le salon est réservé aux membres du cercle");
		}
		return vue;
	}

	/** Le chef de projet : le membre dont c'est le tour ; avant le tirage (ou après le dernier tour), le créateur. */
	static UUID chefDeProjet(GestionCercles.Vue vue) {
		Integer tour = vue.cercle().tourEnCours();
		return vue.membres().stream().filter(m -> tour != null && tour.equals(m.rang())).map(GestionCercles.Membre::utilisateurId)
			.findFirst().orElse(vue.cercle().createurId());
	}

	private void attendre(UUID moi, Instant maintenant) {
		long attente = salon.attenteAvantMessage(moi, maintenant);
		if (attente > 0) {
			throw new ErreurCercle(HttpStatus.TOO_MANY_REQUESTS, "trop_de_requetes", "Trop de messages en peu de temps", null, attente);
		}
	}

	private static String propre(String texte) {
		if (texte == null) {
			return null;
		}
		String t = texte.strip();
		return t.isEmpty() ? null : t;
	}

	private static void personnes(SalonCercle.Message m, Set<UUID> ids) {
		ids.add(m.auteurId());
		ids.add(m.personneId());
		ids.add(m.resoluPar());
	}

	private MessageCercle vue(SalonCercle.Message m, Map<UUID, Compte> comptes) {
		return vue(m, comptes, Contexte.VIDE, null);
	}

	private MessageCercle vue(SalonCercle.Message m, Map<UUID, Compte> comptes, Contexte contexte, UUID moi) {
		Citation citation = null;
		var cite = m.reponseA() == null ? null : contexte.cites().get(m.reponseA());
		if (cite != null) {
			String debut = cite.supprimeLe() != null || cite.texte() == null ? "" : cite.texte();
			if (debut.length() > CITATION_MAX) debut = debut.substring(0, CITATION_MAX - 1) + "…";
			citation = new Citation(cite.id(), personne(cite.auteurId(), comptes), debut, cite.supprimeLe() != null);
		}
		boolean supprime = m.supprimeLe() != null;
		return new MessageCercle(m.id(), m.numero(), m.type(), personne(m.auteurId(), comptes), supprime ? null : m.texte(),
				"point".equals(m.type()) && !supprime ? new Point(m.fait(), m.prevu(), m.bloque()) : null,
				"evenement".equals(m.type()) ? new Evenement(m.evenement(), m.tour(), personne(m.personneId(), comptes)) : null,
				"coup_de_main".equals(m.type()) ? new CoupDeMain(m.resoluLe(), personne(m.resoluPar(), comptes)) : null,
				m.appelId() == null ? null : new AppelDuMessage(m.appelId(), m.appelEnCours()), m.envoyeLe(),
				supprime ? List.of() : fichiers.decrire(m.pieces()), m.version(), citation, m.modifieLe(), supprime,
				supprime ? List.of() : contexte.reactions().getOrDefault(m.id(), List.of()));
	}

	private AppelCercle vue(SalonCercle.Appel a, Map<UUID, Compte> comptes) {
		return new AppelCercle(a.id(), personne(a.lancePar(), comptes), a.lanceLe(), appels + "/" + a.salle(),
				salleAppel.presents(a.id(), a.lanceLe(), Instant.now()));
	}

	private static PersonneResume personne(UUID id, Map<UUID, Compte> comptes) {
		Compte c = id == null ? null : comptes.get(id);
		return c == null ? null : CerclesController.personne(c);
	}

	private static ErreurCercle validation(String champ, String code) {
		return new ErreurCercle(HttpStatus.BAD_REQUEST, "validation", "Certains champs sont invalides",
				List.of(Map.of("champ", champ, "code", code)), 0);
	}

}
