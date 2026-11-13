package dev.jamii.api.cercles;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jamii.api.auth.Annuaire;
import dev.jamii.api.auth.Compte;
import dev.jamii.api.projets.Projets;

/**
 * Les cercles de la Tech Tontine (contracts/cercles.yaml). Un tour n'a pas de table : c'est le projet d'un membre.
 * Toute écriture sur un cercle commence par verrouiller sa ligne (`FOR UPDATE`) : deux personnes qui prennent la
 * dernière place, ou deux requêtes de livraison identiques, passent l'une après l'autre, jamais ensemble.
 */
@Service
class GestionCercles {

	static final int CERCLES_PAR_JOUR = 5;

	private static final SecureRandom HASARD = new SecureRandom();

	record Fiche(UUID id, String nom, String ville, int taille, UUID createurId, String statut, Integer tourEnCours,
			Instant tirageLe, Instant creeLe, int nombreMembres) {
	}

	record Membre(UUID utilisateurId, Instant rejointLe, String ideeTitre, String ideeDescription, Integer rang,
			UUID projetId, Instant livreLe, String depotFournisseur, String depotUrl, String depotCloneUrl) {
	}

	record Vue(Fiche cercle, List<Membre> membres) {
	}

	/** Une page de cercles et le curseur de la suivante (null à la dernière page). */
	record Page(List<Fiche> elements, String curseurSuivant) {
	}

	private final JdbcClient base;

	private final Projets projets;

	private final Annuaire annuaire;

	GestionCercles(JdbcClient base, Projets projets, Annuaire annuaire) {
		this.base = base;
		this.projets = projets;
		this.annuaire = annuaire;
	}

	// ---------------------------------------------------------------- lecture

	@Transactional(readOnly = true)
	Optional<Vue> lire(UUID id) {
		return fiche(id, false).map(f -> new Vue(f, membres(id)));
	}

	@Transactional(readOnly = true)
	Page lister(UUID moi, boolean seulementMiens, int limite, String curseur) {
		Instant avant = null;
		UUID avantId = null;
		if (curseur != null) {
			try {
				String[] morceaux = new String(Base64.getUrlDecoder().decode(curseur)).split("\\|");
				avant = Instant.parse(morceaux[0]);
				avantId = UUID.fromString(morceaux[1]);
			}
			catch (RuntimeException e) {
				throw new ErreurCercle(HttpStatus.BAD_REQUEST, "validation", "Certains champs sont invalides",
						List.of(java.util.Map.of("champ", "curseur", "code", "format_invalide")), 0);
			}
		}
		String filtre = seulementMiens
				? "EXISTS (SELECT 1 FROM cercle_membre m WHERE m.cercle_id = c.id AND m.utilisateur_id = :moi)"
				: "c.statut = 'ouvert' AND (SELECT count(*) FROM cercle_membre m WHERE m.cercle_id = c.id) < c.taille";
		String apres = avant == null ? "" : "AND (c.cree_le, c.id) < (:avant, :avantId)";
		var requete = base.sql("""
				SELECT c.id, c.nom, c.ville, c.taille, c.createur_id, c.statut, c.tour_en_cours, c.tirage_le, c.cree_le,
				       (SELECT count(*) FROM cercle_membre m WHERE m.cercle_id = c.id) AS nombre_membres
				FROM cercle c
				WHERE %s %s
				ORDER BY c.cree_le DESC, c.id DESC
				LIMIT :limite
				""".formatted(filtre, apres))
			.param("limite", limite + 1);
		if (seulementMiens) {
			requete = requete.param("moi", moi);
		}
		if (avant != null) {
			requete = requete.param("avant", Timestamp.from(avant)).param("avantId", avantId);
		}
		List<Fiche> lignes = requete.query((rs, n) -> enFiche(rs)).list();
		if (lignes.size() <= limite) {
			return new Page(lignes, null);
		}
		List<Fiche> page = lignes.subList(0, limite);
		Fiche derniere = page.getLast();
		String suivant = Base64.getUrlEncoder().withoutPadding()
			.encodeToString((derniere.creeLe() + "|" + derniere.id()).getBytes());
		return new Page(new ArrayList<>(page), suivant);
	}

