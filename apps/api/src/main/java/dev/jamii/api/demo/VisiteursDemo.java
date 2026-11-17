package dev.jamii.api.demo;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.security.SecureRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import dev.jamii.api.auth.Annuaire;
import dev.jamii.api.projets.Projets;

/**
 * Un compte démo par visiteur (#157) : « Entrer en démo » crée un compte jetable, rempli de tout le parcours (son
 * cercle, sa mission, le hackathon, une mission terminée), que personne d'autre n'ouvre. Deux membres du jury ne se
 * marchent donc pas dessus. Il est effacé après {@link #DUREE}.
 * <p>
 * Contre l'abus : au plus {@link #MAX} visiteurs en même temps (et une limite par adresse IP dans
 * {@link ConnexionDemoController}). Un compte de démo n'écrit jamais aux vrais comptes (reseau.ErreurReseau).
 */
@Component
@Profile("demo")
class VisiteursDemo {

	private static final Logger journal = LoggerFactory.getLogger(VisiteursDemo.class);

	static final Duration DUREE = Duration.ofHours(24);

	static final int MAX = 50;

	static final String NOM = "Visiteur démo";

	private static final SecureRandom HASARD = new SecureRandom();

	private final JdbcClient base;

	private final Projets projets;

	private final TransactionTemplate transaction;

	VisiteursDemo(JdbcClient base, Projets projets, TransactionTemplate transaction) {
		this.base = base;
		this.projets = projets;
		this.transaction = transaction;
	}

	/** Un nouveau visiteur, avec tout son parcours ; vide s'il y en a déjà {@link #MAX}. */
	Optional<UUID> creer(Instant maintenant) {
		return transaction.execute(statut -> {
			long presents = base.sql("SELECT count(*) FROM utilisateur WHERE email LIKE :v").param("v", Annuaire.VISITEURS_DEMO)
				.query(Long.class).single();
			if (presents >= MAX) {
				return Optional.<UUID>empty();
			}
			String cle = "visiteur-" + HexFormat.of().formatHex(octets());
			UUID id = UUID.randomUUID();
			Timestamp t = Timestamp.from(maintenant);
			base.sql("INSERT INTO utilisateur (id, nom, email, cree_le, mis_a_jour_le) VALUES (:id, :nom, :email, :t, :t)")
				.param("id", id).param("nom", NOM).param("email", cle + Annuaire.DOMAINE_DEMO).param("t", t).update();
			base.sql("INSERT INTO utilisateur_role (utilisateur_id, role) VALUES (:id, 'membre')").param("id", id).update();
			profil(id, t);
			DemoMonCompte.pourVisiteur(base, projets, maintenant, id, cle).completer();
			new DemoPreuves(base, maintenant).pourVisiteur(id, cle);
			return Optional.of(id);
		});
	}

	/** Un profil prêt, non publié : sinon chaque visiteur apparaîtrait dans la recherche de coéquipiers. */
	private void profil(UUID id, Timestamp t) {
		base.sql("""
				INSERT INTO profil (utilisateur_id, bio, pays, ville, disponibilite, heures_par_semaine, publie, generation_statut,
				                    mis_a_jour_le, titre, langues, competences_phares)
				VALUES (:id, :bio, 'SN', 'Dakar', 'disponible', 10, FALSE, 'terminee', :t, 'Développeur full-stack · Vue et Spring',
				        'fr,wo,en', 'vue,spring,postgresql')
				""").param("id", id).param("t", t)
			.param("bio", "Ton compte de démonstration de Jamii, à toi seul : explore librement. Il est effacé après 24 heures.")
			.update();
		for (String c : List.of("vue", "spring", "postgresql", "typescript")) {
			base.sql("INSERT INTO competence_profil (utilisateur_id, competence, source) VALUES (:id, :c, 'declaree')")
				.param("id", id).param("c", c).update();
		}
	}

	@Scheduled(fixedDelayString = "PT15M", initialDelayString = "PT2M")
	void nettoyer() {
		nettoyer(Instant.now());
	}

	/** Efface les visiteurs arrivés il y a plus de {@link #DUREE}, chacun dans sa transaction. Renvoie leur nombre. */
	int nettoyer(Instant maintenant) {
		List<UUID> expires = base.sql("SELECT id FROM utilisateur WHERE email LIKE :v AND cree_le < :limite")
			.param("v", Annuaire.VISITEURS_DEMO).param("limite", Timestamp.from(maintenant.minus(DUREE))).query(UUID.class).list();
		int effaces = 0;
		for (UUID id : expires) {
			try {
				transaction.executeWithoutResult(statut -> new CompteDemo(base).effacer(id));
				effaces++;
			}
			catch (DataAccessException e) {
				journal.warn("Le visiteur de démo {} n'a pas pu être effacé", id, e);
			}
		}
		return effaces;
	}

	private static byte[] octets() {
		byte[] o = new byte[6];
		HASARD.nextBytes(o);
		return o;
	}

}
