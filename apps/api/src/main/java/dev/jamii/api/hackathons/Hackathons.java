package dev.jamii.api.hackathons;

import dev.jamii.api.questionnaires.Questionnaire;
import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Créer, modifier, publier, annuler et lire un hackathon (contracts/hackathons.yaml, tag « hackathons »).
 *
 * <p>Seuls `brouillon`, `publie`, `annule` et `resultats` sont enregistrés : `en_cours` et `evaluation` se **calculent**
 * du calendrier, à chaque lecture. Un brouillon n'existe que pour son organisateur (404 pour tout autre).
 */
@Service
class Hackathons {

	record Calendrier(Instant inscriptionsOuvertesLe, Instant inscriptionsFermeesLe, Instant debutLe, Instant finRendusLe,
			Instant resultatsLe) {

		/** Ouverture < fermeture ≤ début < fin des rendus < résultats. */
		boolean ordonne() {
			return inscriptionsOuvertesLe.isBefore(inscriptionsFermeesLe) && !inscriptionsFermeesLe.isAfter(debutLe)
					&& debutLe.isBefore(finRendusLe) && finRendusLe.isBefore(resultatsLe);
		}

	}

	record Montant(long centimes, String devise) {
	}

	record Critere(UUID id, String nom, int poids) {
	}

	record Prix(UUID id, String nom, Montant montant, String sponsor) {
	}

	record Hackathon(UUID id, UUID organisateurId, String titre, String theme, String description, String lieu, String ville,
			Calendrier calendrier, int tailleMin, int tailleMax, String modeInscription, boolean deploiementObligatoire,
			String codeDeConduite, List<String> questions, List<Questionnaire.Question> questionnaire, String statutBase, int version,
			Instant creeLe,
			List<Critere> criteres, List<Prix> prix) {

		/** Le statut tel que la personne le voit, calculé du calendrier à l'instant `maintenant`. */
		String statut(Instant maintenant) {
			if (!"publie".equals(statutBase)) {
				return statutBase;
			}
			if (maintenant.isBefore(calendrier.debutLe())) {
				return "publie";
			}
			return maintenant.isBefore(calendrier.finRendusLe()) ? "en_cours" : "evaluation";
		}

	}

	/** Un hackathon, et ce que la personne qui le lit est pour lui : `organisateur`, `jure`, `participant` (avec son équipe) ou `visiteur`. */
	record Lecture(Hackathon hackathon, String role, UUID equipeId) {

		Lecture(Hackathon hackathon, String role) {
			this(hackathon, role, null);
		}

	}

	/** Un hackathon nouveau, déjà validé par le contrôleur. */
	record Nouveau(String titre, String theme, String description, String lieu, String ville, Calendrier calendrier,
			int tailleMin, int tailleMax, String modeInscription, boolean deploiementObligatoire, String codeDeConduite,
			List<String> questions, List<Questionnaire.Question> questionnaire, List<Critere> criteres, List<Prix> prix) {
	}

	/**
	 * Une fusion JSON déjà validée. `presents` dit quels champs étaient dans la requête : absent, un champ ne change pas ;
	 * présent à `null`, il est vidé (la ville, le code de conduite).
	 */
	record Modification(int version, Set<String> presents, String titre, String theme, String description, String lieu,
			String ville, Calendrier calendrier, Boolean deploiementObligatoire, String codeDeConduite, List<String> questions,
			List<Questionnaire.Question> questionnaire, List<Critere> criteres, List<Prix> prix) {
	}

	record Page(List<Hackathon> elements, String curseurSuivant) {
	}

	/** Contre les faux hackathons : 3 publications par période glissante de 24 heures et par compte. */
	static final int PUBLICATIONS_PAR_JOUR = 3;

	private static final Duration JOUR = Duration.ofHours(24);

	private static final String COLONNES = """
			id, organisateur_id, titre, theme, description, lieu, ville, inscriptions_ouvertes_le, inscriptions_fermees_le,
			debut_le, fin_rendus_le, resultats_le, taille_min, taille_max, mode_inscription, deploiement_obligatoire,
			code_de_conduite, questions, questionnaire, statut_base, version, cree_le""";

	private final JdbcClient base;

	private final Clock horloge;