	// ---------------------------------------------------------------- écriture

	@Transactional
	UUID creer(UUID moi, String nom, String ville, int taille, Instant maintenant) {
		List<Instant> recents = base.sql("SELECT cree_le FROM cercle WHERE createur_id = :moi AND cree_le > :depuis ORDER BY cree_le")
			.param("moi", moi)
			.param("depuis", Timestamp.from(maintenant.minus(Duration.ofDays(1))))
			.query((rs, n) -> rs.getTimestamp("cree_le").toInstant())
			.list();
		if (recents.size() >= CERCLES_PAR_JOUR) {
			long attente = Math.max(1, Duration.between(maintenant, recents.getFirst().plus(Duration.ofDays(1))).toSeconds());
			throw new ErreurCercle(HttpStatus.TOO_MANY_REQUESTS, "trop_de_requetes", "Trop de cercles créés aujourd'hui",
					null, attente);
		}
		UUID id = UUID.randomUUID();
		Timestamp t = Timestamp.from(maintenant);
		base.sql("""
				INSERT INTO cercle (id, nom, ville, taille, createur_id, cree_le) VALUES (:id, :nom, :ville, :taille, :moi, :t)
				""")
			.param("id", id).param("nom", nom).param("ville", ville).param("taille", taille).param("moi", moi)
			.param("t", t).update();
		ajouterMembre(id, moi, t);
		return id;
	}

	@Transactional
	void rejoindre(UUID cercleId, UUID moi, Instant maintenant) {
		Fiche cercle = fiche(cercleId, true).orElseThrow(ErreurCercle::introuvable);
		if (!"ouvert".equals(cercle.statut())) {
			throw ErreurCercle.conflit("tirage_deja_fait", "L'ordre est déjà tiré : le cercle est fermé");
		}
		if (estMembre(cercleId, moi)) {
			throw ErreurCercle.conflit("deja_membre", "Tu es déjà membre de ce cercle");
		}
		if (cercle.nombreMembres() >= cercle.taille()) {
			throw ErreurCercle.conflit("cercle_complet", "Le cercle est complet");
		}
		try {
			ajouterMembre(cercleId, moi, Timestamp.from(maintenant));
		}
		catch (DuplicateKeyException dejaMembre) {
			throw ErreurCercle.conflit("deja_membre", "Tu es déjà membre de ce cercle");
		}
	}

	@Transactional
	void deposerIdee(UUID cercleId, UUID moi, String titre, String description) {
		Fiche cercle = fiche(cercleId, true).orElseThrow(ErreurCercle::introuvable);
		if (!estMembre(cercleId, moi)) {
			throw ErreurCercle.interdit();
		}
		if (!"ouvert".equals(cercle.statut())) {
			throw ErreurCercle.conflit("tirage_deja_fait", "L'ordre est déjà tiré : l'idée ne change plus");
		}
		base.sql("UPDATE cercle_membre SET idee_titre = :titre, idee_description = :description WHERE cercle_id = :c AND utilisateur_id = :u")
			.param("titre", titre).param("description", description).param("c", cercleId).param("u", moi).update();
	}

