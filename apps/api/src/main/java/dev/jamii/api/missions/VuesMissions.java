package dev.jamii.api.missions;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;

import dev.jamii.api.auth.Annuaire;
import dev.jamii.api.auth.Compte;

/**
 * Ce que l'API montre d'une mission, de ses propositions et de son devis (schémas de contracts/missions.yaml). Les
 * règles d'accès sont dans {@link Missions#role} ; ici on ne fait que présenter ce que la personne a le droit de voir.
 */
@Component
class VuesMissions {

	record PersonneResume(UUID id, String nom, String avatarUrl) {
	}

	record Moi(String role, List<String> actions) {
	}

	record Jalon(UUID id, int ordre, String titre, String description, Missions.Montant montant, int delaiJours,
			Instant echeance, String statut, List<Livraisons.Livraison> livraisons, Instant acceptationAutomatiqueLe, Instant accepteLe,
			boolean acceptationAutomatique, String paiement) {
	}

	record DevisVue(UUID id, Missions.Montant total, int retouchesComprises, String conditions, List<Jalon> jalons,
			Instant envoyeLe, Instant accepteLe) {
	}

	/** Le travail : visible du client et du développeur retenu seulement, dès qu'un devis existe. */
	record Travail(PersonneResume developpeur, DevisVue devis, String informationsDepart, Instant demarreLe,
			int retouchesRestantes, Object prolongationEnAttente, String annulationDemandeePar, Object desaccord,
			List<Object> avis) {
	}

	record MissionVue(UUID id, UUID demandeId, String titre, String besoin, String statut, List<String> competences,
			Missions.Montant budget, LocalDate dateSouhaitee, String visibilite, String ville, String pays, boolean aDistance,
			PersonneResume client, int nombrePropositions, Instant publieeLe, int version, Instant creeLe, Moi moi,
			Travail travail) {
	}

	record MissionResume(UUID id, String titre, String statut, List<String> competences, Missions.Montant budget,
			String ville, String pays, boolean aDistance, PersonneResume client, int nombrePropositions, Instant publieeLe) {
	}

	record PropositionVue(UUID id, PersonneResume developpeur, String message, Missions.Montant prix, int delaiJours,
			List<String> reponses, String statut, Instant envoyeeLe) {
	}

	private final Annuaire annuaire;

	private final Missions missions;

	private final Devis devis;

	private final Livraisons livraisons;

	VuesMissions(Annuaire annuaire, Missions missions, Devis devis, Livraisons livraisons) {
		this.annuaire = annuaire;
		this.missions = missions;
		this.devis = devis;
		this.livraisons = livraisons;
	}

	MissionVue vue(Missions.Lecture l, UUID qui) {
		Missions.Mission m = l.mission();
		int nombre = missions.nombresDePropositions(List.of(m.id())).getOrDefault(m.id(), 0);
		boolean retenu = "candidat".equals(l.role()) && devis.estRetenu(m.id(), qui);
		Optional<Devis.DevisComplet> courant = voitLeTravail(l, retenu) ? devis.courant(m.id()) : Optional.empty();
		Compte client = annuaire.lire(m.clientId()).orElse(null);
		return new MissionVue(m.id(), m.demandeId(), m.titre(), m.besoin(), m.statut(), m.competences(), m.budget(),
				m.dateSouhaitee(), m.visibilite(), m.ville(), m.pays(), m.aDistance(), personne(m.clientId(), client), nombre,
				m.publieeLe(), m.version(), m.creeLe(), new Moi(l.role(), actions(l, qui, nombre, retenu, courant, suivi(courant))),
				courant.map(this::travail).orElse(null));
	}

	MissionResume resume(Missions.Mission m, Compte client, int nombre) {
		return new MissionResume(m.id(), m.titre(), m.statut(), m.competences(), m.budget(), m.ville(), m.pays(), m.aDistance(),
				personne(m.clientId(), client), nombre, m.publieeLe());
	}

	PropositionVue proposition(Propositions.Proposition p, Compte developpeur) {
		return new PropositionVue(p.id(), personne(p.developpeurId(), developpeur), p.message(), p.prix(), p.delaiJours(),
				p.reponses(), p.statut(), p.envoyeeLe());
	}

	DevisVue devis(Devis.DevisComplet d) {
		return devis(d, null, Map.of());
	}