	Hackathons(JdbcClient base, ObjectProvider<Clock> horloge) {
		this.base = base;
		this.horloge = horloge.getIfAvailable(Clock::systemUTC);
	}

	Instant maintenant() {
		return horloge.instant();
	}

	@Transactional
	UUID creer(UUID organisateur, Nouveau n) {
		UUID id = UUID.randomUUID();
		base.sql("""
				INSERT INTO hackathon (id, organisateur_id, titre, theme, description, lieu, ville, inscriptions_ouvertes_le,
					inscriptions_fermees_le, debut_le, fin_rendus_le, resultats_le, taille_min, taille_max, mode_inscription,
					deploiement_obligatoire, code_de_conduite, questions, questionnaire, cree_le)
				VALUES (:id, :orga, :titre, :theme, :description, :lieu, :ville, :ouvertes, :fermees, :debut, :fin, :resultats,
					:tmin, :tmax, :mode, :deploiement, :code, :questions, CAST(:questionnaire AS jsonb), :maintenant)
				""")
			.param("id", id).param("orga", organisateur).param("titre", n.titre()).param("theme", n.theme())
			.param("description", n.description()).param("lieu", n.lieu()).param("ville", n.ville(), java.sql.Types.VARCHAR)
			.param("ouvertes", Timestamp.from(n.calendrier().inscriptionsOuvertesLe()))
			.param("fermees", Timestamp.from(n.calendrier().inscriptionsFermeesLe()))
			.param("debut", Timestamp.from(n.calendrier().debutLe())).param("fin", Timestamp.from(n.calendrier().finRendusLe()))
			.param("resultats", Timestamp.from(n.calendrier().resultatsLe())).param("tmin", n.tailleMin())
			.param("tmax", n.tailleMax()).param("mode", n.modeInscription()).param("deploiement", n.deploiementObligatoire())
			.param("code", n.codeDeConduite(), java.sql.Types.VARCHAR).param("questions", n.questions().toArray(String[]::new))
			.param("questionnaire", Questionnaire.enJson(n.questionnaire()))
			.param("maintenant", Timestamp.from(maintenant()))
			.update();
		remplacerCriteres(id, n.criteres());
		remplacerPrix(id, n.prix());
		return id;
	}

	/** Le hackathon, si `moi` (null : visiteur sans connexion) a le droit de savoir qu'il existe. Sinon 404. */
	@Transactional(readOnly = true)
	Lecture lire(UUID id, UUID moi) {
		return role(charger(id, false), moi).orElseThrow(ErreurHackathons::introuvable);
	}

	/**
	 * Les hackathons publiés, par date de début, avec un curseur. Les brouillons et les hackathons annulés n'y sont
	 * jamais. `statut` filtre sur le statut calculé.
	 */
	@Transactional(readOnly = true)
	Page lister(String statut, int limite, String curseur) {
		var requete = new StringBuilder("SELECT " + COLONNES + " FROM hackathon WHERE ");
		switch (statut == null ? "" : statut) {
			case "publie" -> requete.append("statut_base = 'publie' AND :maintenant < debut_le");
			case "en_cours" -> requete.append("statut_base = 'publie' AND :maintenant >= debut_le AND :maintenant < fin_rendus_le");
			case "evaluation" -> requete.append("statut_base = 'publie' AND :maintenant >= fin_rendus_le");
			case "resultats" -> requete.append("statut_base = 'resultats'");
			case "" -> requete.append("statut_base IN ('publie', 'resultats')");
			default -> requete.append("FALSE");
		}
		Curseur c = curseur == null ? null : Curseur.lire(curseur);
		if (c != null) {
			requete.append(" AND (debut_le, id) > (:t, :cid)");
		}
		requete.append(" ORDER BY debut_le, id LIMIT :n");
		var q = base.sql(requete.toString()).param("n", limite + 1);
		if (statut != null && Set.of("publie", "en_cours", "evaluation").contains(statut)) {
			q = q.param("maintenant", Timestamp.from(maintenant()));
		}
		if (c != null) {
			q = q.param("t", Timestamp.from(c.debutLe())).param("cid", c.id());
		}
		List<Hackathon> lus = new ArrayList<>(q.query(Hackathons::hackathon).list());
		String suivant = null;
		if (lus.size() > limite) {
			lus = new ArrayList<>(lus.subList(0, limite));
			suivant = new Curseur(lus.getLast().calendrier().debutLe(), lus.getLast().id()).texte();
		}
		return new Page(lus, suivant);
	}

