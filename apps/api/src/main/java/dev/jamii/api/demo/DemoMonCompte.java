package dev.jamii.api.demo;

import static dev.jamii.api.demo.DonneesDemo.AWA;
import static dev.jamii.api.demo.DonneesDemo.FATOU;
import static dev.jamii.api.demo.DonneesDemo.KOFI;
import static dev.jamii.api.demo.DonneesDemo.MOUSSA;
import static dev.jamii.api.demo.DonneesDemo.NIA;
import static dev.jamii.api.demo.DonneesDemo.YANN;
import static dev.jamii.api.demo.DonneesDemo.id;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;

import dev.jamii.api.projets.Projets;

/**
 * Un vrai compte dans la démo : celui dont le login GitHub est donné par `JAMII_DEMO_COMPTE` (vide : rien ne se passe).
 * Le compte doit déjà exister (s'être connecté une fois). Il reçoit :
 * <ul>
 * <li>le cercle des bâtisseurs, où son projet (Jamii) est le tour en cours, au 8e jour sur 14 : un tour livré avant le
 * sien, des jalons et des tâches datés pour le calendrier, un salon animé ;</li>
 * <li>deux communautés, des messages dans leurs salons et Jamii montré dans « Montre ton projet » ;</li>
 * <li>des contacts, une demande de contact à accepter, une conversation avec Kofi ;</li>
 * <li>une équipe au hackathon ouvert d'Abidjan ;</li>
 * <li>une mission publiée, avec deux propositions à départager.</li>
 * </ul>
 * Le cercle se crée une seule fois (ses dates partent de ce moment-là) ; le reste se complète à chaque démarrage
 * (identifiants stables, `ON CONFLICT DO NOTHING`).
 */
final class DemoMonCompte {

	private static final Logger journal = LoggerFactory.getLogger(DemoMonCompte.class);

	static final UUID CERCLE = id("cercle:batisseurs");

	static final UUID MISSION = id("mission:jamii-mobile");

	private static final String CLE = "mon-compte";

	private static final String LIVREE = "livree";

	/** Une tâche du tour : échéance en jours après le début du tour ; `qui` null pour le compte, sinon un membre de démo. */
	private record Tache(String titre, int jalon, Object qui, String statut, int echeance, String priorite, String description) {
	}

	private final JdbcClient base;

	private final Projets projets;

	private final Instant maintenant;

	private final String login;

	private UUID moi;

	DemoMonCompte(JdbcClient base, Projets projets, Instant maintenant, String login) {
		this.base = base;
		this.projets = projets;
		this.maintenant = maintenant;
		this.login = login == null ? "" : login.trim();
	}

	/** Vrai si le compte a été trouvé (et complété). */
	boolean charger() {
		if (login.isEmpty()) {
			return false;
		}
		Optional<UUID> compte = base.sql("SELECT id FROM utilisateur WHERE lower(login_github) = lower(:l)").param("l", login)
			.query(UUID.class).optional();
		if (compte.isEmpty()) {
			journal.warn("JAMII_DEMO_COMPTE : aucun compte avec le login GitHub {} (il doit s'être connecté une fois)", login);
			return false;
		}
		moi = compte.get();
		cercle();
		communautes();
		reseau();
		hackathon();
		mission();
		journal.info("Données de démo complétées pour le compte {}", login);
		return true;
	}

	private Timestamp ilYA(Duration duree) {
		return Timestamp.from(maintenant.minus(duree));
	}

	private static Duration heures(long n) {
		return Duration.ofHours(n);
	}

	private static Duration jours(long n) {
		return Duration.ofDays(n);
	}

	private UUID qui(Object p) {
		return p == null ? moi : ((DonneesDemo.Personne) p).id();
	}

	// ---------------------------------------------------------------- le cercle

