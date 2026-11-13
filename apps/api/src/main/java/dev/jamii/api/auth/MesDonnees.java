package dev.jamii.api.auth;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Export et suppression d'un compte (loi sénégalaise 2008-12, docs/architecture.md). Chaque module apporte sa
 * section et sa règle de transmission par {@link DonneesPersonnelles}.
 */
@Service
class MesDonnees {

	private final List<DonneesPersonnelles> modules;

	private final UtilisateurRepository utilisateurs;

	private final JdbcClient base;

	MesDonnees(List<DonneesPersonnelles> modules, UtilisateurRepository utilisateurs, JdbcClient base) {
		var sections = new HashSet<String>();
		modules.forEach(m -> {
			if (!sections.add(m.section()) || "compte".equals(m.section()) || "exporteLe".equals(m.section())) {
				throw new IllegalStateException("Section d'export en double : " + m.section());
			}
		});
		this.modules = modules.stream().sorted(Comparator.comparing(DonneesPersonnelles::section)).toList();
		this.utilisateurs = utilisateurs;
		this.base = base;
	}

	/** Vide si le compte n'existe plus. Une seule transaction en lecture : l'export est cohérent. */
	@Transactional(readOnly = true)
	Optional<Map<String, Object>> exporter(UUID id, Instant maintenant) {
		return utilisateurs.findById(id).map(utilisateur -> {
			Map<String, Object> export = new LinkedHashMap<>();
			export.put("exporteLe", maintenant);
			export.put("compte", MoiController.Moi.depuis(utilisateur));
			modules.forEach(m -> export.put(m.section(), m.exporter(id)));
			return export;
		});
	}

	/**
	 * Tout ou rien, dans une seule transaction : chaque module transmet ce qui doit survivre, puis le compte est
	 * effacé avec ses jetons de connexion et toutes ses sessions (sur tous les appareils). Faux si le compte
	 * n'existait déjà plus.
	 */
	@Transactional
	boolean supprimer(UUID id, Instant maintenant) {
		var utilisateur = utilisateurs.findById(id);
		if (utilisateur.isEmpty()) {
			return false;
		}
		modules.forEach(m -> m.avantSuppression(id, maintenant));
		if (utilisateur.get().email() != null) {
			base.sql("DELETE FROM jeton_connexion WHERE email = lower(:email)").param("email", utilisateur.get().email()).update();
		}
		base.sql("DELETE FROM SPRING_SESSION WHERE PRINCIPAL_NAME = :nom").param("nom", id.toString()).update();
		// Les rôles, profils, adhésions, candidatures, demandes et appartenances partent en cascade ; les
		// publications et réponses restent, sans auteur (« compte supprimé »).
		base.sql("DELETE FROM utilisateur WHERE id = :id").param("id", id).update();
		return true;
	}

}
