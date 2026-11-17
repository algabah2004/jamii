package dev.jamii.api.auth;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface UtilisateurRepository extends JpaRepository<Utilisateur, UUID> {

	Optional<Utilisateur> findByGithubId(long githubId);

	Optional<Utilisateur> findByEmailIgnoreCase(String email);

	long countByCreeLeGreaterThanEqual(java.time.Instant depuis);

	/** `motif` est déjà en minuscules, avec `%` et `_` échappés par `\`. */
	@Query("""
			SELECT u.id FROM Utilisateur u
			WHERE lower(u.nom) LIKE :motif ESCAPE '\\' OR lower(u.loginGithub) LIKE :motif ESCAPE '\\'
			""")
	List<UUID> idsDontLeNomContient(@Param("motif") String motif, Pageable page);

}