	private void cercle() {
		boolean deja = base.sql("SELECT EXISTS (SELECT 1 FROM cercle WHERE id = :c)").param("c", CERCLE).query(Boolean.class).single();
		if (deja) {
			return;
		}
		String ville = base.sql("SELECT ville FROM profil WHERE utilisateur_id = :u AND ville IS NOT NULL").param("u", moi)
			.query(String.class).optional().orElse("Dakar");
		Instant creation = maintenant.minus(jours(24));
		Instant tirage = maintenant.minus(jours(21));
		Instant debutTour2 = maintenant.minus(jours(7));

		base.sql("""
				INSERT INTO cercle (id, nom, ville, taille, createur_id, statut, tour_en_cours, tirage_le, cree_le)
				VALUES (:id, 'Cercle des bâtisseurs', :ville, 4, :moi, 'en_cours', 2, :tirage, :cree)
				""")
			.param("id", CERCLE).param("ville", ville).param("moi", moi).param("tirage", Timestamp.from(tirage))
			.param("cree", Timestamp.from(creation)).update();
		// Dans l'ordre du tirage : le tour de Nia est livré, celui du compte (Jamii) est en cours.
		List<Object[]> places = List.of(
				new Object[] { NIA, "Le prochain matatu", "Savoir quand passe le prochain matatu, signalé en direct par les passagers, même par SMS." },
				new Object[] { null, "Jamii", "La plateforme où des développeurs africains se regroupent en cercles pour construire, à tour de rôle, le projet de chacun." },
				new Object[] { AWA, "Les tisserandes en ligne", "Une boutique en ligne pour les tisserandes de Thiès : catalogue de pagnes, commande par WhatsApp et paiement mobile." },
				new Object[] { KOFI, "Le carnet des coopératives de cacao", "Une application qui marche sans réseau pour que les coopératives de cacao suivent leurs pesées et leurs paiements." });
		List<UUID> membres = places.stream().map(p -> qui(p[0])).toList();
		for (int i = 0; i < places.size(); i++) {
			base.sql("""
					INSERT INTO cercle_membre (cercle_id, utilisateur_id, rejoint_le, idee_titre, idee_description, rang)
					VALUES (:c, :u, :t, :titre, :description, :rang)
					""")
				.param("c", CERCLE).param("u", membres.get(i)).param("t", Timestamp.from(creation.plus(heures(i))))
				.param("titre", places.get(i)[1]).param("description", places.get(i)[2]).param("rang", i + 1).update();
		}

		// Tour 1 : le matatu de Nia, livré il y a 8 jours.
		UUID matatu = ouvrirTour(1, NIA.id(), membres, places.get(0), tirage);
		taches(matatu, "1", tirage, List.of(
				new Object[] { "Les arrêts et les lignes", 3, true },
				new Object[] { "Le signalement par SMS", 8, true },
				new Object[] { "La mise en ligne", 13, true }), List.of(
				new Tache("Relever les arrêts de la ligne 46", 0, NIA, LIVREE, 2, "haute", null),
				new Tache("Dessiner la carte des lignes", 0, AWA, LIVREE, 3, "normale", null),
				new Tache("Recevoir un signalement par SMS", 1, null, LIVREE, 6, "haute", null),
				new Tache("Afficher l'heure estimée du prochain passage", 1, KOFI, LIVREE, 8, "normale", null),
				new Tache("Mettre l'API en ligne sur DataCloud", 2, null, LIVREE, 12, "normale", null),
				new Tache("Tester avec dix passagers de la ligne 46", 2, NIA, LIVREE, 13, "basse", null)), maintenant.minus(jours(8)));
		base.sql("UPDATE cercle_membre SET livre_le = :t WHERE cercle_id = :c AND rang = 1")
			.param("t", ilYA(jours(8))).param("c", CERCLE).update();
		projets.marquerLivre(matatu, maintenant.minus(jours(8)));

		// Tour 2 : Jamii, au 8e jour. Le calendrier montre des échéances passées, aujourd'hui, et à venir.
		UUID jamii = ouvrirTour(2, moi, membres, places.get(1), debutTour2);
		taches(jamii, "2", debutTour2, List.of(
				new Object[] { "Les cercles et le tirage au sort", 3, true },
				new Object[] { "Le QG du cercle : salon et calendrier", 8, false },
				new Object[] { "Les hackathons et la mise en ligne", 13, false }), List.of(
				new Tache("Écrire le parcours d'un nouveau membre", 0, null, LIVREE, 1, "haute", null),
				new Tache("Créer un cercle et inviter ses membres", 0, AWA, LIVREE, 2, "haute", null),
				new Tache("Tirer l'ordre des tours au sort", 0, KOFI, LIVREE, 3, "normale", null),
				new Tache("Le salon du cercle : messages et points du jour", 1, NIA, LIVREE, 5, "haute", null),
				new Tache("Le bouton « Je bloque » et les coups de main", 1, AWA, LIVREE, 6, "normale", null),
				new Tache("Le calendrier du tour : jalons et échéances", 1, null, "en_cours", 7, "haute",
						"Afficher les jalons et les échéances des tâches jour par jour.\nFini quand on voit d'un coup d'œil ce qui tombe cette semaine."),
				new Tache("Les appels vidéo du cercle", 1, KOFI, "en_cours", 8, "normale",
						"Lancer un appel depuis le salon, sans installer d'application.\nFini quand quatre membres se voient depuis un téléphone."),
				new Tache("Les inscriptions aux hackathons", 2, NIA, "a_faire", 10, "normale", null),
				new Tache("Noter les rendus avec le jury", 2, AWA, "a_faire", 11, "basse", null),
				new Tache("Mettre Jamii en ligne sur DataCloud", 2, null, "a_faire", 12, "haute",
						"Déployer l'API, le web et la base sur DataCloud avec le domaine jamii.dev.\nFini quand le cercle fait sa démo sur le site en ligne."),
				new Tache("Préparer la démo devant le jury", 2, null, "a_faire", 13, "haute", null)), maintenant.minus(heures(3)));

		new DemoSalonsCercles(base, maintenant).histoire(List.of(CERCLE));
		salon();
	}

