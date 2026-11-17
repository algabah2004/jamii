package dev.jamii.api.projets;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import dev.jamii.api.auth.Annuaire;
import dev.jamii.api.deploiement.DeploiementsEnLigne;
import dev.jamii.api.profils.SourcePreuves;

/**
 * Ce que les projets prouvent du travail d'une personne (profils.SourcePreuves) : ses projets Jamii, les tâches
 * qu'elle a livrées à la date prévue, et ses projets en ligne (vérifiés par le module deploiement).
 * <p>
 * Une tâche compte si elle lui est attribuée, livrée, et avait une échéance : livrée au plus tard ce jour-là, elle est
 * « à temps ». Sans échéance, on ne peut rien prouver : elle ne compte pas.
 */
@Component
class PreuvesProjets implements SourcePreuves {

	private static final DateTimeFormatter JOUR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

	/**
	 * Les projets du parcours d'un visiteur de la démo (auth.Annuaire.VISITEURS_DEMO) : ils ne comptent que pour lui.
	 * Sinon, chaque visiteur ajouterait un projet et des tâches aux personnes de la démo qui sont dans son cercle.
	 */
	private static final String SANS_VISITEUR_TIERS = """
			  AND NOT EXISTS (SELECT 1 FROM utilisateur w WHERE w.email LIKE :visiteurs AND w.id <> :u
			                  AND (w.id = p.porteur_id OR EXISTS (SELECT 1 FROM membre_equipe v
			                                                      WHERE v.projet_id = p.id AND v.utilisateur_id = w.id)))
			""";

	/** Ses projets : ceux qu'elle porte et ceux de son équipe actuelle. Ni recrutement, ni abandonnés. */
	private static final String SES_PROJETS = """
			SELECT p.id, p.titre, p.porteur_id, p.derniere_activite_le FROM projet p
			WHERE p.statut IN ('en_cours', 'en_pause', 'livre')
			  AND (p.porteur_id = :u OR EXISTS (SELECT 1 FROM membre_equipe m
			                                    WHERE m.projet_id = p.id AND m.utilisateur_id = :u AND m.parti_le IS NULL))
			""" + SANS_VISITEUR_TIERS;

	private record Ligne(UUID id, String titre, UUID porteur, Instant activite) {
	}

	private final JdbcClient base;

	private final DeploiementsEnLigne enLigne;

	PreuvesProjets(JdbcClient base, DeploiementsEnLigne enLigne) {
		this.base = base;
		this.enLigne = enLigne;
	}

	@Override
	public List<Projet> projets(UUID personne) {
		List<Ligne> lignes = sesProjets(personne);
		if (lignes.isEmpty()) {
			return List.of();
		}
		Map<UUID, DeploiementsEnLigne.ProjetEnLigne> enLigneParProjet = enLigne(lignes);
		Map<UUID, List<UUID>> equipes = base.sql("SELECT projet_id, utilisateur_id FROM membre_equipe WHERE projet_id IN (:p) AND parti_le IS NULL")
			.param("p", lignes.stream().map(Ligne::id).toList())
			.query((rs, n) -> Map.entry(rs.getObject("projet_id", UUID.class), rs.getObject("utilisateur_id", UUID.class))).list().stream()
			.collect(Collectors.groupingBy(Map.Entry::getKey, Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
		return lignes.stream().map(l -> {
			List<UUID> equipe = new ArrayList<>(List.of(l.porteur()));
			equipes.getOrDefault(l.id(), List.of()).stream().filter(u -> !u.equals(l.porteur())).forEach(equipe::add);
			var deploiement = enLigneParProjet.get(l.id());
			return new Projet(l.id(), l.titre(), personne.equals(l.porteur()) ? "porteur" : "membre", equipe, deploiement != null,
					deploiement == null ? null : deploiement.url(), deploiement == null ? null : deploiement.hebergeur().code(),
					deploiement == null ? null : deploiement.verifieLe(), l.activite());
		}).toList();
	}

	@Override
	public List<Point> points(UUID personne) {
		List<Point> points = new ArrayList<>();
		base.sql("""
				SELECT count(*) FILTER (WHERE t.livree_le::date <= t.echeance) AS a_temps, count(*) AS livrees
				FROM tache t JOIN projet p ON p.id = t.projet_id
				WHERE t.assignee_id = :u AND t.statut = 'livree' AND t.echeance IS NOT NULL
				""" + SANS_VISITEUR_TIERS).param("u", personne).param("visiteurs", Annuaire.VISITEURS_DEMO).query((rs, n) -> new Point(TACHE_LIVREE_A_TEMPS, rs.getInt("a_temps"), rs.getInt("livrees")))
			.optional().ifPresent(points::add);
		points.add(new Point(PROJET_EN_LIGNE, enLigne(sesProjets(personne)).size(), null));
		return points;
	}

	@Override
	public List<Preuve> preuves(UUID personne, String type, int limite) {
		if (TACHE_LIVREE_A_TEMPS.equals(type)) {
			return base.sql("""
					SELECT t.id, t.titre, t.livree_le, t.echeance FROM tache t JOIN projet p ON p.id = t.projet_id
					WHERE t.assignee_id = :u AND t.statut = 'livree' AND t.echeance IS NOT NULL AND t.livree_le::date <= t.echeance
					""" + SANS_VISITEUR_TIERS + " ORDER BY t.livree_le DESC LIMIT :n")
				.param("u", personne).param("n", limite).param("visiteurs", Annuaire.VISITEURS_DEMO)
				.query((rs, n) -> {
					Instant livree = rs.getTimestamp("livree_le").toInstant();
					LocalDate prevue = rs.getObject("echeance", LocalDate.class);
					String detail = "Livrée le " + JOUR.format(livree.atZone(java.time.ZoneOffset.UTC)) + ", prévue le " + JOUR.format(prevue);
					UUID id = rs.getObject("id", UUID.class);
					return new Preuve(id, type, "projets", id, rs.getString("titre"), detail, null, livree);
				}).list();
		}
		if (PROJET_EN_LIGNE.equals(type)) {
			List<Ligne> lignes = sesProjets(personne);
			var deploiements = enLigne(lignes);
			return lignes.stream().filter(l -> deploiements.containsKey(l.id())).map(l -> {
				var d = deploiements.get(l.id());
				return new Preuve(l.id(), type, "projets", l.id(), l.titre(), d.url(), null, d.misEnLigneLe());
			}).limit(limite).toList();
		}
		return List.of();
	}

	private List<Ligne> sesProjets(UUID personne) {
		return base.sql(SES_PROJETS).param("u", personne).param("visiteurs", Annuaire.VISITEURS_DEMO)
			.query((rs, n) -> new Ligne(rs.getObject("id", UUID.class), rs.getString("titre"), rs.getObject("porteur_id", UUID.class),
					instant(rs.getTimestamp("derniere_activite_le"))))
			.list();
	}

	/** Ses projets qui ont répondu à la dernière vérification du module deploiement. */
	private Map<UUID, DeploiementsEnLigne.ProjetEnLigne> enLigne(List<Ligne> lignes) {
		var siens = lignes.stream().map(Ligne::id).collect(Collectors.toSet());
		return enLigne.lister().stream().filter(d -> siens.contains(d.projetId()))
			.collect(Collectors.toMap(DeploiementsEnLigne.ProjetEnLigne::projetId, Function.identity(), (a, b) -> a));
	}

	private static Instant instant(Timestamp t) {
		return t == null ? null : t.toInstant();
	}

}