	/**
	 * Les hackathons d'une personne : ceux qu'elle organise (brouillons et annulés compris), ceux dont elle est jurée et
	 * ceux où elle est inscrite. Le plus récent d'abord, pour retrouver vite celui qu'on vient de créer.
	 */
	@Transactional(readOnly = true)
	Page listerMiens(UUID moi, int limite, String curseur) {
		var requete = new StringBuilder("SELECT " + COLONNES + " FROM hackathon WHERE " + """
				(organisateur_id = :moi
					OR (statut_base <> 'brouillon' AND (
						EXISTS (SELECT 1 FROM jure j WHERE j.hackathon_id = hackathon.id AND j.utilisateur_id = :moi)
						OR EXISTS (SELECT 1 FROM inscription i WHERE i.hackathon_id = hackathon.id AND i.utilisateur_id = :moi))))""");
		Curseur c = curseur == null ? null : Curseur.lire(curseur);
		if (c != null) {
			requete.append(" AND (debut_le, id) < (:t, :cid)");
		}
		requete.append(" ORDER BY debut_le DESC, id DESC LIMIT :n");
		var q = base.sql(requete.toString()).param("n", limite + 1).param("moi", moi);
		if (c != null) {
			q = q.param("t", Timestamp.from(c.debutLe())).param("cid", c.id());
		}
		List<Hackathon> lus = new ArrayList<>(q.query(Hackathons::hackathon).list());
		String suivant = null;
		if (lus.size() > limite) {
			lus = new ArrayList<>(lus.subList(0, limite));
			suivant = new Curseur(lus.getLast().calendrier().debutLe(), lus.getLast().id()).texte();
		}
		return new Page(lus, suivant);
	}

	/**
	 * Modifier le hackathon (merge-patch), tant qu'il est en brouillon ou publié et pas commencé. Une fois publié, les
	 * critères ne changent plus : des équipes peuvent déjà s'y être inscrites.
	 */
	@Transactional
	Hackathon modifier(UUID id, UUID moi, Modification m) {
		Hackathon h = organisateurDe(id, moi);
		verifierVersion(h, m.version());
		String statut = h.statut(maintenant());
		if (!"brouillon".equals(statut) && !"publie".equals(statut)) {
			throw ErreurHackathons.conflit("etat_invalide", "Ce hackathon ne peut plus être modifié");
		}
		if ("publie".equals(statut) && m.presents().contains("criteres")) {
			throw ErreurHackathons.conflit("etat_invalide", "Les critères ne changent plus une fois le hackathon publié");
		}
		// Les réponses déjà données gardent leur sens : on ne change plus les questions une fois quelqu'un inscrit.
		if (m.presents().contains("questionnaire") && !m.questionnaire().equals(h.questionnaire())
				&& base.sql("SELECT EXISTS (SELECT 1 FROM inscription WHERE hackathon_id = :h)").param("h", id).query(Boolean.class).single()) {
			throw ErreurHackathons.conflit("questionnaire_fige", "Des personnes se sont déjà inscrites : le questionnaire ne change plus");
		}
		Calendrier calendrier = m.presents().contains("calendrier") ? m.calendrier() : h.calendrier();
		if (!calendrier.ordonne()) {
			throw ErreurHackathons.validation("calendrier", "dates_dans_le_desordre");
		}
		base.sql("""
				UPDATE hackathon SET titre = :titre, theme = :theme, description = :description, lieu = :lieu, ville = :ville,
				  inscriptions_ouvertes_le = :ouvertes, inscriptions_fermees_le = :fermees, debut_le = :debut,
				  fin_rendus_le = :fin, resultats_le = :resultats, deploiement_obligatoire = :deploiement,
				  code_de_conduite = :code, questions = :questions, questionnaire = CAST(:questionnaire AS jsonb), version = version + 1
				WHERE id = :id
				""")
			.param("titre", m.presents().contains("titre") ? m.titre() : h.titre())
			.param("theme", m.presents().contains("theme") ? m.theme() : h.theme())
			.param("description", m.presents().contains("description") ? m.description() : h.description())
			.param("lieu", m.presents().contains("lieu") ? m.lieu() : h.lieu())
			.param("ville", m.presents().contains("ville") ? m.ville() : h.ville(), java.sql.Types.VARCHAR)
			.param("ouvertes", Timestamp.from(calendrier.inscriptionsOuvertesLe()))
			.param("fermees", Timestamp.from(calendrier.inscriptionsFermeesLe()))
			.param("debut", Timestamp.from(calendrier.debutLe())).param("fin", Timestamp.from(calendrier.finRendusLe()))
			.param("resultats", Timestamp.from(calendrier.resultatsLe()))
			.param("deploiement", m.presents().contains("deploiementObligatoire") ? m.deploiementObligatoire() : h.deploiementObligatoire())
			.param("code", m.presents().contains("codeDeConduite") ? m.codeDeConduite() : h.codeDeConduite(), java.sql.Types.VARCHAR)
			.param("questions", (m.presents().contains("questions") ? m.questions() : h.questions()).toArray(String[]::new))
			.param("questionnaire", Questionnaire.enJson(m.presents().contains("questionnaire") ? m.questionnaire() : h.questionnaire()))
			.param("id", id)
			.update();
		if (m.presents().contains("criteres")) {
			remplacerCriteres(id, m.criteres());
		}
		if (m.presents().contains("prix")) {
			remplacerPrix(id, m.prix());
		}
		return charger(id, false);
	}

