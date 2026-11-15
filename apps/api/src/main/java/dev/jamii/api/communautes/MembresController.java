package dev.jamii.api.communautes;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import dev.jamii.api.auth.Annuaire;
import dev.jamii.api.auth.Compte;
import dev.jamii.api.auth.UtilisateurConnecte;

/**
 * Les membres d'une communauté (contracts/communautes.yaml v0.3) : les modérateurs, puis les actifs des 7 derniers
 * jours, puis les autres. Le nom et l'avatar viennent de l'annuaire, jamais l'e-mail.
 */
@RestController
class MembresController {

	record Membre(CommunautesController.PersonneResume personne, String role, Instant rejointLe, boolean actif) {
	}

	record Page(List<Membre> elements, long total) {
	}

	private record Ligne(UUID utilisateurId, String role, Instant rejointLe, boolean actif) {
	}

	private final GestionCommunautes communautes;

	private final JdbcClient base;

	private final Annuaire annuaire;

	MembresController(GestionCommunautes communautes, JdbcClient base, Annuaire annuaire) {
		this.communautes = communautes;
		this.base = base;
		this.annuaire = annuaire;
	}

	@GetMapping("/api/communautes/{slug}/membres")
	ResponseEntity<?> lister(@PathVariable String slug, @RequestParam(defaultValue = "50") String limite,
			@AuthenticationPrincipal UtilisateurConnecte moi) {
		Integer taille = taille(limite);
		if (taille == null) {
			return Problemes.validation("limite", "valeur_invalide");
		}
		Instant maintenant = Instant.now();
		var fiche = communautes.lire(slug, moi.id(), maintenant);
		if (fiche.isEmpty()) {
			return Problemes.introuvable();
		}
		UUID c = fiche.get().id();
		List<Ligne> lignes = base.sql("""
				SELECT a.utilisateur_id, a.role, a.rejoint_le,
				       (EXISTS (SELECT 1 FROM message_salon m JOIN salon s ON s.id = m.salon_id
				                WHERE s.communaute_id = a.communaute_id AND m.auteur_id = a.utilisateur_id AND m.envoye_le > :depuis)
				        OR EXISTS (SELECT 1 FROM publication p
				                   WHERE p.communaute_id = a.communaute_id AND p.auteur_id = a.utilisateur_id AND p.cree_le > :depuis)
				        OR EXISTS (SELECT 1 FROM reponse r JOIN publication p ON p.id = r.publication_id
				                   WHERE p.communaute_id = a.communaute_id AND r.auteur_id = a.utilisateur_id AND r.cree_le > :depuis)) AS actif
				FROM adhesion a
				WHERE a.communaute_id = :c
				ORDER BY (a.role = 'moderateur') DESC, actif DESC, a.rejoint_le DESC, a.utilisateur_id
				LIMIT :l
				""")
			.param("c", c).param("depuis", Timestamp.from(maintenant.minus(Duration.ofDays(7)))).param("l", taille)
			.query((rs, n) -> new Ligne(rs.getObject("utilisateur_id", UUID.class), rs.getString("role"),
					rs.getTimestamp("rejoint_le").toInstant(), rs.getBoolean("actif")))
			.list();
		long total = base.sql("SELECT count(*) FROM adhesion WHERE communaute_id = :c").param("c", c).query(Long.class).single();
		Map<UUID, Compte> comptes = annuaire.lire(lignes.stream().map(Ligne::utilisateurId).toList());
		List<Membre> membres = lignes.stream()
			.filter(l -> comptes.containsKey(l.utilisateurId()))
			.map(l -> new Membre(VuesPublications.personne(l.utilisateurId(), comptes), l.role(), l.rejointLe(), l.actif()))
			.toList();
		return ResponseEntity.ok(new Page(membres, total));
	}

	private static Integer taille(String limite) {
		try {
			int n = Integer.parseInt(limite);
			return n >= 1 && n <= 100 ? n : null;
		}
		catch (NumberFormatException e) {
			return null;
		}
	}

}
