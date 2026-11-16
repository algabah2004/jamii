package dev.jamii.api.demandes;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Les demandes et leur orientation (contracts/demandes.yaml v0.2). L'orientation complète (et son plan) vit dans le
 * service IA, relue à chaque lecture. Seul son résumé est recopié une fois terminée, pour les tendances et les clients
 * suivis de Systalink. Le texte de l'auteur, lui, est toujours conservé, même si l'IA est en panne.
 */
@Service
class GestionDemandes {

	/** 4 nouvelles demandes par heure et par utilisateur : contre les rafales, en plus du quota du jour. */
	static final int PLANS_PAR_HEURE = 4;

	/** Au-delà, un service IA qui ne répond toujours pas fait passer l'orientation à « échouée ». */
	/**
	 * Au-delà, un service IA qui ne répond pas cède la place à une orientation de secours calculée ici, sans IA
	 * (`origine: repli`, annoncée comme telle) : la personne n'attend jamais devant un échec (docs/priorites.md).
	 */
	static final Duration SECOURS_APRES = Duration.ofSeconds(20);

	/** Les réponses de la personne dans une conversation, après son premier message. */
	static final int PRECISIONS_MAX = 12;

	/** Ce que l'IA lit de la conversation : le contrat ia.yaml accepte 8 000 caractères. */
	static final int CONVERSATION_MAX = 8000;

	/**
	 * 20 messages à Ayo par jour et par personne (un premier message ou une réponse chacun, sur 24 heures glissantes) :
	 * de quoi tenir plusieurs conversations, avec leurs questions, sans vider le budget IA du jour des autres.
	 */
	static final int ANALYSES_PAR_JOUR = 20;

	/** Une adresse e-mail ou un numéro de téléphone n'est jamais envoyé à l'IA. */
	private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+");

	private static final Pattern TELEPHONE = Pattern.compile("\\+?\\d[\\d .-]{7,}\\d");

	private static final Logger journal = LoggerFactory.getLogger(GestionDemandes.class);