	private UUID ouvrirTour(int rang, UUID beneficiaire, List<UUID> membres, Object[] place, Instant quand) {
		UUID projet = projets.creerPourCercle(beneficiaire, membres, (String) place[1], (String) place[2], quand);
		base.sql("UPDATE cercle_membre SET projet_id = :p WHERE cercle_id = :c AND rang = :n")
			.param("p", projet).param("c", CERCLE).param("n", rang).update();
		return projet;
	}

	/** Les jalons ({titre, échéance en jours, atteint}) et les tâches d'un tour, livrées l'une après l'autre jusqu'à `derniere`. */
	private void taches(UUID projet, String tour, Instant debut, List<Object[]> jalons, List<Tache> taches, Instant derniere) {
		for (int i = 0; i < jalons.size(); i++) {
			Object[] j = jalons.get(i);
			boolean atteint = (Boolean) j[2];
			base.sql("""
					INSERT INTO jalon (id, projet_id, titre, echeance, statut, ordre, cree_le, version)
					VALUES (:id, :p, :titre, :echeance, :statut, :ordre, :t, :version)
					""")
				.param("id", id("jalon:" + CLE + ":" + tour + ":" + i)).param("p", projet).param("titre", j[0])
				.param("echeance", date(debut, (Integer) j[1])).param("statut", atteint ? "atteint" : "prevu").param("ordre", i)
				.param("t", Timestamp.from(debut.plus(Duration.ofMinutes(i)))).param("version", atteint ? 1 : 0).update();
		}
		long livrees = taches.stream().filter(t -> LIVREE.equals(t.statut())).count();
		Duration pas = Duration.between(debut, derniere).dividedBy(Math.max(1, livrees));
		int rang = 0;
		for (int i = 0; i < taches.size(); i++) {
			Tache t = taches.get(i);
			Instant livreeLe = null;
			if (LIVREE.equals(t.statut())) {
				rang++;
				livreeLe = debut.plus(pas.multipliedBy(rang));
			}
			base.sql("""
					INSERT INTO tache (id, projet_id, jalon_id, titre, description, priorite, echeance, assignee_id, statut,
					                   livree_le, cree_le, version)
					VALUES (:id, :p, :jalon, :titre, :description, :priorite, :echeance, :assignee, :statut, :livree, :t, :version)
					""")
				.param("id", id("tache:" + CLE + ":" + tour + ":" + i)).param("p", projet)
				.param("jalon", id("jalon:" + CLE + ":" + tour + ":" + t.jalon())).param("titre", t.titre())
				.param("description", t.description()).param("priorite", t.priorite()).param("echeance", date(debut, t.echeance()))
				.param("assignee", qui(t.qui())).param("statut", t.statut())
				.param("livree", livreeLe == null ? null : Timestamp.from(livreeLe))
				.param("t", Timestamp.from(debut.plus(Duration.ofMinutes(10 + i))))
				.param("version", switch (t.statut()) { case LIVREE -> 2; case "en_cours" -> 1; default -> 0; }).update();
		}
		base.sql("UPDATE projet SET derniere_activite_le = GREATEST(derniere_activite_le, :t) WHERE id = :p")
			.param("t", Timestamp.from(derniere)).param("p", projet).update();
	}