	/** Le devis avec le suivi de ses jalons : l'échéance compte depuis le démarrage, l'acceptation automatique depuis la livraison. */
	private DevisVue devis(Devis.DevisComplet d, Instant demarreLe, Map<UUID, Livraisons.SuiviJalon> suivi) {
		List<Jalon> jalons = d.jalons().stream().map(j -> {
			Livraisons.SuiviJalon s = suivi.get(j.id());
			Instant echeance = demarreLe == null ? null : demarreLe.plus(java.time.Duration.ofDays(j.delaiJours()));
			Instant automatique = s != null && "livre".equals(j.statut()) && s.livreLe() != null
					? s.livreLe().plus(Livraisons.ACCEPTATION_AUTOMATIQUE) : null;
			return new Jalon(j.id(), j.ordre(), j.titre(), j.description(), j.montant(), j.delaiJours(), echeance, j.statut(),
					s == null ? List.of() : s.livraisons(), automatique, s == null ? null : s.accepteLe(),
					s != null && s.acceptationAutomatique(), s == null ? "non_paye" : s.paiement());
		}).toList();
		return new DevisVue(d.id(), d.total(), d.retouchesComprises(), d.conditions(), jalons, d.envoyeLe(), d.accepteLe());
	}

	private Travail travail(Devis.DevisComplet d) {
		Compte developpeur = annuaire.lire(d.developpeurId()).orElse(null);
		Livraisons.Depart depart = livraisons.depart(d.missionId());
		return new Travail(personne(d.developpeurId(), developpeur), devis(d, depart.demarreLe(), livraisons.suivi(d.id())),
				depart.informations(), depart.demarreLe(), Math.max(0, d.retouchesComprises() - depart.retouchesUtilisees()), null, null, null,
				List.of());
	}

	private Map<UUID, Livraisons.SuiviJalon> suivi(Optional<Devis.DevisComplet> courant) {
		return courant.filter(d -> d.accepteLe() != null).map(d -> livraisons.suivi(d.id())).orElse(Map.of());
	}

	/** Le client, le développeur retenu et le candidat dont la proposition est retenue voient le devis ; personne d'autre. */
	private static boolean voitLeTravail(Missions.Lecture l, boolean candidatRetenu) {
		return !"brouillon".equals(l.mission().statut())
				&& ("client".equals(l.role()) || "developpeur_retenu".equals(l.role()) || candidatRetenu);
	}

	/** Ce que la personne peut faire ; le front en tire ses boutons, et chaque route revérifie. */
	private static List<String> actions(Missions.Lecture l, UUID qui, int nombre, boolean retenu,
			Optional<Devis.DevisComplet> courant, Map<UUID, Livraisons.SuiviJalon> suivi) {
		String statut = l.mission().statut();
		List<Devis.Jalon> jalons = courant.map(Devis.DevisComplet::jalons).orElse(List.of());
		boolean unLivre = jalons.stream().anyMatch(j -> "livre".equals(j.statut()));
		boolean unEnCours = jalons.stream().anyMatch(j -> "en_cours".equals(j.statut()));
		boolean aPayer = jalons.stream().anyMatch(j -> ("livre".equals(j.statut()) || "accepte".equals(j.statut()))
				&& suivi.containsKey(j.id()) && "non_paye".equals(suivi.get(j.id()).paiement()));
		boolean aRecevoir = suivi.values().stream().anyMatch(s -> "declare_paye".equals(s.paiement()));
		return switch (l.role()) {
			case "client" -> switch (statut) {
				case "brouillon" -> List.of("modifier", "publier", "inviter");
				case "publiee" -> {
					var actions = new java.util.ArrayList<>(List.of("modifier", "fermer", "inviter"));
					if (nombre > 0) {
						actions.add("retenir");
					}
					if (courant.isPresent() && courant.get().accepteLe() == null) {
						actions.add("accepter_devis");
					}
					yield List.copyOf(actions);
				}
				case "attribuee" -> List.of("demarrer");
				case "en_cours", "terminee" -> {
					var actions = new java.util.ArrayList<String>();
					if (unLivre && "en_cours".equals(statut)) {
						actions.addAll(List.of("accepter_jalon", "demander_retouche"));
					}
					if (aPayer) {
						actions.add("declarer_paye");
					}
					yield List.copyOf(actions);
				}
				default -> List.of();
			};
			case "developpeur_retenu" -> {
				var actions = new java.util.ArrayList<String>();
				if (unEnCours && "en_cours".equals(statut)) {
					actions.add("livrer");
				}
				if (aRecevoir) {
					actions.add("declarer_recu");
				}
				yield List.copyOf(actions);
			}
			case "candidat" -> retenu && "publiee".equals(statut) ? List.of("envoyer_devis") : List.of();
			case "invite", "visiteur" -> qui != null && "publiee".equals(statut) ? List.of("proposer") : List.of();
			default -> List.of();
		};
	}

	PersonneResume personne(UUID id, Compte compte) {
		return new PersonneResume(id, compte == null ? "Compte supprimé" : compte.nom(), compte == null ? null : compte.avatarUrl());
	}

	/** Les comptes des auteurs de ces propositions, en une seule lecture. */
	Map<UUID, Compte> comptes(java.util.Collection<UUID> ids) {
		return annuaire.lire(ids);
	}

}