	/** Tire l'ordre et ouvre le tour 1. Renvoie le numéro du tour ouvert (1). */
	@Transactional
	int tirer(UUID cercleId, UUID moi, Instant maintenant) {
		Fiche cercle = fiche(cercleId, true).orElseThrow(ErreurCercle::introuvable);
		if (!cercle.createurId().equals(moi)) {
			throw ErreurCercle.interdit();
		}
		if (!"ouvert".equals(cercle.statut())) {
			throw ErreurCercle.conflit("tirage_deja_fait", "L'ordre est déjà tiré");
		}
		if (cercle.nombreMembres() < cercle.taille()) {
			throw ErreurCercle.conflit("cercle_incomplet", "Il manque des membres pour tirer l'ordre");
		}
		List<Membre> membres = new ArrayList<>(membres(cercleId));
		if (membres.stream().anyMatch(m -> m.ideeTitre() == null)) {
			throw ErreurCercle.conflit("idees_manquantes", "Chaque membre doit déposer son idée avant le tirage");
		}
		// Le mélange est fait ici, par le serveur, avec un générateur cryptographique. Jamais par le navigateur.
		Collections.shuffle(membres, HASARD);
		for (int i = 0; i < membres.size(); i++) {
			base.sql("UPDATE cercle_membre SET rang = :rang WHERE cercle_id = :c AND utilisateur_id = :u")
				.param("rang", i + 1).param("c", cercleId).param("u", membres.get(i).utilisateurId()).update();
		}
		base.sql("UPDATE cercle SET statut = 'en_cours', tour_en_cours = 1, tirage_le = :t, version = version + 1 WHERE id = :id")
			.param("t", Timestamp.from(maintenant)).param("id", cercleId).update();
		ouvrirTour(cercleId, 1, maintenant);
		return 1;
	}

	/** Livre le tour `numero`. Renvoie le numéro du tour ouvert ensuite, ou 0 si c'était le dernier. */
	@Transactional
	int livrer(UUID cercleId, int numero, UUID moi, Instant maintenant) {
		Fiche cercle = fiche(cercleId, true).orElseThrow(ErreurCercle::introuvable);
		if (numero < 1 || numero > cercle.taille()) {
			throw ErreurCercle.introuvable();
		}
		List<Membre> membres = membres(cercleId);
		Optional<Membre> proprietaire = membres.stream().filter(m -> m.rang() != null && m.rang() == numero).findFirst();
		boolean autorise = cercle.createurId().equals(moi) || proprietaire.map(m -> m.utilisateurId().equals(moi)).orElse(false);
		if (!autorise) {
			throw ErreurCercle.interdit();
		}
		if ("termine".equals(cercle.statut())) {
			throw ErreurCercle.conflit("cercle_termine", "Tous les tours sont déjà livrés");
		}
		if (!"en_cours".equals(cercle.statut()) || cercle.tourEnCours() != numero) {
			throw ErreurCercle.conflit("tour_non_en_cours", "Ce n'est pas le tour en cours");
		}
		Timestamp t = Timestamp.from(maintenant);
		base.sql("UPDATE cercle_membre SET livre_le = :t WHERE cercle_id = :c AND rang = :n")
			.param("t", t).param("c", cercleId).param("n", numero).update();
		proprietaire.map(Membre::projetId).ifPresent(projet -> projets.marquerLivre(projet, maintenant));
		if (numero == cercle.taille()) {
			base.sql("UPDATE cercle SET statut = 'termine', tour_en_cours = NULL, version = version + 1 WHERE id = :id")
				.param("id", cercleId).update();
			return 0;
		}
		base.sql("UPDATE cercle SET tour_en_cours = :n, version = version + 1 WHERE id = :id")
			.param("n", numero + 1).param("id", cercleId).update();
		ouvrirTour(cercleId, numero + 1, maintenant);
		return numero + 1;
	}

	// ---------------------------------------------------------------- dépôt de code

	@Transactional(readOnly = true)
	Optional<DepotCode.DemandeDepot> demandeDepot(UUID cercleId, int numero) {
		return lire(cercleId).flatMap(vue -> vue.membres().stream()
			.filter(m -> m.rang() != null && m.rang() == numero && m.depotUrl() == null)
			.findFirst()
			.map(tour -> {
				var comptes = annuaire.lire(vue.membres().stream().map(Membre::utilisateurId).toList());
				List<String> logins = vue.membres().stream()
					.map(m -> comptes.get(m.utilisateurId()))
					.filter(c -> c != null && c.loginGithub() != null)
					.map(Compte::loginGithub)
					.toList();
				return new DepotCode.DemandeDepot(cercleId, vue.cercle().nom(), numero, tour.ideeTitre(), logins);
			}));
	}

