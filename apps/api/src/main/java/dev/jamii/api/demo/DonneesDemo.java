package dev.jamii.api.demo;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import dev.jamii.api.projets.Projets;

/**
 * Les données de la démo (docs/priorites.md) : 6 membres et 3 cercles, dont celui de Dakar avec un tour livré et un
 * tour en cours. Chargées <b>seulement</b> avec le profil `demo` (`SPRING_PROFILES_ACTIVE=demo`), jamais par une
 * migration : une base de production n'en voit jamais la couleur.
 * <p>
 * Rejouable : les identifiants sont fixes, et si Awa existe déjà, rien n'est rechargé. Les comptes n'ont ni login
 * GitHub (aucune invitation ne part vers un vrai compte) ni adresse joignable (domaine réservé `.test`).
 */
@Component
@Profile("demo")
class DonneesDemo implements ApplicationRunner {

	private static final Logger journal = LoggerFactory.getLogger(DonneesDemo.class);

	record Personne(String cle, String nom) {
		UUID id() {
			return DonneesDemo.id("utilisateur:" + cle);
		}

		String email() {
			return cle + "@demo.jamii.test";
		}
	}

	static final Personne AWA = new Personne("awa", "Awa Diop");
	static final Personne KOFI = new Personne("kofi", "Kofi Mensah");
	static final Personne FATOU = new Personne("fatou", "Fatou Ndiaye");
	static final Personne MOUSSA = new Personne("moussa", "Moussa Traoré");
	static final Personne NIA = new Personne("nia", "Nia Wanjiru");
	static final Personne YANN = new Personne("yann", "Yann Kouassi");

	static final UUID DAKAR = id("cercle:dakar");
	static final UUID ABIDJAN = id("cercle:abidjan");
	static final UUID NAIROBI = id("cercle:nairobi");

	/** Un membre d'un cercle : son idée et, après le tirage, son rang. */
	private record Place(Personne qui, String ideeTitre, String ideeDescription) {
	}

	private final JdbcClient base;

	private final Projets projets;

	private final TransactionTemplate transaction;

	DonneesDemo(JdbcClient base, Projets projets, TransactionTemplate transaction) {
		this.base = base;
		this.projets = projets;
		this.transaction = transaction;
	}

	@Override
	public void run(ApplicationArguments arguments) {
		charger(Instant.now());
	}

	/** Vrai si les données viennent d'être chargées, faux si elles l'étaient déjà. */
	boolean charger(Instant maintenant) {
		Boolean charge = transaction.execute(statut -> {
			boolean deja = base.sql("SELECT EXISTS (SELECT 1 FROM utilisateur WHERE id = :id)").param("id", AWA.id())
				.query(Boolean.class).single();
			if (deja) {
				return false;
			}
			for (Personne p : List.of(AWA, KOFI, FATOU, MOUSSA, NIA, YANN)) {
				personne(p, maintenant.minus(Duration.ofDays(30)));
			}
			dakar(maintenant);
			abidjan(maintenant);
			nairobi(maintenant);
			return true;
		});
		journal.info(Boolean.TRUE.equals(charge) ? "Données de démo chargées (profil demo)" : "Données de démo déjà présentes");
		return Boolean.TRUE.equals(charge);
	}

	// ---------------------------------------------------------------- les cercles

	/** Tiré il y a 9 jours : le tour d'Awa est livré, celui de Kofi en est à son 3e jour. */
	private void dakar(Instant maintenant) {
		List<Place> places = List.of(
				new Place(AWA, "Les tisserandes en ligne",
						"Une boutique en ligne pour les tisserandes de Thiès : catalogue de pagnes, commande par WhatsApp et paiement mobile."),
				new Place(KOFI, "Le carnet des coopératives de cacao",
						"Une application qui marche sans réseau pour que les coopératives de cacao suivent leurs pesées et leurs paiements."),
				new Place(FATOU, "Rendez-vous au centre de santé",
						"Prendre rendez-vous par SMS dans un centre de santé de quartier, avec un rappel la veille."),
				new Place(MOUSSA, "Soutien scolaire entre voisins",
						"Mettre en relation les élèves de terminale et les étudiants du quartier pour des séances de soutien."),
				new Place(NIA, "La bibliothèque partagée",
						"Prêter et emprunter des livres de cours entre étudiants d'une même université."),
				new Place(YANN, "Le carnet de crédit du boutiquier",
						"Un carnet numérique pour les boutiques de quartier : qui doit quoi, avec un rappel par SMS."));
		Instant tirage = maintenant.minus(Duration.ofDays(9));
		cercle(DAKAR, "Cercle de Dakar", "Dakar", 6, AWA, "en_cours", 2, tirage, places, maintenant.minus(Duration.ofDays(12)));
		ouvrirTour(DAKAR, 1, places, tirage);
		livrerTour(DAKAR, 1, maintenant.minus(Duration.ofDays(3)));
		ouvrirTour(DAKAR, 2, places, maintenant.minus(Duration.ofDays(3)));
	}