	private static Date date(Instant debut, int jour) {
		return Date.valueOf(debut.plus(jours(jour)).atZone(ZoneOffset.UTC).toLocalDate());
	}

	/** Le salon du tour en cours : on y parle du projet du compte, et Kofi attend sa réponse. */
	private void salon() {
		texte(1, NIA.id(), "Le matatu est livré, merci à tous ! Place à Jamii 🚀", heures(167));
		texte(2, moi, "Merci Nia ! J'ai découpé Jamii en 11 tâches et 3 jalons, tout est dans le calendrier. Prenez ce qui vous parle.", heures(166));
		message(3, AWA.id(), "decision", "On montre le cercle en entier pendant la démo : salon, calendrier et tâches.", null, null, null, heures(150), null, null);
		message(4, KOFI.id(), "point", null, "Le tirage au sort, avec ses tests.", "Les appels vidéo du cercle.", null, heures(52), null, null);
		message(5, AWA.id(), "coup_de_main", "Le bouton « Je bloque » s'affiche deux fois sur mobile. Une idée ?", null, null, null, heures(30), moi, heures(28));
		texte(6, moi, "Oui : le composant était monté dans la barre et dans le salon. Je l'ai retiré de la barre.", heures(28));
		message(7, NIA.id(), "point", null, "Les points du jour dans le salon.", "Les inscriptions aux hackathons.", null, heures(20), null, null);
		message(8, moi, "point", null, "Les échéances des tâches dans le calendrier.", "Les jalons jour par jour.", null, heures(5), null, null);
		texte(9, KOFI.id(), "Les appels marchent à trois. Tu peux tester avec nous ce soir à 20 h ?", Duration.ofMinutes(50));
	}

	private void texte(int n, UUID auteur, String texte, Duration ilYa) {
		message(n, auteur, "texte", texte, null, null, null, ilYa, null, null);
	}

	private void message(int n, UUID auteur, String type, String texte, String fait, String prevu, String bloque, Duration ilYa,
			UUID resoluPar, Duration resoluIlYa) {
		base.sql("""
				INSERT INTO message_cercle (id, cercle_id, auteur_id, type, texte, point_fait, point_prevu, point_bloque,
				                            resolu_le, resolu_par, envoye_le)
				VALUES (:id, :c, :a, :type, :texte, :fait, :prevu, :bloque, :resoluLe, :resoluPar, :le)
				ON CONFLICT (id) DO NOTHING
				""")
			.param("id", id("salon-cercle:" + CERCLE + ":" + n)).param("c", CERCLE).param("a", auteur).param("type", type)
			.param("texte", texte).param("fait", fait).param("prevu", prevu).param("bloque", bloque)
			.param("resoluLe", resoluIlYa == null ? null : ilYA(resoluIlYa)).param("resoluPar", resoluPar)
			.param("le", ilYA(ilYa)).update();
	}

	// ---------------------------------------------------------------- les communautés

	private void communautes() {
		for (String slug : List.of("web", "antenne-dakar")) {
			communaute(slug).ifPresent(c -> base.sql("""
					INSERT INTO adhesion (communaute_id, utilisateur_id, role, rejoint_le) VALUES (:c, :u, 'membre', :t)
					ON CONFLICT (communaute_id, utilisateur_id) DO NOTHING
					""").param("c", c).param("u", moi).param("t", ilYA(jours(20))).update());
		}
		salon("web", "general", 1, moi, "Bonjour à tous ! Je construis Jamii avec mon cercle, on cherche des retours sur le salon du cercle.", heures(26));
		salon("web", "general", 2, YANN.id(), "Bienvenue ! Montre-le dans #projets, on testera.", heures(25));
		salon("antenne-dakar", "general", 1, moi, "Qui vient à la démo de Jamii samedi ? On montre les cercles en direct.", heures(6));
		salon("antenne-dakar", "general", 2, FATOU.id(), "Moi ! Je ramène deux amies de l'université.", heures(5));

		communaute("web").ifPresent(c -> {
			Optional<UUID> projet = base.sql("SELECT projet_id FROM cercle_membre WHERE cercle_id = :c AND utilisateur_id = :u")
				.param("c", CERCLE).param("u", moi).query(UUID.class).optional();
			UUID publication = id("publication:" + CLE + ":jamii");
			base.sql("""
					INSERT INTO publication (id, communaute_id, auteur_id, type, titre, contenu, projet_id, cree_le)
					VALUES (:id, :c, :a, 'montre_ton_projet', :titre, :contenu, :projet, :t)
					ON CONFLICT (id) DO NOTHING
					""")
				.param("id", publication).param("c", c).param("a", moi).param("titre", "Jamii : des cercles pour construire le projet de chacun")
				.param("contenu", "Quatre développeurs, quatre projets : à chaque tour, tout le cercle construit le projet de l'un d'eux. "
						+ "On en est au tour de Jamii lui-même. Vos retours sur le salon et le calendrier du cercle sont les bienvenus !")
				.param("projet", projet.orElse(null)).param("t", ilYA(heures(24))).update();
			reponse(publication, 1, DemoCommunautes.SAMIRA.id(), "Le calendrier du tour est très clair. Vous pensez ajouter un rappel la veille d'une échéance ?", heures(20));
			reponse(publication, 2, YANN.id(), "J'adore l'idée du tirage au sort. Je monte un cercle à Abidjan dès que c'est en ligne.", heures(18));
		});
	}

