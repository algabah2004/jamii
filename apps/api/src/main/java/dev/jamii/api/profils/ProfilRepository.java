package dev.jamii.api.profils;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ProfilRepository extends JpaRepository<Profil, UUID> {

}
