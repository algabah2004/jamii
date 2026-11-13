package dev.jamii.api.demandes;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.jamii.api.auth.Annuaire;
import dev.jamii.api.auth.Compte;
import dev.jamii.api.auth.Role;
import dev.jamii.api.auth.UtilisateurConnecte;
import dev.jamii.api.profils.ProfilsPublies;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Ce que Systalink gagne à Jamii (contracts/systalink.yaml v0.2) : les tendances du marché, anonymes, et les clients
 * qui ont demandé à recevoir ses offres. Les données vivent dans les demandes, d'où ce service dans ce module.
 * <p>
 * Une case de moins de {@value #SEUIL} besoins passe dans `autres` : personne ne peut être reconnu.
 */
@Service
class InfosSystalink {

	static final int SEUIL = 5;

	static final Set<String> STATUTS = Set.of("nouveau", "contacte", "client", "sans_suite");

	record Compteur(String cle, long nombre) {
	}

	record CompetenceManquante(String competence, long demandes, long profils) {
	}

	record Tendances(String periode, long besoins, List<Map<String, Object>> secteurs, List<Map<String, Object>> services,
			List<CompetenceManquante> competencesManquantes, List<Map<String, Object>> pays) {
	}

	record Contact(String nom, String email) {
	}

	record ClientSuivi(UUID demandeId, Instant creeLe, String resume, String secteur, String ville, String pays,
			List<String> services, boolean developpeursNecessaires, Contact contact, String statut) {
	}

	record Page(List<ClientSuivi> elements, String curseurSuivant) {
	}

	private final JdbcClient base;

	private final JsonMapper json;

	private final Annuaire annuaire;

	private final ProfilsPublies profils;

	private final Set<String> acces;

	InfosSystalink(JdbcClient base, JsonMapper json, Annuaire annuaire, ProfilsPublies profils,
			@Value("${jamii.clients-systalink.acces:}") String acces) {
		this.base = base;
		this.json = json;
		this.annuaire = annuaire;
		this.profils = profils;
		this.acces = java.util.Arrays.stream(acces.split(","))
			.map(a -> a.strip().toLowerCase(Locale.ROOT))
			.filter(a -> !a.isEmpty())
			.collect(Collectors.toUnmodifiableSet());
	}

	/** Le rôle `systalink` : un admin, ou un login GitHub ou un e-mail listé dans `SYSTALINK_ACCES`. */
	boolean aAcces(UtilisateurConnecte moi) {
		if (moi.roles().contains(Role.ADMIN)) {
			return true;
		}
		if (acces.isEmpty()) {
			return false;
		}
		Optional<String> login = annuaire.lire(moi.id()).map(Compte::loginGithub);
		Optional<String> email = annuaire.emailDeContact(moi.id());
		return login.map(l -> acces.contains(l.toLowerCase(Locale.ROOT))).orElse(false)
				|| email.map(e -> acces.contains(e.toLowerCase(Locale.ROOT))).orElse(false);
	}

	@Transactional(readOnly = true)
	Tendances tendances(String periode, Instant depuis) {
		List<JsonNode> orientations = new ArrayList<>();
		List<String> paysListe = new ArrayList<>();
		base.sql("SELECT orientation, pays FROM demande WHERE orientation IS NOT NULL AND (CAST(:depuis AS timestamptz) IS NULL OR cree_le >= :depuis)")
			.param("depuis", depuis == null ? null : Timestamp.from(depuis), java.sql.Types.TIMESTAMP)
			.query(rs -> {
				orientations.add(json.readTree(rs.getString("orientation")));
				paysListe.add(rs.getString("pays"));
			});
		Map<String, Long> secteurs = new LinkedHashMap<>();
		Map<String, Long> services = new LinkedHashMap<>();
		Map<String, Long> competences = new LinkedHashMap<>();
		for (JsonNode o : orientations) {
			secteurs.merge(o.path("secteur").asString("autre"), 1L, Long::sum);
			o.path("services").forEach(s -> services.merge(s.asString(""), 1L, Long::sum));
			o.path("competences").forEach(c -> competences.merge(c.asString(""), 1L, Long::sum));
		}
		Map<String, Long> pays = new LinkedHashMap<>();
		paysListe.forEach(p -> pays.merge(p == null ? "autres" : p, 1L, Long::sum));
		List<String> plusDemandees = trier(competences).stream().limit(10).map(Compteur::cle).toList();
		Map<String, Long> profilsParCompetence = profils.compterParCompetence(plusDemandees);
		List<CompetenceManquante> manquantes = plusDemandees.stream()
			.map(c -> new CompetenceManquante(c, competences.get(c), profilsParCompetence.getOrDefault(c, 0L)))
			.toList();
		return new Tendances(periode, orientations.size(), regrouper(secteurs, "secteur", "besoins"),
				trier(services).stream().filter(c -> !c.cle().isEmpty())
					.map(c -> Map.<String, Object>of("service", c.cle(), "suggestions", c.nombre())).toList(),
				manquantes, regrouper(pays, "pays", "besoins"));
	}

	/** Les clients suivis, les plus récents d'abord. Le curseur est opaque pour le front. */
	@Transactional(readOnly = true)
	Page clientsSuivis(String statut, String curseur, int limite) {
		Instant apresDate = null;
		UUID apresId = null;
		if (curseur != null && !curseur.isBlank()) {
			String[] morceaux = new String(Base64.getUrlDecoder().decode(curseur), StandardCharsets.UTF_8).split("\\|");
			apresDate = Instant.parse(morceaux[0]);
			apresId = UUID.fromString(morceaux[1]);
		}
		record Ligne(UUID id, UUID auteur, Instant creeLe, String ville, String pays, String statut, JsonNode orientation) {
		}
		List<Ligne> lignes = base.sql("""
				SELECT id, auteur_id, cree_le, ville, pays, suivi_statut, orientation FROM demande
				WHERE offres_systalink AND orientation IS NOT NULL
				  AND (CAST(:statut AS varchar) IS NULL OR suivi_statut = :statut)
				  AND (CAST(:date AS timestamptz) IS NULL OR cree_le < :date OR (cree_le = :date AND id > :id))
				ORDER BY cree_le DESC, id LIMIT :limite
				""")
			.param("statut", statut, java.sql.Types.VARCHAR)
			.param("date", apresDate == null ? null : Timestamp.from(apresDate), java.sql.Types.TIMESTAMP)
			.param("id", apresId == null ? new UUID(0, 0) : apresId)
			.param("limite", limite + 1)
			.query((rs, n) -> new Ligne(rs.getObject("id", UUID.class), rs.getObject("auteur_id", UUID.class),
					rs.getTimestamp("cree_le").toInstant(), rs.getString("ville"), rs.getString("pays"),
					rs.getString("suivi_statut"), json.readTree(rs.getString("orientation"))))
			.list();
		boolean suite = lignes.size() > limite;
		List<Ligne> page = suite ? lignes.subList(0, limite) : lignes;
		Map<UUID, Compte> comptes = annuaire.lire(page.stream().map(Ligne::auteur).toList());
		List<ClientSuivi> clients = page.stream()
			.map(l -> enClient(l.id(), l.creeLe(), l.ville(), l.pays(), l.statut(), l.orientation(), comptes.get(l.auteur()),
					l.auteur()))
			.toList();
		String suivant = null;
		if (suite) {
			Ligne derniere = page.getLast();
			suivant = Base64.getUrlEncoder().withoutPadding()
				.encodeToString((derniere.creeLe() + "|" + derniere.id()).getBytes(StandardCharsets.UTF_8));
		}
		return new Page(clients, suivant);
	}

	/** Le client suivi d'une demande, seulement si l'accord est donné. */
	@Transactional(readOnly = true)
	Optional<ClientSuivi> client(UUID demandeId) {
		return base.sql("""
				SELECT id, auteur_id, cree_le, ville, pays, suivi_statut, orientation FROM demande
				WHERE id = :id AND offres_systalink AND orientation IS NOT NULL
				""")
			.param("id", demandeId)
			.query((rs, n) -> {
				UUID auteur = rs.getObject("auteur_id", UUID.class);
				return enClient(rs.getObject("id", UUID.class), rs.getTimestamp("cree_le").toInstant(), rs.getString("ville"),
						rs.getString("pays"), rs.getString("suivi_statut"), json.readTree(rs.getString("orientation")),
						annuaire.lire(auteur).orElse(null), auteur);
			})
			.optional();
	}

	@Transactional
	boolean noterSuivi(UUID demandeId, String statut) {
		return base.sql("UPDATE demande SET suivi_statut = :s WHERE id = :id AND offres_systalink AND orientation IS NOT NULL")
			.param("s", statut).param("id", demandeId).update() == 1;
	}

	private ClientSuivi enClient(UUID id, Instant creeLe, String ville, String pays, String statut, JsonNode orientation,
			Compte compte, UUID auteur) {
		List<String> services = new ArrayList<>();
		orientation.path("services").forEach(s -> services.add(s.asString("")));
		Contact contact = new Contact(compte == null ? "" : compte.nom(), annuaire.emailDeContact(auteur).orElse(null));
		return new ClientSuivi(id, creeLe, orientation.path("resume").asString(""), orientation.path("secteur").asString(null),
				ville, pays, services, orientation.path("developpeursNecessaires").asBoolean(false), contact, statut);
	}

	private static List<Compteur> trier(Map<String, Long> nombres) {
		return nombres.entrySet().stream()
			.map(e -> new Compteur(e.getKey(), e.getValue()))
			.sorted((a, b) -> Long.compare(b.nombre(), a.nombre()))
			.toList();
	}

	/** Moins de {@value #SEUIL} : dans `autres`, pour que personne ne puisse être reconnu. */
	private static List<Map<String, Object>> regrouper(Map<String, Long> nombres, String cle, String valeur) {
		List<Map<String, Object>> liste = new ArrayList<>();
		long autres = 0;
		for (Compteur c : trier(nombres)) {
			if (c.nombre() < SEUIL || "autres".equals(c.cle())) {
				autres += c.nombre();
			}
			else {
				liste.add(Map.of(cle, c.cle(), valeur, c.nombre()));
			}
		}
		if (autres > 0) {
			liste.add(Map.of(cle, "autres", valeur, autres));
		}
		return liste;
	}

	static String csv(Collection<ClientSuivi> clients) {
		StringBuilder sortie = new StringBuilder(
				"demandeId,creeLe,resume,secteur,ville,pays,services,developpeursNecessaires,nom,email,statut\r\n");
		for (ClientSuivi c : clients) {
			sortie.append(String.join(",", cellule(c.demandeId().toString()), cellule(c.creeLe().toString()),
					cellule(c.resume()), cellule(c.secteur()), cellule(c.ville()), cellule(c.pays()),
					cellule(String.join(" ", c.services())), cellule(String.valueOf(c.developpeursNecessaires())),
					cellule(c.contact().nom()), cellule(c.contact().email()), cellule(c.statut())))
				.append("\r\n");
		}
		return sortie.toString();
	}

	/** Échappée, et neutralisée contre l'injection de formules dans un tableur (`=`, `+`, `-`, `@`). */
	static String cellule(String valeur) {
		if (valeur == null) {
			return "";
		}
		String sure = !valeur.isEmpty() && "=+-@\t\r".indexOf(valeur.charAt(0)) >= 0 ? "'" + valeur : valeur;
		return "\"" + sure.replace("\"", "\"\"") + "\"";
	}

}