	@Transactional
	void enregistrerDepot(UUID cercleId, int numero, DepotCode.DepotDuTour depot) {
		base.sql("""
				UPDATE cercle_membre SET depot_fournisseur = :f, depot_url = :u, depot_clone_url = :c
				WHERE cercle_id = :cercle AND rang = :n AND depot_url IS NULL
				""")
			.param("f", depot.fournisseur()).param("u", depot.url()).param("c", depot.cloneUrl()).param("cercle", cercleId)
			.param("n", numero).update();
	}

	// ---------------------------------------------------------------- privé

	private void ouvrirTour(UUID cercleId, int numero, Instant maintenant) {
		List<Membre> membres = membres(cercleId);
		Membre beneficiaire = membres.stream().filter(m -> m.rang() != null && m.rang() == numero).findFirst().orElseThrow();
		UUID projet = projets.creerPourCercle(beneficiaire.utilisateurId(), membres.stream().map(Membre::utilisateurId).toList(),
				beneficiaire.ideeTitre(), beneficiaire.ideeDescription(), maintenant);
		base.sql("UPDATE cercle_membre SET projet_id = :p WHERE cercle_id = :c AND rang = :n")
			.param("p", projet).param("c", cercleId).param("n", numero).update();
	}

	private void ajouterMembre(UUID cercleId, UUID utilisateurId, Timestamp quand) {
		base.sql("INSERT INTO cercle_membre (cercle_id, utilisateur_id, rejoint_le) VALUES (:c, :u, :t)")
			.param("c", cercleId).param("u", utilisateurId).param("t", quand).update();
	}

	private boolean estMembre(UUID cercleId, UUID utilisateurId) {
		return base.sql("SELECT EXISTS (SELECT 1 FROM cercle_membre WHERE cercle_id = :c AND utilisateur_id = :u)")
			.param("c", cercleId).param("u", utilisateurId).query(Boolean.class).single();
	}

	private Optional<Fiche> fiche(UUID id, boolean verrouiller) {
		return base.sql("""
				SELECT c.id, c.nom, c.ville, c.taille, c.createur_id, c.statut, c.tour_en_cours, c.tirage_le, c.cree_le,
				       (SELECT count(*) FROM cercle_membre m WHERE m.cercle_id = c.id) AS nombre_membres
				FROM cercle c WHERE c.id = :id
				""" + (verrouiller ? " FOR UPDATE OF c" : ""))
			.param("id", id)
			.query((rs, n) -> enFiche(rs))
			.optional();
	}

	private List<Membre> membres(UUID cercleId) {
		return base.sql("""
				SELECT utilisateur_id, rejoint_le, idee_titre, idee_description, rang, projet_id, livre_le,
				       depot_fournisseur, depot_url, depot_clone_url
				FROM cercle_membre WHERE cercle_id = :c ORDER BY rang NULLS LAST, rejoint_le, utilisateur_id
				""")
			.param("c", cercleId)
			.query((rs, n) -> new Membre(rs.getObject("utilisateur_id", UUID.class), rs.getTimestamp("rejoint_le").toInstant(),
					rs.getString("idee_titre"), rs.getString("idee_description"), entier(rs, "rang"),
					rs.getObject("projet_id", UUID.class),
					rs.getTimestamp("livre_le") == null ? null : rs.getTimestamp("livre_le").toInstant(),
					rs.getString("depot_fournisseur"), rs.getString("depot_url"), rs.getString("depot_clone_url")))
			.list();
	}

	private static Integer entier(java.sql.ResultSet rs, String colonne) throws java.sql.SQLException {
		int valeur = rs.getInt(colonne);
		return rs.wasNull() ? null : valeur;
	}

	private static Fiche enFiche(java.sql.ResultSet rs) throws java.sql.SQLException {
		return new Fiche(rs.getObject("id", UUID.class), rs.getString("nom"), rs.getString("ville"), rs.getInt("taille"),
				rs.getObject("createur_id", UUID.class), rs.getString("statut"), entier(rs, "tour_en_cours"),
				rs.getTimestamp("tirage_le") == null ? null : rs.getTimestamp("tirage_le").toInstant(),
				rs.getTimestamp("cree_le").toInstant(), rs.getInt("nombre_membres"));
	}

}