	/**
	 * brouillon → publie, par l'organisateur, avec la version lue. Il faut un code de conduite et des rendus à venir ;
	 * au plus {@value #PUBLICATIONS_PAR_JOUR} publications par période de 24 heures (429 sinon : rien n'est publié).
	 */
	@Transactional
	Hackathon publier(UUID id, UUID moi, int version) {
		Hackathon h = organisateurDe(id, moi);
		verifierVersion(h, version);
		if (!"brouillon".equals(h.statutBase())) {
			throw ErreurHackathons.conflit("etat_invalide", "Seul un brouillon se publie");
		}
		if (h.codeDeConduite() == null || h.codeDeConduite().isBlank()) {
			throw ErreurHackathons.validation("codeDeConduite", "obligatoire");
		}
		Instant maintenant = maintenant();
		if (!h.calendrier().finRendusLe().isAfter(maintenant)) {
			throw ErreurHackathons.validation("calendrier", "dans_le_passe");
		}
		// Deux publications simultanées du même compte se comptent l'une après l'autre.
		base.sql("SELECT pg_advisory_xact_lock(hashtextextended(:orga, 2))").param("orga", moi.toString()).query((rs, i) -> 1).single();
		List<Instant> recentes = base.sql("SELECT publie_le FROM hackathon WHERE organisateur_id = :o AND publie_le > :depuis ORDER BY publie_le")
			.param("o", moi).param("depuis", Timestamp.from(maintenant.minus(JOUR)))
			.query((rs, i) -> rs.getTimestamp(1).toInstant()).list();
		if (recentes.size() >= PUBLICATIONS_PAR_JOUR) {
			Instant liberee = recentes.get(recentes.size() - PUBLICATIONS_PAR_JOUR).plus(JOUR);
			throw ErreurHackathons.limiteAtteinte("Trop de hackathons publiés aujourd'hui", Duration.between(maintenant, liberee).toSeconds() + 1);
		}
		base.sql("UPDATE hackathon SET statut_base = 'publie', publie_le = :t, version = version + 1 WHERE id = :id")
			.param("t", Timestamp.from(maintenant)).param("id", id).update();
		return charger(id, false);
	}

	/** publie ou en_cours → annule, avec une raison montrée aux inscrits. */
	@Transactional
	Hackathon annuler(UUID id, UUID moi, int version, String raison) {
		Hackathon h = organisateurDe(id, moi);
		verifierVersion(h, version);
		String statut = h.statut(maintenant());
		if (!"publie".equals(statut) && !"en_cours".equals(statut)) {
			throw ErreurHackathons.conflit("etat_invalide", "Ce hackathon ne peut plus être annulé");
		}
		base.sql("UPDATE hackathon SET statut_base = 'annule', annulation_raison = :raison, version = version + 1 WHERE id = :id")
			.param("raison", raison).param("id", id).update();
		return charger(id, false);
	}

	// ---------------------------------------------------------------- règles d'accès

