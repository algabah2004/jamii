package dev.jamii.api.profils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import dev.jamii.api.auth.Annuaire;

/**
 * Assemble les projets et les preuves d'une personne à partir des modules ({@link SourcePreuves}). L'ordre des points
 * suit le contrat (tâches, avis, réponses, projets en ligne) ; un type sans preuve n'apparaît pas : jamais de « 0 ».
 */
@Component
class Preuves {

	static final List<String> TYPES = List.of(SourcePreuves.TACHE_LIVREE_A_TEMPS, SourcePreuves.AVIS_COEQUIPIER,
			SourcePreuves.REPONSE_ACCEPTEE, SourcePreuves.PROJET_EN_LIGNE);

	static final int PROJETS_MAX = 6;

	/** Ce qu'un profil montre de son travail sur Jamii. */
	record Resume(List<Vues.ProjetDuProfil> projets, List<Vues.PointFiabilite> fiabilite) {

		static final Resume VIDE = new Resume(List.of(), List.of());
	}

	private final List<SourcePreuves> sources;

	private final JdbcClient base;

	private final Annuaire annuaire;

	Preuves(List<SourcePreuves> sources, JdbcClient base, Annuaire annuaire) {
		this.sources = sources;
		this.base = base;
		this.annuaire = annuaire;
	}

	Resume resume(UUID personne) {
		return new Resume(projets(personne), fiabilite(personne));
	}

	/** Les projets en ligne d'abord, puis les plus actifs. Les pays de l'équipe viennent des profils publiés. */
	private List<Vues.ProjetDuProfil> projets(UUID personne) {
		List<SourcePreuves.Projet> projets = sources.stream().flatMap(s -> s.projets(personne).stream())
			.sorted(Comparator.comparing(SourcePreuves.Projet::enLigne).reversed()
				.thenComparing(SourcePreuves.Projet::derniereActivite, Comparator.nullsLast(Comparator.reverseOrder())))
			.limit(PROJETS_MAX).toList();
		Map<UUID, String> pays = pays(projets.stream().flatMap(p -> p.equipe().stream()).collect(Collectors.toSet()));
		return projets.stream().map(p -> new Vues.ProjetDuProfil(p.projetId(), p.titre(), p.role(),
				p.equipe().stream().map(pays::get).filter(Objects::nonNull).collect(Collectors.toCollection(LinkedHashSet::new)).stream().toList(),
				p.enLigne(), p.url(), p.hebergeur(), p.verifieLe())).toList();
	}

	private List<Vues.PointFiabilite> fiabilite(UUID personne) {
		List<SourcePreuves.Point> points = sources.stream().flatMap(s -> s.points(personne).stream()).filter(p -> p.nombre() > 0).toList();
		List<Vues.PointFiabilite> resultat = new ArrayList<>();
		for (String type : TYPES) {
			points.stream().filter(p -> p.type().equals(type)).findFirst()
				.ifPresent(p -> resultat.add(new Vues.PointFiabilite(type, p.nombre(), p.sur(), 0)));
		}
		return resultat;
	}

	/** Le détail des preuves, les plus récentes d'abord. `apres` : combien sauter (le curseur de la page précédente). */
	List<Vues.Preuve> lister(UUID personne, String type, int apres, int limite) {
		List<SourcePreuves.Preuve> toutes = sources.stream()
			.flatMap(s -> (type == null ? TYPES : List.of(type)).stream().flatMap(t -> s.preuves(personne, t, apres + limite + 1).stream()))
			.sorted(Comparator.comparing(SourcePreuves.Preuve::creeLe, Comparator.nullsLast(Comparator.reverseOrder())))
			.skip(apres).limit(limite + 1L).toList();
		var auteurs = annuaire.lire(toutes.stream().map(SourcePreuves.Preuve::auteurId).filter(Objects::nonNull).toList());
		return toutes.stream().map(p -> new Vues.Preuve(p.id(), p.type(), new Vues.SourcePreuve(p.module(), p.sourceId(), p.titre()),
				p.detail(), p.auteurId() == null ? null : auteurs.containsKey(p.auteurId()) ? Vues.PersonneResume.depuis(auteurs.get(p.auteurId())) : null,
				false, p.creeLe())).toList();
	}

	private Map<UUID, String> pays(Collection<UUID> personnes) {
		if (personnes.isEmpty()) {
			return Map.of();
		}
		return base.sql("SELECT utilisateur_id, pays FROM profil WHERE utilisateur_id IN (:ids) AND publie AND pays IS NOT NULL")
			.param("ids", personnes).query((rs, n) -> Map.entry(rs.getObject("utilisateur_id", UUID.class), rs.getString("pays")))
			.list().stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
	}

}
