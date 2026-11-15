package dev.jamii.api.communautes;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.jamii.api.auth.Annuaire;
import dev.jamii.api.auth.Compte;
import dev.jamii.api.auth.UtilisateurConnecte;

/**
 * Les salons d'une communauté (contracts/communautes.yaml v0.7, #155). Lire : tout utilisateur connecté. Écrire, lancer
 * un appel et recevoir son adresse : les membres seulement. Terminer un appel : celui qui l'a lancé, ou un modérateur.
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
			AppelDuMessage appel) {
	}

	record Liste<T>(List<T> elements) {
	}

	record NouveauMessage(String texte) {
	}

	private final GestionCommunautes communautes;

	private final Salons salons;

	private final Annuaire annuaire;

	private final String appels;

	SalonsController(GestionCommunautes communautes, Salons salons, Annuaire annuaire,
			@Value("${jamii.appels.url:https://meet.jit.si}") String appels) {
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
		var messages = salons.messages(leSalon.get().id(), depuis, taille.intValue(), Instant.now());
		Map<UUID, Compte> comptes = annuaire.lire(messages.stream().map(Salons.Message::auteurId).distinct().toList());
		return ResponseEntity.ok()
			.cacheControl(CacheControl.noStore())
			.body(new Liste<>(messages.stream().map(m -> message(m, comptes)).toList()));
	}

	@PostMapping("/api/communautes/{slug}/salons/{salon}/messages")
	ResponseEntity<?> ecrire(@PathVariable String slug, @PathVariable String salon, @RequestBody NouveauMessage demande,
			@AuthenticationPrincipal UtilisateurConnecte moi) {
		String texte = demande.texte() == null ? "" : demande.texte().strip();
		if (texte.isEmpty() || texte.length() > 2000) {
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
		long attente = salons.attenteAvantMessage(moi.id(), maintenant);
		if (attente > 0) {
			return Problemes.tropDeRequetes(attente, "Trop de messages en peu de temps");
		}
		var message = salons.ecrire(leSalon.get().id(), moi.id(), texte, maintenant);
		return ResponseEntity.status(HttpStatus.CREATED).body(message(message, annuaire.lire(List.of(moi.id()))));
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

	private static MessageSalon message(Salons.Message m, Map<UUID, Compte> comptes) {
		return new MessageSalon(m.id(), m.numero(), VuesPublications.personne(m.auteurId(), comptes), m.texte(), m.envoyeLe(),
				m.appelId() == null ? null : new AppelDuMessage(m.appelId(), m.appelEnCours()));
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