	/** Le hackathon verrouillé, pour son organisateur seulement : 404 si `moi` ne peut pas le voir, 403 s'il le voit sans l'être. */
	Hackathon organisateurDe(UUID id, UUID moi) {
		Hackathon h = charger(id, true);
		Lecture l = role(h, moi).orElseThrow(ErreurHackathons::introuvable);
		if (!"organisateur".equals(l.role())) {
			throw ErreurHackathons.interdit();
		}
		return h;
	}

	/** Comme {@link #organisateurDe}, sans verrou : pour les lectures réservées à l'organisateur. */
	Hackathon exigerOrganisateur(UUID id, UUID moi) {
		Hackathon h = charger(id, false);
		Lecture l = role(h, moi).orElseThrow(ErreurHackathons::introuvable);
		if (!"organisateur".equals(l.role())) {
			throw ErreurHackathons.interdit();
		}
		return h;
	}

	/**
	 * Ce que `moi` est pour ce hackathon : `organisateur`, `jure`, `participant` (inscrit, hors inscription refusée,
	 * avec son équipe s'il en a une) ou `visiteur` (tout hackathon publié). Vide : il n'existe pas pour lui.
	 */
	Optional<Lecture> role(Hackathon h, UUID moi) {
		if (h.organisateurId().equals(moi)) {
			return Optional.of(new Lecture(h, "organisateur"));
		}
		if ("brouillon".equals(h.statutBase())) {
			return Optional.empty();
		}
		if (moi != null) {
			var inscrit = base.sql("SELECT equipe_id FROM inscription WHERE hackathon_id = :h AND utilisateur_id = :u AND statut <> 'refusee'")
				.param("h", h.id()).param("u", moi).query((rs, i) -> Optional.ofNullable(rs.getObject("equipe_id", UUID.class))).optional();
			UUID equipe = inscrit.flatMap(e -> e).orElse(null);
			// Un juré peut aussi être inscrit : il reste juré, et ne note pas le projet de son équipe.
			boolean jure = base.sql("SELECT 1 FROM jure WHERE hackathon_id = :h AND utilisateur_id = :u").param("h", h.id()).param("u", moi)
				.query(Integer.class).optional().isPresent();
			if (jure) {
				return Optional.of(new Lecture(h, "jure", equipe));
			}
			if (inscrit.isPresent()) {
				return Optional.of(new Lecture(h, "participant", equipe));
			}
		}
		return Optional.of(new Lecture(h, "visiteur"));
	}

	/** Combien de personnes sont inscrites à chaque hackathon (une inscription refusée ne compte pas). */
	@Transactional(readOnly = true)
	java.util.Map<UUID, Integer> nombresInscrits(java.util.Collection<UUID> hackathons) {
		java.util.Map<UUID, Integer> nombres = new java.util.HashMap<>();
		if (hackathons.isEmpty()) {
			return nombres;
		}
		base.sql("SELECT hackathon_id, count(*) FROM inscription WHERE hackathon_id IN (:ids) AND statut <> 'refusee' GROUP BY hackathon_id")
			.param("ids", hackathons)
			.query((rs, i) -> nombres.put(rs.getObject(1, UUID.class), rs.getInt(2)))
			.list();
		return nombres;
	}

	private static void verifierVersion(Hackathon h, int version) {
		if (h.version() != version) {
			throw ErreurHackathons.conflit("modification_concurrente", "Le hackathon a changé entre-temps");
		}
	}

	Hackathon charger(UUID id, boolean verrouiller) {
		Hackathon h = base.sql("SELECT " + COLONNES + " FROM hackathon WHERE id = :id" + (verrouiller ? " FOR UPDATE" : ""))
			.param("id", id).query(Hackathons::hackathon).optional().orElseThrow(ErreurHackathons::introuvable);
		return new Hackathon(h.id(), h.organisateurId(), h.titre(), h.theme(), h.description(), h.lieu(), h.ville(), h.calendrier(),
				h.tailleMin(), h.tailleMax(), h.modeInscription(), h.deploiementObligatoire(), h.codeDeConduite(), h.questions(),
				h.questionnaire(), h.statutBase(), h.version(), h.creeLe(), criteres(id), prix(id));
	}