	/** Encore ouvert, 3 membres sur 4 : quelqu'un peut le rejoindre pendant la démo. */
	private void abidjan(Instant maintenant) {
		List<Place> places = List.of(
				new Place(YANN, "Réserver une salle de quartier",
						"Voir les salles libres d'une mairie de quartier et les réserver pour un baptême ou une réunion."),
				new Place(FATOU, "Les gardes de pharmacie",
						"Savoir chaque soir quelle pharmacie est de garde près de chez soi, même sans internet, par SMS."),
				new Place(MOUSSA, "Le suivi des tontines familiales",
						"Tenir les comptes d'une tontine de famille : qui a cotisé, qui reçoit ce mois-ci."));
		cercle(ABIDJAN, "Cercle d'Abidjan", "Abidjan", 4, YANN, "ouvert", null, null, places, maintenant.minus(Duration.ofDays(2)));
	}

	/** Terminé : les 4 projets sont livrés. */
	private void nairobi(Instant maintenant) {
		List<Place> places = List.of(
				new Place(NIA, "Covoiturage vers le marché",
						"Partager les trajets des commerçantes vers le marché de gros, avec les places et le prix connus d'avance."),
				new Place(KOFI, "L'annuaire des artisans",
						"Trouver un menuisier, un tailleur ou un électricien du quartier, avec les avis des voisins."),
				new Place(MOUSSA, "L'alerte coupure d'eau",
						"Prévenir les habitants d'un quartier des coupures d'eau prévues, par SMS et sur une carte."),
				new Place(YANN, "Les petites annonces de l'université",
						"Une page d'annonces pour les étudiants : chambres à louer, livres d'occasion, cours particuliers."));
		Instant tirage = maintenant.minus(Duration.ofDays(60));
		cercle(NAIROBI, "Cercle de Nairobi", "Nairobi", 4, NIA, "termine", null, tirage, places, maintenant.minus(Duration.ofDays(65)));
		for (int n = 1; n <= 4; n++) {
			Instant debut = tirage.plus(Duration.ofDays(14L * (n - 1)));
			ouvrirTour(NAIROBI, n, places, debut);
			livrerTour(NAIROBI, n, debut.plus(Duration.ofDays(13)));
		}
	}

	// ---------------------------------------------------------------- écritures

	private void personne(Personne p, Instant creeLe) {
		Timestamp t = Timestamp.from(creeLe);
		base.sql("INSERT INTO utilisateur (id, nom, email, cree_le, mis_a_jour_le) VALUES (:id, :nom, :email, :t, :t)")
			.param("id", p.id()).param("nom", p.nom()).param("email", p.email()).param("t", t).update();
		base.sql("INSERT INTO utilisateur_role (utilisateur_id, role) VALUES (:id, 'membre')").param("id", p.id()).update();
	}

	/** Le cercle et ses membres, dans l'ordre d'arrivée ; si l'ordre est tiré, le rang suit cet ordre. */
	private void cercle(UUID id, String nom, String ville, int taille, Personne createur, String statut, Integer tour,
			Instant tirage, List<Place> places, Instant creeLe) {
		base.sql("""
				INSERT INTO cercle (id, nom, ville, taille, createur_id, statut, tour_en_cours, tirage_le, cree_le)
				VALUES (:id, :nom, :ville, :taille, :createur, :statut, :tour, :tirage, :t)
				""")
			.param("id", id).param("nom", nom).param("ville", ville).param("taille", taille).param("createur", createur.id())
			.param("statut", statut).param("tour", tour).param("tirage", tirage == null ? null : Timestamp.from(tirage))
			.param("t", Timestamp.from(creeLe)).update();
		for (int i = 0; i < places.size(); i++) {
			Place place = places.get(i);
			base.sql("""
					INSERT INTO cercle_membre (cercle_id, utilisateur_id, rejoint_le, idee_titre, idee_description, rang)
					VALUES (:c, :u, :t, :titre, :description, :rang)
					""")
				.param("c", id).param("u", place.qui().id()).param("t", Timestamp.from(creeLe.plus(Duration.ofHours(i))))
				.param("titre", place.ideeTitre()).param("description", place.ideeDescription())
				.param("rang", tirage == null ? null : i + 1).update();
		}
	}

	/** Comme le tirage ou la livraison : le projet du tour, porté par son bénéficiaire, avec tout le cercle. */
	private void ouvrirTour(UUID cercle, int numero, List<Place> places, Instant quand) {
		Place beneficiaire = places.get(numero - 1);
		UUID projet = projets.creerPourCercle(beneficiaire.qui().id(), places.stream().map(p -> p.qui().id()).toList(),
				beneficiaire.ideeTitre(), beneficiaire.ideeDescription(), quand);
		base.sql("UPDATE cercle_membre SET projet_id = :p WHERE cercle_id = :c AND rang = :n")
			.param("p", projet).param("c", cercle).param("n", numero).update();
	}

	private void livrerTour(UUID cercle, int numero, Instant quand) {
		UUID projet = base.sql("SELECT projet_id FROM cercle_membre WHERE cercle_id = :c AND rang = :n")
			.param("c", cercle).param("n", numero).query(UUID.class).single();
		base.sql("UPDATE cercle_membre SET livre_le = :t WHERE cercle_id = :c AND rang = :n")
			.param("t", Timestamp.from(quand)).param("c", cercle).param("n", numero).update();
		projets.marquerLivre(projet, quand);
	}

	/** Des identifiants fixes, pour que la démo se recharge à l'identique et qu'un test les retrouve. */
	static UUID id(String nom) {
		return UUID.nameUUIDFromBytes(("jamii-demo:" + nom).getBytes(StandardCharsets.UTF_8));
	}

}
