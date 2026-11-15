package dev.jamii.api.communautes;

import java.net.URI;
import java.sql.Timestamp;
import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.security.SecureRandom;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import dev.jamii.api.auth.UtilisateurConnecte;

/**
 * Créer une communauté, et y créer des salons (contracts/communautes.yaml v0.4). Qui crée une communauté en devient le
 * modérateur ; seuls les modérateurs créent des salons. Les droits et les limites sont vérifiés ici, à chaque appel.
 */
@RestController
class CreationCommunautes {

	static final int COMMUNAUTES_PAR_JOUR = 3;

	static final int SALONS_MAX = 20;

	static final Set<String> THEMES = Set.of("flutter", "fintech", "ia", "web", "cloud", "design", "data", "jeux", "securite",
			"antenne", "autre");

	/** Les salons de départ de toute communauté, comme ceux de la migration. */
	private static final List<String[]> SALONS_DE_DEPART = List.of(
			new String[] { "general", "général", "Se présenter et parler de tout." },
			new String[] { "entraide", "entraide", "Une question rapide, un blocage : quelqu'un t'aide." },
			new String[] { "projets", "projets", "Montrer ce qu'on construit et trouver des coéquipiers." });

	record NouvelleCommunaute(String nom, String description, String theme, String pays) {
	}

	record NouveauSalon(String nom, String sujet) {
	}

	private static final SecureRandom HASARD = new SecureRandom();

	private final JdbcClient base;

	private final GestionCommunautes communautes;

	private final String appels;

	CreationCommunautes(JdbcClient base, GestionCommunautes communautes,
			@Value("${jamii.appels.url:https://meet.jit.si}") String appels) {
		this.base = base;
		this.communautes = communautes;
		this.appels = appels.endsWith("/") ? appels.substring(0, appels.length() - 1) : appels;
	}

	@PostMapping("/api/communautes")
	@Transactional
	ResponseEntity<?> creer(@RequestBody NouvelleCommunaute demande, @AuthenticationPrincipal UtilisateurConnecte moi) {
		String nom = demande.nom() == null ? "" : demande.nom().strip();
		String description = demande.description() == null ? "" : demande.description().strip();
		List<Map<String, String>> erreurs = new ArrayList<>();
		if (nom.length() < 3 || nom.length() > 80 || nomCourt(nom, 50).isEmpty()) {
			erreurs.add(Map.of("champ", "nom", "code", "longueur_invalide"));
		}
		if (description.length() < 10 || description.length() > 500) {
			erreurs.add(Map.of("champ", "description", "code", "longueur_invalide"));
		}
		if (demande.theme() == null || !THEMES.contains(demande.theme())) {
			erreurs.add(Map.of("champ", "theme", "code", "valeur_invalide"));
		}
		if (demande.pays() != null && !demande.pays().matches("[A-Z]{2}")) {
			erreurs.add(Map.of("champ", "pays", "code", "valeur_invalide"));
		}
		if (!erreurs.isEmpty()) {
			return Problemes.probleme(HttpStatus.BAD_REQUEST, "validation", "Certains champs sont invalides", erreurs);
		}
		Instant maintenant = Instant.now();
		List<Instant> creees = base.sql("""
				SELECT cree_le FROM communaute WHERE createur_id = :u AND cree_le > :depuis ORDER BY cree_le
				""").param("u", moi.id()).param("depuis", Timestamp.from(maintenant.minus(Duration.ofDays(1))))
			.query((rs, n) -> rs.getTimestamp("cree_le").toInstant()).list();
		if (creees.size() >= COMMUNAUTES_PAR_JOUR) {
			long attente = Math.max(1, Duration.between(maintenant, creees.getFirst().plus(Duration.ofDays(1))).toSeconds() + 1);
			return Problemes.tropDeRequetes(attente, "Trop de communautés créées aujourd'hui");
		}

		String slug = libre(nomCourt(nom, 50), s -> base.sql("SELECT EXISTS (SELECT 1 FROM communaute WHERE slug = :s)")
			.param("s", s).query(Boolean.class).single());
		UUID id = UUID.randomUUID();
		base.sql("""
				INSERT INTO communaute (id, slug, nom, description, theme, pays, createur_id, cree_le)
				VALUES (:id, :slug, :nom, :description, :theme, :pays, :u, :t)
				""").param("id", id).param("slug", slug).param("nom", nom).param("description", description)
			.param("theme", demande.theme()).param("pays", demande.pays()).param("u", moi.id())
			.param("t", Timestamp.from(maintenant)).update();
		base.sql("""
				INSERT INTO adhesion (communaute_id, utilisateur_id, role, rejoint_le) VALUES (:c, :u, 'moderateur', :t)
				""").param("c", id).param("u", moi.id()).param("t", Timestamp.from(maintenant)).update();
		int ordre = 1;
		for (String[] s : SALONS_DE_DEPART) {
			ajouterSalon(id, slug, s[0], s[1], s[2], ordre++);
		}
		var fiche = communautes.lire(slug, moi.id(), maintenant).orElseThrow();
		return ResponseEntity.created(URI.create("/api/communautes/" + slug)).body(CommunautesController.communaute(fiche));
	}

