package dev.jamii.api.demandes;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * Un service IA simulé, sans réseau : la tâche passe de « en attente » à « en cours » puis « terminée » avec le temps,
 * et le plan est un modèle générique construit à partir du texte de la PME. Annoncé comme tel (`origine` = `repli`),
 * comme le fait le vrai service quand son fournisseur est indisponible. Pour le local, les tests et la démo sans réseau.
 */
class PlansIaSimules implements PlansIa {

	private record Tache(Entree entree, Instant creeLe) {
	}

	private final Map<UUID, Tache> taches = new ConcurrentHashMap<>();

	private final Map<UUID, UUID> parCle = new ConcurrentHashMap<>();

	private final Duration attente;

	private final Clock horloge;

	private final JsonMapper json;

	PlansIaSimules(Duration attente, Clock horloge, JsonMapper json) {
		this.attente = attente;
		this.horloge = horloge;
		this.json = json;
	}

	@Override
	public UUID deposer(UUID cle, Entree entree) {
		// Même clé, même tâche : le comportement idempotent du vrai service.
		return parCle.computeIfAbsent(cle, k -> {
			UUID id = UUID.randomUUID();
			taches.put(id, new Tache(entree, horloge.instant()));
			return id;
		});
	}

	@Override
	public Etat lire(UUID tacheId) {
		Tache tache = taches.get(tacheId);
		if (tache == null) {
			throw new IllegalStateException("Tâche inconnue");
		}
		Duration age = Duration.between(tache.creeLe(), horloge.instant());
		if (age.compareTo(attente.dividedBy(2)) < 0) {
			return new Etat("en_attente", null, null, null);
		}
		if (age.compareTo(attente) < 0) {
			return new Etat("en_cours", null, null, null);
		}
		return new Etat("terminee", "repli", orientation(tache.entree()), null);
	}

	/**
	 * Une orientation de repli, comme celle du vrai service sans IA : des développeurs et un plan générique, aucun service
	 * Systalink poussé (on ne devine pas).
	 */
	private JsonNode orientation(Entree entree) {
		ObjectNode orientation = json.createObjectNode();
		String description = entree.description().strip().replaceAll("\\s+", " ");
		orientation.put("resume", description.length() > 600 ? description.substring(0, 599) + "…" : description);
		orientation.put("secteur", entree.secteur() == null ? "autre" : entree.secteur());
		orientation.putArray("services");
		orientation.put("developpeursNecessaires", true);
		orientation.putArray("competences").add("typescript").add("spring").add("design");
		orientation.set("plan", plan(entree));
		return orientation;
	}

	private JsonNode plan(Entree entree) {
		int semaines = entree.dureeSemaines() == null ? 8 : entree.dureeSemaines();
		ObjectNode plan = json.createObjectNode();
		ObjectNode cahier = plan.putObject("cahierDesCharges");
		String description = entree.description().strip().replaceAll("\\s+", " ");
		cahier.put("resume", description.length() > 400 ? description.substring(0, 399) + "…" : description);
		cahier.putArray("objectifs").add("Répondre au besoin décrit : « " + entree.titre() + " »")
			.add("Livrer une première version utilisable rapidement");
		ArrayNode fonctions = cahier.putArray("fonctionnalites");
		fonction(fonctions, "Le cœur du besoin", "Ce que la PME a décrit, dans sa version la plus simple.", "indispensable");
		fonction(fonctions, "Accès depuis un téléphone", "Un affichage pensé pour un petit écran et une connexion lente.", "indispensable");
		fonction(fonctions, "Suivi et statistiques", "Voir ce qui marche, une fois en ligne.", "plus_tard");
		cahier.putArray("horsPerimetre").add("Tout ce qui n'est pas dans la description");
		ArrayNode jalons = plan.putArray("jalons");
		jalon(jalons, "Maquette validée", Math.max(1, Math.round(semaines * 0.25f)), "Des écrans que la PME a relus.");
		jalon(jalons, "Première version", Math.max(1, Math.round(semaines * 0.65f)), "Le besoin principal fonctionne de bout en bout.");
		jalon(jalons, "Remise à la PME", semaines, "La PME utilise le service sans aide.");
		ArrayNode taches = plan.putArray("taches");
		tache(taches, "Maquette sur téléphone", 0, "design");
		tache(taches, "Construire le cœur du besoin", 1, "typescript", "spring");
		tache(taches, "Tester avec la PME et corriger", 2, "test");
		plan.putArray("rolesManquants");
		plan.putArray("servicesSuggeres");
		return plan;
	}

	private static void fonction(ArrayNode liste, String titre, String description, String priorite) {
		liste.addObject().put("titre", titre).put("description", description).put("priorite", priorite);
	}

	private static void jalon(ArrayNode liste, String titre, int semaine, String livrable) {
		liste.addObject().put("titre", titre).put("semaine", semaine).put("livrable", livrable);
	}

	private static void tache(ArrayNode liste, String titre, int jalon, String... competences) {
		ObjectNode tache = liste.addObject().put("titre", titre).put("jalon", jalon);
		ArrayNode c = tache.putArray("competences");
		for (String competence : competences) {
			c.add(competence);
		}
	}

}