	record Fiche(UUID id, UUID auteurId, String titre, String description, Integer dureeSemaines, String secteur,
			String ville, String pays, boolean offresSystalink, UUID tacheIaId, JsonNode orientation, Instant creeLe,
			Instant publieeLe, String publieeResume, List<Precision> precisions, String langue) {

		/** Le début de l'analyse en cours : la création, ou la dernière précision. */
		Instant analyseDepuis() {
			return precisions.isEmpty() ? creeLe : precisions.getLast().creeLe();
		}

		/**
		 * Une clé d'idempotence par version du besoin : la même tant que rien n'est ajouté (un dépôt arrivé sans qu'on
		 * le sache donne la même tâche), une nouvelle à chaque précision.
		 */
		UUID cleIa() {
			return precisions.isEmpty() ? id
					: UUID.nameUUIDFromBytes((id + ":" + precisions.size()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
		}

		/**
		 * La conversation telle que l'IA la lit : le premier message, puis chaque échange, une ligne par prise de parole.
		 * Ayo voit ainsi ce qu'il a déjà dit et demandé (`Ayo (question)`), et ne repose pas la même question. Au-delà de
		 * 8 000 caractères, le début du premier message est raccourci : la fin de la conversation compte le plus.
		 */
		String besoinComplet() {
			if (precisions.isEmpty()) {
				return description;
			}
			StringBuilder suite = new StringBuilder("\n\nSuite de la conversation :");
			for (Precision p : precisions) {
				if (p.ayo() != null && !p.ayo().isBlank()) {
					String ayo = p.ayo().replaceAll("\\s+", " ").strip();
					suite.append(p.ayoQuestion() ? "\nAyo (question) : " : "\nAyo : ")
						.append(ayo.length() > 400 ? ayo.substring(0, 400) + "…" : ayo);
				}
				suite.append("\nPersonne : ").append(p.texte().replaceAll("\\s+", " ").strip());
			}
			int place = Math.max(20, CONVERSATION_MAX - suite.length());
			return (description.length() > place ? description.substring(0, place) : description) + suite;
		}

	}

	/** Une réponse de la personne, avec ce qu'Ayo venait de lui dire (vide pour les plus anciennes). */
	record Precision(String texte, Instant creeLe, String ayo, boolean ayoQuestion) {
	}

	/** Une ligne de « Tes demandes ». */
	record Resume(UUID id, String titre, String resume, Instant creeLe, int precisions, boolean publiee) {
	}

	/** Ce que l'auteur ajoute à son besoin, tout facultatif. */
	record Contexte(String secteur, String ville, String pays, boolean offresSystalink, String langue) {

		Contexte(String secteur, String ville, String pays, boolean offresSystalink) {
			this(secteur, ville, pays, offresSystalink, "fr");
		}
	}

	/** L'état de l'orientation, tel que le service IA le décrit. Les champs absents ne sont pas écrits. */
	/**
	 * `secours` : l'orientation a été calculée ici parce que le service IA ne répondait pas. Elle se lit, mais ne se publie
	 * pas : ses compétences sont génériques, et une publication est définitive (revue de @sowibrr sur #263).
	 */
	record Etat(String statut, String origine, JsonNode resultat, String erreurCode, boolean secours) {

		Etat(String statut, String origine, JsonNode resultat, String erreurCode) {
			this(statut, origine, resultat, erreurCode, false);
		}

	}

	/** Trop de demandes : à reprendre dans `attenteSecondes`. */
	static final class TropDePlans extends RuntimeException {

		final long attenteSecondes;

		/** Vrai quand c'est le quota du jour, pas la limite de l'heure : le front le dit autrement. */
		final boolean duJour;

		TropDePlans(long attenteSecondes) {
			this(attenteSecondes, false);
		}

		TropDePlans(long attenteSecondes, boolean duJour) {
			super(null, null, false, false);
			this.attenteSecondes = attenteSecondes;
			this.duJour = duJour;
		}

	}

	private final JdbcClient base;

	private final PlansIa ia;

	private final JsonMapper json;

	/** L'orientation de secours, sans IA ni réseau : la même que celle du fournisseur simulé, sans attente. */
	private final PlansIaSimules secours;

	GestionDemandes(JdbcClient base, PlansIa ia, JsonMapper json) {
		this.base = base;
		this.ia = ia;
		this.json = json;
		this.secours = new PlansIaSimules(Duration.ZERO, java.time.Clock.systemUTC(), json);
	}

	/** Enregistre la demande si l'auteur n'a pas dépassé sa limite. Une demande refusée n'est pas enregistrée. */
	@Transactional
	UUID creer(UUID auteur, String titre, String description, Integer dureeSemaines, Contexte contexte,
			Instant maintenant) {
		List<Instant> recentes = base.sql("SELECT cree_le FROM demande WHERE auteur_id = :a AND cree_le > :depuis ORDER BY cree_le")
			.param("a", auteur)
			.param("depuis", Timestamp.from(maintenant.minus(Duration.ofHours(1))))
			.query((rs, n) -> rs.getTimestamp("cree_le").toInstant())
			.list();
		if (recentes.size() >= PLANS_PAR_HEURE) {
			throw new TropDePlans(Math.max(1, Duration.between(maintenant, recentes.getFirst().plus(Duration.ofHours(1))).toSeconds()));
		}
		verifierQuotaDuJour(auteur, maintenant);
		UUID id = UUID.randomUUID();
		base.sql("""
				INSERT INTO demande (id, auteur_id, titre, description, duree_semaines, secteur, ville, pays,
				  offres_systalink, offres_accord_le, cree_le, langue)
				VALUES (:id, :auteur, :titre, :description, :duree, :secteur, :ville, :pays, :offres, :accord, :t, :langue)
				""")
			.param("id", id).param("auteur", auteur).param("titre", titre).param("description", description)
			.param("duree", dureeSemaines, java.sql.Types.SMALLINT)
			.param("secteur", contexte.secteur(), java.sql.Types.VARCHAR)
			.param("ville", contexte.ville(), java.sql.Types.VARCHAR)
			.param("pays", contexte.pays(), java.sql.Types.CHAR)
			.param("offres", contexte.offresSystalink())
			.param("accord", contexte.offresSystalink() ? Timestamp.from(maintenant) : null, java.sql.Types.TIMESTAMP)
			.param("t", Timestamp.from(maintenant)).param("langue", contexte.langue()).update();
		return id;
	}

	/** La demande, si elle existe ET appartient à cet utilisateur : pour les autres, elle n'existe pas. */
	@Transactional(readOnly = true)
	Optional<Fiche> lire(UUID id, UUID auteur) {
		return base.sql("""
				SELECT id, auteur_id, titre, description, duree_semaines, secteur, ville, pays, offres_systalink,
				  tache_ia_id, orientation, cree_le, publiee_le, publiee_resume, langue
				FROM demande WHERE id = :id AND auteur_id = :auteur
				""")
			.param("id", id).param("auteur", auteur)
			.query((rs, n) -> {
				int duree = rs.getInt("duree_semaines");
				Integer dureeSemaines = rs.wasNull() ? null : duree;
				String orientation = rs.getString("orientation");
				Timestamp publiee = rs.getTimestamp("publiee_le");
				return new Fiche(rs.getObject("id", UUID.class), rs.getObject("auteur_id", UUID.class), rs.getString("titre"),
						rs.getString("description"), dureeSemaines, rs.getString("secteur"), rs.getString("ville"),
						rs.getString("pays"), rs.getBoolean("offres_systalink"), rs.getObject("tache_ia_id", UUID.class),
						orientation == null ? null : json.readTree(orientation), rs.getTimestamp("cree_le").toInstant(),
						publiee == null ? null : publiee.toInstant(), rs.getString("publiee_resume"), List.<Precision>of(), rs.getString("langue"));
			})
			.optional()
			.map(f -> new Fiche(f.id(), f.auteurId(), f.titre(), f.description(), f.dureeSemaines(), f.secteur(), f.ville(),
					f.pays(), f.offresSystalink(), f.tacheIaId(), f.orientation(), f.creeLe(), f.publieeLe(), f.publieeResume(),
						precisions(f.id()), f.langue()));
	}

	/**
	 * L'état de l'orientation. Sans transaction : on n'attend jamais le service IA en tenant une connexion à la base.
	 * Service injoignable : `en_attente` pendant 20 secondes (on réessaie à chaque lecture), puis une orientation de
	 * secours sans IA (`repli`). Chaque lecture retente quand même le service : dès que l'IA revient, elle reprend la main.
	 */
	Etat orientation(Fiche demande, Instant maintenant) {
		Duration attente = Duration.between(demande.analyseDepuis(), maintenant);
		PlansIa.Entree entree = new PlansIa.Entree(demande.titre(), sansCoordonnees(demande.besoinComplet()),
				demande.dureeSemaines(), demande.secteur(), demande.pays(), demande.langue());
		try {
			UUID tache = demande.tacheIaId();
			if (tache == null) {
				// La même clé d'idempotence : si un dépôt précédent est arrivé sans qu'on le sache, c'est la même tâche.
				tache = ia.deposer(demande.cleIa(), entree);
				retenirTache(demande.id(), tache);
			}
			PlansIa.Etat etat = ia.lire(tache);
			if ("terminee".equals(etat.statut()) && etat.resultat() != null) {
				retenirAyo(demande.id(), etat.resultat());
				// Une question ou une simple discussion n'est pas un conseil : rien ne part vers les tendances ni vers Systalink.
				if (demande.orientation() == null && (conseille(etat.resultat()) || "repli".equals(etat.origine()))) {
					retenirResume(demande.id(), etat.resultat());
				}
			}
			return new Etat(etat.statut(), etat.origine(), etat.resultat(), etat.erreurCode());
		}
		catch (RuntimeException e) {
			// Ni le texte de l'auteur ni le jeton dans les journaux : seulement le type de l'erreur.
			journal.warn("Service IA injoignable pour une demande : {}", e.getClass().getSimpleName());
			if (attente.compareTo(SECOURS_APRES) < 0) {
				return new Etat("en_attente", null, null, null);
			}
			// Le secours n'est pas enregistré : dès que l'IA revient, la lecture suivante lui redonne la main.
			PlansIa.Etat repli = secours.lire(secours.deposer(demande.cleIa(), entree));
			return new Etat(repli.statut(), "repli", repli.resultat(), null, true);
		}
	}

	/** Accorder ou retirer « Recevoir les offres Systalink adaptées à mon projet ». Faux si la demande n'est pas à lui. */
	@Transactional
	boolean choisirOffres(UUID id, UUID auteur, boolean accepte, Instant maintenant) {
		return base.sql("""
				UPDATE demande SET offres_systalink = :accepte,
				  offres_accord_le = CASE WHEN :accepte THEN coalesce(offres_accord_le, :t) ELSE NULL END
				WHERE id = :id AND auteur_id = :auteur
				""")
			.param("accepte", accepte).param("t", Timestamp.from(maintenant)).param("id", id).param("auteur", auteur)
			.update() == 1;
	}

	private List<Precision> precisions(UUID demandeId) {
		return base.sql("SELECT texte, cree_le, ayo, ayo_question FROM demande_precision WHERE demande_id = :d ORDER BY rang")
			.param("d", demandeId)
			.query((rs, n) -> new Precision(rs.getString("texte"), rs.getTimestamp("cree_le").toInstant(), rs.getString("ayo"),
					rs.getBoolean("ayo_question")))
			.list();
	}

	/** Les demandes de cet auteur, les plus récentes d'abord. */
	@Transactional(readOnly = true)
	List<Resume> mesDemandes(UUID auteur) {
		return base.sql("""
				SELECT d.id, d.titre, d.orientation ->> 'resume' AS resume, d.cree_le, d.publiee_le IS NOT NULL AS publiee,
				  (SELECT count(*) FROM demande_precision p WHERE p.demande_id = d.id) AS precisions
				FROM demande d
				WHERE d.auteur_id = :a ORDER BY d.cree_le DESC, d.id LIMIT 50
				""")
			.param("a", auteur)
			.query((rs, n) -> new Resume(rs.getObject("id", UUID.class), rs.getString("titre"), rs.getString("resume"),
					rs.getTimestamp("cree_le").toInstant(), rs.getInt("precisions"), rs.getBoolean("publiee")))
			.list();
	}

	enum Ajout {

		AJOUTEE, INTROUVABLE, LIMITE

	}

	/**
	 * Ajoute une réponse de la personne et relance Ayo : la tâche et le résumé précédents sont oubliés, la prochaine
	 * lecture dépose une nouvelle tâche avec toute la conversation. La réponse garde ce qu'Ayo venait de dire, pour le
	 * fil. 12 réponses au plus par demande.
	 */
	@Transactional
	Ajout preciser(UUID demandeId, UUID auteur, String texte, Instant maintenant) {
		Integer rang = base.sql("""
				SELECT coalesce(max(p.rang), 0) + 1 FROM demande d LEFT JOIN demande_precision p ON p.demande_id = d.id
				WHERE d.id = :d AND d.auteur_id = :a GROUP BY d.id
				""")
			.param("d", demandeId).param("a", auteur).query(Integer.class).optional().orElse(null);
		if (rang == null) {
			return Ajout.INTROUVABLE;
		}
		if (rang > PRECISIONS_MAX) {
			return Ajout.LIMITE;
		}
		verifierQuotaDuJour(auteur, maintenant);
		base.sql("""
				INSERT INTO demande_precision (demande_id, rang, texte, cree_le, ayo, ayo_question)
				SELECT :d, :r, :t, :c, ayo_dernier, ayo_question FROM demande WHERE id = :d
				""")
			.param("d", demandeId).param("r", rang).param("t", texte).param("c", Timestamp.from(maintenant)).update();
		base.sql("UPDATE demande SET tache_ia_id = NULL, orientation = NULL, ayo_dernier = NULL, ayo_question = FALSE WHERE id = :d")
			.param("d", demandeId).update();
		return Ajout.AJOUTEE;
	}

	/** Les conseils demandés sur les dernières 24 heures (demandes et précisions), du plus ancien au plus récent. */
	private List<Instant> analysesDuJour(UUID auteur, Instant maintenant) {
		return base.sql("""
				SELECT t FROM (
				  SELECT cree_le AS t FROM demande WHERE auteur_id = :a AND cree_le > :depuis
				  UNION ALL
				  SELECT p.cree_le FROM demande_precision p JOIN demande d ON d.id = p.demande_id
				  WHERE d.auteur_id = :a AND p.cree_le > :depuis
				) x ORDER BY t
				""")
			.param("a", auteur)
			.param("depuis", Timestamp.from(maintenant.minus(Duration.ofDays(1))))
			.query((rs, n) -> rs.getTimestamp("t").toInstant())
			.list();
	}

	/** Ce qu'il reste à la personne aujourd'hui, affiché par le front (« encore 2 conseils aujourd'hui »). */
	int analysesRestantes(UUID auteur, Instant maintenant) {
		return Math.max(0, ANALYSES_PAR_JOUR - analysesDuJour(auteur, maintenant).size());
	}

	private void verifierQuotaDuJour(UUID auteur, Instant maintenant) {
		List<Instant> jour = analysesDuJour(auteur, maintenant);
		if (jour.size() >= ANALYSES_PAR_JOUR) {
			throw new TropDePlans(Math.max(1, Duration.between(maintenant, jour.getFirst().plus(Duration.ofDays(1))).toSeconds()), true);
		}
	}

	/**
	 * Supprimer une conversation : la demande, ses réponses et ce qu'Ayo y a dit. Faux si elle n'est pas à lui. Une mission
	 * déjà publiée depuis elle reste en ligne : d'autres y ont peut-être déjà répondu (elle a sa propre copie).
	 */
	@Transactional
	boolean supprimer(UUID demandeId, UUID auteur) {
		return base.sql("DELETE FROM demande WHERE id = :id AND auteur_id = :a").param("id", demandeId).param("a", auteur)
			.update() == 1;
	}

	/** Ayo pose une question avant de conseiller (contracts/ia.yaml, `Orientation.question`). */
	/** Ayo conseille : pas de question, et au moins un service, une étape ou des développeurs (ni question ni discussion). */
	static boolean conseille(JsonNode orientation) {
		return !poseUneQuestion(orientation) && (!orientation.path("services").isEmpty() || !orientation.path("etapes").isEmpty()
				|| orientation.path("developpeursNecessaires").asBoolean(false));
	}

	static boolean poseUneQuestion(JsonNode orientation) {
		return orientation.path("question").path("texte").asString("").strip().length() > 0;
	}

	/** Ce qu'Ayo vient de dire, en texte, pour la prochaine réponse de la personne. Écrit une fois par analyse. */
	@Transactional
	void retenirAyo(UUID demandeId, JsonNode orientation) {
		String message = texte(orientation, "message");
		if (message == null || message.isBlank()) {
			message = texte(orientation, "resume");
		}
		boolean question = poseUneQuestion(orientation);
		String dit = ((message == null ? "" : message.strip())
				+ (question ? "\n\n" + orientation.path("question").path("texte").asString("").strip() : "")).strip();
		if (dit.isEmpty()) {
			return;
		}
		base.sql("UPDATE demande SET ayo_dernier = :dit, ayo_question = :q WHERE id = :id AND ayo_dernier IS NULL")
			.param("dit", dit.length() > 1200 ? dit.substring(0, 1200) : dit).param("q", question).param("id", demandeId)
			.update();
	}

	@Transactional
	void retenirTache(UUID demandeId, UUID tacheId) {
		base.sql("UPDATE demande SET tache_ia_id = :t WHERE id = :id AND tache_ia_id IS NULL")
			.param("t", tacheId).param("id", demandeId).update();
	}

	/** Seulement le résumé : jamais le plan, jamais le texte d'origine. */
	@Transactional
	void retenirResume(UUID demandeId, JsonNode orientation) {
		ObjectNode resume = json.createObjectNode();
		resume.put("resume", texte(orientation, "resume"));
		resume.put("secteur", texte(orientation, "secteur"));
		resume.put("developpeursNecessaires", orientation.path("developpeursNecessaires").asBoolean(false));
		var services = resume.putArray("services");
		orientation.path("services").forEach(service -> services.add(service.path("id").asString("")));
		var competences = resume.putArray("competences");
		orientation.path("competences").forEach(competence -> competences.add(competence.asString("")));
		base.sql("UPDATE demande SET orientation = CAST(:o AS jsonb) WHERE id = :id AND orientation IS NULL")
			.param("o", json.writeValueAsString(resume)).param("id", demandeId).update();
	}

	static String sansCoordonnees(String texte) {
		return TELEPHONE.matcher(EMAIL.matcher(texte).replaceAll("[e-mail retiré]")).replaceAll("[téléphone retiré]");
	}

	private static String texte(JsonNode noeud, String champ) {
		JsonNode valeur = noeud.get(champ);
		return valeur == null || valeur.isNull() ? null : valeur.asString();
	}

}