	@PostMapping("/api/communautes/{slug}/salons")
	@Transactional
	ResponseEntity<?> creerSalon(@PathVariable String slug, @RequestBody NouveauSalon demande,
			@AuthenticationPrincipal UtilisateurConnecte moi) {
		String nom = demande.nom() == null ? "" : demande.nom().strip().replaceFirst("^#\\s*", "");
		String sujet = demande.sujet() == null ? "" : demande.sujet().strip();
		String court = nomCourt(nom, 40);
		List<Map<String, String>> erreurs = new ArrayList<>();
		if (nom.length() < 2 || nom.length() > 40 || court.isEmpty()) {
			erreurs.add(Map.of("champ", "nom", "code", "longueur_invalide"));
		}
		if (sujet.length() < 5 || sujet.length() > 200) {
			erreurs.add(Map.of("champ", "sujet", "code", "longueur_invalide"));
		}
		if (!erreurs.isEmpty()) {
			return Problemes.probleme(HttpStatus.BAD_REQUEST, "validation", "Certains champs sont invalides", erreurs);
		}
		var fiche = communautes.lire(slug, moi.id(), Instant.now());
		if (fiche.isEmpty()) {
			return Problemes.introuvable();
		}
		if (!fiche.get().moderateur()) {
			return Problemes.interdit("pas_moderateur", "Seuls les modérateurs créent des salons");
		}
		UUID c = fiche.get().id();
		long nombre = base.sql("SELECT count(*) FROM salon WHERE communaute_id = :c").param("c", c).query(Long.class).single();
		if (nombre >= SALONS_MAX) {
			return Problemes.conflit("trop_de_salons", "Cette communauté a déjà " + SALONS_MAX + " salons");
		}
		boolean existe = base.sql("SELECT EXISTS (SELECT 1 FROM salon WHERE communaute_id = :c AND slug = :s)")
			.param("c", c).param("s", court).query(Boolean.class).single();
		if (existe) {
			return Problemes.conflit("salon_existe", "Un salon porte déjà ce nom");
		}
		int ordre = base.sql("SELECT coalesce(max(ordre), 0) + 1 FROM salon WHERE communaute_id = :c").param("c", c)
			.query(Integer.class).single();
		Salons.Salon salon = ajouterSalon(c, slug, court, nom.toLowerCase(Locale.ROOT), sujet, ordre);
		return ResponseEntity.status(HttpStatus.CREATED)
			.body(new SalonsController.Salon(salon.id(), salon.slug(), salon.nom(), salon.sujet(), appels + "/" + salon.salleAppel()));
	}

	private Salons.Salon ajouterSalon(UUID communaute, String communauteSlug, String slug, String nom, String sujet, int ordre) {
		UUID id = UUID.randomUUID();
		String salle = "jamii-" + communauteSlug + "-" + slug + "-" + HexFormat.of().formatHex(aleatoire(6));
		base.sql("""
				INSERT INTO salon (id, communaute_id, slug, nom, sujet, ordre, salle_appel) VALUES (:id, :c, :slug, :nom, :sujet, :o, :salle)
				""").param("id", id).param("c", communaute).param("slug", slug).param("nom", nom).param("sujet", sujet)
			.param("o", ordre).param("salle", salle).update();
		return new Salons.Salon(id, slug, nom, sujet, salle);
	}

	/** « Devs du Sahel ! » → « devs-du-sahel » : sans accents, minuscules, des tirets, au plus `max` caractères. */
	static String nomCourt(String nom, int max) {
		String sansAccents = Normalizer.normalize(nom, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
		String court = sansAccents.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
		if (court.length() > max) {
			court = court.substring(0, max).replaceAll("-+$", "");
		}
		return court;
	}

	/** Le premier nom court libre : « devs-du-sahel », puis « devs-du-sahel-2 »… */
	private static String libre(String court, java.util.function.Predicate<String> pris) {
		if (!pris.test(court)) {
			return court;
		}
		for (int i = 2;; i++) {
			String essai = court.substring(0, Math.min(court.length(), 56)) + "-" + i;
			if (!pris.test(essai)) {
				return essai;
			}
		}
	}

	private static byte[] aleatoire(int n) {
		byte[] octets = new byte[n];
		HASARD.nextBytes(octets);
		return octets;
	}

}