	private Optional<UUID> communaute(String slug) {
		return base.sql("SELECT id FROM communaute WHERE slug = :s").param("s", slug).query(UUID.class).optional();
	}

	private void salon(String communaute, String salon, int n, UUID auteur, String texte, Duration ilYa) {
		communaute(communaute).ifPresent(c -> base.sql("""
				INSERT INTO message_salon (id, salon_id, auteur_id, texte, envoye_le)
				SELECT :id, s.id, :a, :texte, :le FROM salon s WHERE s.communaute_id = :c AND s.slug = :slug
				ON CONFLICT (id) DO NOTHING
				""")
			.param("id", id("message:" + CLE + ":" + communaute + "/" + salon + "/" + n)).param("a", auteur).param("texte", texte)
			.param("le", ilYA(ilYa)).param("c", c).param("slug", salon).update());
	}

	private void reponse(UUID publication, int n, UUID auteur, String contenu, Duration ilYa) {
		base.sql("""
				INSERT INTO reponse (id, publication_id, auteur_id, contenu, cree_le)
				SELECT :id, :p, :a, :contenu, :t WHERE EXISTS (SELECT 1 FROM publication WHERE id = :p)
				ON CONFLICT (id) DO NOTHING
				""")
			.param("id", id("reponse:" + CLE + ":jamii:" + n)).param("p", publication).param("a", auteur).param("contenu", contenu)
			.param("t", ilYA(ilYa)).update();
	}

	// ---------------------------------------------------------------- le réseau

	private void reseau() {
		for (var p : List.of(AWA, KOFI, NIA, FATOU)) {
			contact(p.id(), moi, true, jours(18));
		}
		// Une demande à accepter pendant la démo.
		contact(YANN.id(), moi, false, heures(4));
		for (var p : List.of(AWA, NIA, MOUSSA)) {
			base.sql("INSERT INTO abonnement (abonne_id, suivi_id, depuis) VALUES (:a, :s, :t) ON CONFLICT DO NOTHING")
				.param("a", p.id()).param("s", moi).param("t", ilYA(jours(10))).update();
		}
		prive(1, KOFI.id(), moi, "Salut ! J'ai vu le calendrier de Jamii, je prends les appels vidéo.", heures(30), true);
		prive(2, moi, KOFI.id(), "Parfait, merci Kofi. Regarde Jitsi, on l'utilise déjà dans les salons.", heures(29), true);
		prive(3, KOFI.id(), moi, "C'est branché : les appels marchent à trois. Je te montre ce soir ?", Duration.ofMinutes(45), false);
	}

	/** Une seule ligne par paire, dans un sens ou dans l'autre : rien si le lien existe déjà. */
	private void contact(UUID demandeur, UUID destinataire, boolean accepte, Duration ilYa) {
		base.sql("""
				INSERT INTO contact (demandeur_id, destinataire_id, accepte, demande_le, accepte_le)
				SELECT :d, :r, :accepte, :demande, :accepteLe
				WHERE NOT EXISTS (SELECT 1 FROM contact WHERE (demandeur_id = :d AND destinataire_id = :r)
				                                         OR (demandeur_id = :r AND destinataire_id = :d))
				""")
			.param("d", demandeur).param("r", destinataire).param("accepte", accepte).param("demande", ilYA(ilYa))
			.param("accepteLe", accepte ? ilYA(ilYa.minus(heures(1))) : null).update();
	}

