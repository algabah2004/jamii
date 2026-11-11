package dev.jamii.api.auth;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface UtilisateurRepository extends JpaRepository<Utilisateur, UUID> {

	Optional<Utilisateur> findByGithubId(long githubId);

	long countByCreeLeGreaterThanEqual(java.time.Instant depuis);

}
