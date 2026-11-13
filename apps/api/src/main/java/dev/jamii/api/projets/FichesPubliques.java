package dev.jamii.api.projets;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jamii.api.auth.Annuaire;
import dev.jamii.api.auth.Compte;

/**
 * Le service public des projets pour la vitrine (demande #67) : titre, accroche, origine et équipe de plusieurs
 * projets, en deux requêtes. Un projet inconnu est simplement absent du résultat.
 */
@Service
public class FichesPubliques {

	/** Longueur maximale de l'accroche (contracts/vitrine.yaml). */
	public static final int ACCROCHE_MAX = 280;

	private record Membre(UUID projetId, UUID utilisateurId, Instant rejointLe) {
	}

	private final JdbcClient base;

	private final Annuaire annuaire;

	FichesPubliques(JdbcClient base, Annuaire annuaire) {
		this.base = base;
		this.annuaire = annuaire;
	}

	@Transactional(readOnly = true)
	public Map<UUID, FichePublique> lire(Collection<UUID> ids) {
		Map<UUID, FichePublique> fiches = new LinkedHashMap<>();
		if (ids.isEmpty()) {
			return fiches;
		}
		Map<UUID, String[]> projets = new HashMap<>();
		Map<UUID, UUID> porteurs = new HashMap<>();
		base.sql("SELECT id, titre, description, origine, porteur_id FROM projet WHERE id IN (:ids)")
			.param("ids", ids)
			.query((rs, n) -> {
				UUID id = rs.getObject("id", UUID.class);
				projets.put(id, new String[] { rs.getString("titre"), rs.getString("description"), rs.getString("origine") });
				porteurs.put(id, rs.getObject("porteur_id", UUID.class));
				return id;
			})
			.list();
		List<Membre> membres = base.sql("""
				SELECT projet_id, utilisateur_id, rejoint_le FROM membre_equipe
				WHERE projet_id IN (:ids) AND parti_le IS NULL
				""")
			.param("ids", projets.keySet())
			.query((rs, n) -> new Membre(rs.getObject("projet_id", UUID.class), rs.getObject("utilisateur_id", UUID.class),
					rs.getTimestamp("rejoint_le").toInstant()))
			.list();
		Map<UUID, Compte> comptes = annuaire.lire(membres.stream().map(Membre::utilisateurId).distinct().toList());
		for (UUID id : ids) {
			String[] projet = projets.get(id);
			if (projet == null) {
				continue;
			}
			UUID porteur = porteurs.get(id);
			List<FichePublique.Personne> equipe = new ArrayList<>();
			membres.stream()
				.filter(m -> m.projetId().equals(id) && comptes.containsKey(m.utilisateurId()))
				.sorted(Comparator.comparing((Membre m) -> !m.utilisateurId().equals(porteur))
					.thenComparing(Membre::rejointLe).thenComparing(Membre::utilisateurId))
				.forEach(m -> {
					Compte c = comptes.get(m.utilisateurId());
					equipe.add(new FichePublique.Personne(c.id(), c.nom(), c.avatarUrl()));
				});
			fiches.put(id, new FichePublique(id, projet[0], accroche(projet[1]), projet[2], List.copyOf(equipe)));
		}
		return fiches;
	}

	/** Texte brut sur une ligne, d'au plus 280 caractères, coupé entre deux mots et terminé par « … » s'il est raccourci. */
	static String accroche(String description) {
		String texte = description == null ? "" : description.replaceAll("\\s+", " ").strip();
		if (texte.length() <= ACCROCHE_MAX) {
			return texte;
		}
		String debut = texte.substring(0, ACCROCHE_MAX - 1);
		int espace = debut.lastIndexOf(' ');
		// Un seul mot géant, sans espace : on coupe net plutôt que de renvoyer une accroche vide.
		String coupe = espace > ACCROCHE_MAX / 2 ? debut.substring(0, espace) : debut;
		// Pas de ponctuation orpheline avant les points de suspension.
		return coupe.replaceAll("[\\s,;:.!?-]+$", "") + "…";
	}

}
