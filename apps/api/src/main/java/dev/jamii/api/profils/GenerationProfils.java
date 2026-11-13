package dev.jamii.api.profils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

import dev.jamii.api.auth.Annuaire;
import dev.jamii.api.auth.Compte;

/**
 * Génère un profil depuis GitHub, en tâche de fond : l'appel à GitHub peut prendre plusieurs secondes, et on ne garde
 * ni une requête HTTP ni une connexion à la base ouvertes pendant ce temps. Le front suit l'avancement avec
 * GET /api/me/profil (champ generation).
 */
class GenerationProfils {

	private static final Logger LOG = LoggerFactory.getLogger(GenerationProfils.class);

	static final int LIMITE_PAR_HEURE = 5;

	private final ProfilRepository profils;

	private final Annuaire comptes;

	private final SourceGithub github;

	private final TransactionTemplate transactions;

	private final Executor executeur;

	private final Clock horloge;

	/** Limite par compte, en mémoire : suffisant avec une seule instance de l'API (à revoir si on en lance plusieurs). */
	private final Map<UUID, Deque<Instant>> lancements = new ConcurrentHashMap<>();

	GenerationProfils(ProfilRepository profils, Annuaire comptes, SourceGithub github, TransactionTemplate transactions,
			Executor executeur, Clock horloge) {
		this.profils = profils;
		this.comptes = comptes;
		this.github = github;
		this.transactions = transactions;
		this.executeur = executeur;
		this.horloge = horloge;
	}

	Vues.Generation lancer(UUID utilisateurId) {
		String login = comptes.lire(utilisateurId)
			.map(Compte::loginGithub)
			.orElseThrow(() -> new ErreurProfil(HttpStatus.CONFLICT, "github_non_relie"));
		if (login == null) {
			throw new ErreurProfil(HttpStatus.CONFLICT, "github_non_relie");
		}
		Instant maintenant = horloge.instant();
		Profil profil;
		try {
			profil = transactions.execute(statut -> {
				Profil p = profils.findById(utilisateurId).orElseGet(() -> new Profil(utilisateurId, maintenant));
				if (p.generationEnCours(maintenant)) {
					throw new ErreurProfil(HttpStatus.CONFLICT, "generation_en_cours");
				}
				compterLancement(utilisateurId, maintenant);
				p.demarrerGeneration(maintenant);
				return profils.saveAndFlush(p);
			});
		}
		catch (DataIntegrityViolationException | ObjectOptimisticLockingFailureException e) {
			// Deux lancements simultanés : le second voit le premier.
			throw new ErreurProfil(HttpStatus.CONFLICT, "generation_en_cours");
		}
		// Après la validation de la transaction : la tâche de fond voit bien le statut « en_cours ».
		executeur.execute(() -> executer(utilisateurId, login));
		return Vues.Generation.depuis(profil);
	}

	private void compterLancement(UUID utilisateurId, Instant maintenant) {
		Deque<Instant> recents = lancements.computeIfAbsent(utilisateurId, id -> new ArrayDeque<>());
		synchronized (recents) {
			Instant ilYaUneHeure = maintenant.minus(Duration.ofHours(1));
			while (!recents.isEmpty() && recents.peekFirst().isBefore(ilYaUneHeure)) {
				recents.pollFirst();
			}
			if (recents.size() >= LIMITE_PAR_HEURE) {
				long attente = Duration.between(ilYaUneHeure, recents.peekFirst()).toSeconds() + 1;
				throw ErreurProfil.tropDeRequetes((int) Math.max(1, attente));
			}
			recents.addLast(maintenant);
		}
	}

	void executer(UUID utilisateurId, String login) {
		AnalyseGithub.Resultat resultat;
		try {
			resultat = AnalyseGithub.analyser(github.depotsPublics(login));
		}
		catch (SourceGithub.CompteIntrouvable e) {
			terminerEnEchec(utilisateurId, "github_refus");
			return;
		}
		catch (RuntimeException e) {
			// Sans le détail : il peut contenir l'en-tête d'autorisation ou la réponse de GitHub.
			LOG.warn("Génération du profil {} : GitHub indisponible ({})", utilisateurId, e.getClass().getSimpleName());
			terminerEnEchec(utilisateurId, "github_indisponible");
			return;
		}
		transactions.executeWithoutResult(statut -> profils.findById(utilisateurId)
			.ifPresent(p -> p.appliquerGithub(resultat, horloge.instant())));
	}

	private void terminerEnEchec(UUID utilisateurId, String erreur) {
		transactions.executeWithoutResult(statut -> profils.findById(utilisateurId)
			.ifPresent(p -> p.echecGeneration(erreur, horloge.instant())));
	}

}