	private void prive(int n, UUID de, UUID a, String texte, Duration ilYa, boolean lu) {
		base.sql("""
				INSERT INTO message (id, expediteur_id, destinataire_id, texte, envoye_le, lu_le)
				VALUES (:id, :de, :a, :texte, :le, :lu)
				ON CONFLICT (id) DO NOTHING
				""")
			.param("id", id("message-prive:" + CLE + ":kofi:" + n)).param("de", de).param("a", a).param("texte", texte)
			.param("le", ilYA(ilYa)).param("lu", lu ? ilYA(ilYa.minus(Duration.ofMinutes(5))) : null).update();
	}

	// ---------------------------------------------------------------- le hackathon

	/** Une équipe au hackathon ouvert d'Abidjan, avec Awa, si le compte n'y est pas déjà inscrit. */
	private void hackathon() {
		UUID h = DemoAnnonces.HACKATHON_OUVERT;
		boolean possible = base.sql("""
				SELECT EXISTS (SELECT 1 FROM hackathon WHERE id = :h)
				   AND NOT EXISTS (SELECT 1 FROM inscription WHERE hackathon_id = :h AND utilisateur_id IN (:moi, :awa))
				""").param("h", h).param("moi", moi).param("awa", AWA.id()).query(Boolean.class).single();
		if (!possible) {
			return;
		}
		UUID equipe = id("hackathon:abidjan-sante:equipe:" + CLE);
		base.sql("INSERT INTO equipe (id, hackathon_id, nom, chef_id, cree_le) VALUES (:id, :h, 'Les bâtisseurs', :c, :t)")
			.param("id", equipe).param("h", h).param("c", moi).param("t", ilYA(heures(20))).update();
		for (UUID u : List.of(moi, AWA.id())) {
			base.sql("""
					INSERT INTO inscription (id, hackathon_id, utilisateur_id, equipe_id, statut, cherche_equipe, competences, cree_le)
					VALUES (:id, :h, :u, :e, 'acceptee', FALSE, :c, :t)
					""")
				.param("id", id("hackathon:abidjan-sante:inscription:" + u)).param("h", h).param("u", u).param("e", equipe)
				.param("c", new String[] { "vue", "spring" }).param("t", ilYA(heures(20))).update();
		}
	}

	// ---------------------------------------------------------------- la mission

	/** Une mission publiée par le compte, avec deux propositions : de quoi montrer le choix d'un développeur. */
	private void mission() {
		base.sql("""
				INSERT INTO mission (id, client_id, titre, besoin, competences, budget_centimes, devise, date_souhaitee, visibilite,
					ville, pays, a_distance, statut, version, cree_le, publiee_le)
				VALUES (:id, :moi, 'L''application mobile de Jamii', :besoin, ARRAY['flutter', 'spring'], 600000, 'XOF', :souhaitee,
					'publique', 'Dakar', 'SN', TRUE, 'publiee', 2, :t, :t)
				ON CONFLICT (id) DO NOTHING
				""")
			.param("id", MISSION).param("moi", moi)
			.param("besoin", "Jamii a besoin d'une application mobile : le salon du cercle, le point du jour et le calendrier du tour, "
					+ "avec des notifications. Elle doit marcher sur un téléphone d'entrée de gamme et avec peu de réseau.")
			.param("souhaitee", date(maintenant, 45)).param("t", ilYA(jours(3))).update();
		proposition(KOFI, "J'ai déjà fait une appli Flutter qui marche sans réseau pour des coopératives. Trois jalons, chacun testé avec vous.",
				550_000, 30, heures(40));
		proposition(FATOU, "Je propose de commencer par le salon et le point du jour, puis le calendrier. Je m'occupe aussi du design.",
				600_000, 35, heures(12));
	}

	private void proposition(DonneesDemo.Personne qui, String message, long prix, int delai, Duration ilYa) {
		base.sql("""
				INSERT INTO proposition (id, mission_id, developpeur_id, message, prix_centimes, devise, delai_jours, statut, cree_le)
				VALUES (:id, :m, :d, :message, :prix, 'XOF', :delai, 'envoyee', :t)
				ON CONFLICT DO NOTHING
				""")
			.param("id", id("mission:jamii-mobile:proposition:" + qui.cle())).param("m", MISSION).param("d", qui.id())
			.param("message", message).param("prix", prix).param("delai", delai).param("t", ilYA(ilYa)).update();
	}

}
