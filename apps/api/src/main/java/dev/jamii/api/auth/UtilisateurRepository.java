package dev.jamii.api.auth;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface UtilisateurRepository extends JpaRepository<Utilisateur, UUID> {

	Optional<Utilisateur> findByGithubId(long githubId);

	Optional<Utilisateur> findByEmailIgnoreCase(String email);

	long countByCreeLeGreaterThanEqual(java.time.Instant depuis);

}