	private List<Critere> criteres(UUID hackathon) {
		return base.sql("SELECT id, nom, poids FROM critere WHERE hackathon_id = :h ORDER BY ordre").param("h", hackathon)
			.query((rs, i) -> new Critere(rs.getObject("id", UUID.class), rs.getString("nom"), rs.getInt("poids"))).list();
	}

	private List<Prix> prix(UUID hackathon) {
		return base.sql("SELECT id, nom, montant_centimes, devise, sponsor FROM prix WHERE hackathon_id = :h ORDER BY ordre")
			.param("h", hackathon)
			.query((rs, i) -> {
				long centimes = rs.getLong("montant_centimes");
				Montant montant = rs.wasNull() ? null : new Montant(centimes, rs.getString("devise"));
				return new Prix(rs.getObject("id", UUID.class), rs.getString("nom"), montant, rs.getString("sponsor"));
			}).list();
	}

	private void remplacerCriteres(UUID hackathon, List<Critere> criteres) {
		base.sql("DELETE FROM critere WHERE hackathon_id = :h").param("h", hackathon).update();
		int ordre = 1;
		for (Critere c : criteres) {
			base.sql("INSERT INTO critere (id, hackathon_id, ordre, nom, poids) VALUES (:id, :h, :o, :nom, :poids)")
				.param("id", UUID.randomUUID()).param("h", hackathon).param("o", ordre++).param("nom", c.nom())
				.param("poids", c.poids()).update();
		}
	}

	private void remplacerPrix(UUID hackathon, List<Prix> prix) {
		base.sql("DELETE FROM prix WHERE hackathon_id = :h").param("h", hackathon).update();
		int ordre = 1;
		for (Prix p : prix) {
			base.sql("""
					INSERT INTO prix (id, hackathon_id, ordre, nom, montant_centimes, devise, sponsor)
					VALUES (:id, :h, :o, :nom, :centimes, :devise, :sponsor)
					""")
				.param("id", UUID.randomUUID()).param("h", hackathon).param("o", ordre++).param("nom", p.nom())
				.param("centimes", p.montant() == null ? null : p.montant().centimes(), java.sql.Types.BIGINT)
				.param("devise", p.montant() == null ? null : p.montant().devise(), java.sql.Types.CHAR)
				.param("sponsor", p.sponsor(), java.sql.Types.VARCHAR).update();
		}
	}

	/** Une ligne de la table, sans ses critères ni ses prix (la liste n'en a pas besoin). */
	private static Hackathon hackathon(ResultSet rs, int ligne) throws SQLException {
		Array questions = rs.getArray("questions");
		return new Hackathon(rs.getObject("id", UUID.class), rs.getObject("organisateur_id", UUID.class), rs.getString("titre"),
				rs.getString("theme"), rs.getString("description"), rs.getString("lieu"), rs.getString("ville"),
				new Calendrier(rs.getTimestamp("inscriptions_ouvertes_le").toInstant(),
						rs.getTimestamp("inscriptions_fermees_le").toInstant(), rs.getTimestamp("debut_le").toInstant(),
						rs.getTimestamp("fin_rendus_le").toInstant(), rs.getTimestamp("resultats_le").toInstant()),
				rs.getInt("taille_min"), rs.getInt("taille_max"), rs.getString("mode_inscription"),
				rs.getBoolean("deploiement_obligatoire"), rs.getString("code_de_conduite"),
				List.of((String[]) questions.getArray()), Questionnaire.questions(rs.getString("questionnaire")), rs.getString("statut_base"), rs.getInt("version"),
				rs.getTimestamp("cree_le").toInstant(), List.of(), List.of());
	}

	/** Le curseur de la liste : la date de début et l'identifiant du dernier hackathon, en base 64 (opaque pour le front). */
	private record Curseur(Instant debutLe, UUID id) {

		String texte() {
			return Base64.getUrlEncoder().withoutPadding().encodeToString((debutLe + "|" + id).getBytes(StandardCharsets.UTF_8));
		}

		static Curseur lire(String texte) {
			try {
				String[] parties = new String(Base64.getUrlDecoder().decode(texte), StandardCharsets.UTF_8).split("\\|");
				return new Curseur(Instant.parse(parties[0]), UUID.fromString(parties[1]));
			}
			catch (RuntimeException e) {
				throw ErreurHackathons.validation("curseur", "invalide");
			}
		}

	}

}
