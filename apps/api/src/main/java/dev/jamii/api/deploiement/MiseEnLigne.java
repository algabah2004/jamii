package dev.jamii.api.deploiement;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import dev.jamii.api.systalink.ContexteMoment;
import dev.jamii.api.systalink.MomentsSystalink;
import dev.jamii.api.systalink.PropositionSystalink;
import dev.jamii.api.systalink.ServiceSystalink;

/** Les règles de la mise en ligne (contracts/deploiement.yaml). Le contrôleur ne fait que traduire en HTTP. */
@Service
@EnableScheduling
class MiseEnLigne {

	static final int DECLARATIONS_PAR_HEURE = 10;

	static final Duration ENTRE_DEUX_VERIFICATIONS = Duration.ofMinutes(1);

	private static final int LOT_HORAIRE = 500;

	private static final Logger journal = LoggerFactory.getLogger(MiseEnLigne.class);

	private final DepotDeploiements depot;

	private final ObjectProvider<AccesProjets> acces;

	private final Verificateur verificateur;

	private final AdressesPubliques.Resolveur resolveur;

	private final MomentsSystalink moments;

	private final ApplicationEventPublisher evenements;

	private final Clock horloge;

	private final List<String> domainesDatacloud;

	/** Limites gardées en mémoire, sans donnée personnelle en base. Une seule instance de l'API pour la CADEV. */
	private final Map<UUID, List<Instant>> declarations = new ConcurrentHashMap<>();

	private final ExecutorService enArrierePlan = Executors.newVirtualThreadPerTaskExecutor();

	MiseEnLigne(DepotDeploiements depot, ObjectProvider<AccesProjets> acces, Verificateur verificateur,
			ObjectProvider<AdressesPubliques.Resolveur> resolveur, MomentsSystalink moments,
			ApplicationEventPublisher evenements, ObjectProvider<Clock> horloge,
			@Value("${DATACLOUD_DOMAINES:}") String domainesDatacloud) {
		this.depot = depot;
		this.acces = acces;
		this.verificateur = verificateur;
		this.resolveur = resolveur.getIfAvailable(() -> AdressesPubliques.Resolveur.SYSTEME);
		this.moments = moments;
		this.evenements = evenements;
		this.horloge = horloge.getIfAvailable(Clock::systemUTC);
		this.domainesDatacloud = Arrays.stream(domainesDatacloud.split(","))
			.map(d -> d.trim().toLowerCase(Locale.ROOT))
			.filter(d -> !d.isEmpty())
			.toList();
	}

	@PreDestroy
	void arreter() {
		enArrierePlan.shutdownNow();
	}

	List<Deploiement> lister(UUID projetId, UUID moi, Instant apresLe, UUID apresId, int limite) {
		exiger(projetId, moi, AccesProjets.Acces.LECTEUR);
		return depot.page(projetId, apresLe, apresId, limite);
	}

	Deploiement declarer(UUID projetId, UUID moi, String adresse) {
		exiger(projetId, moi, AccesProjets.Acces.MEMBRE);
		URI url = valider(adresse);
		String texte = url.toString();
		if (depot.existe(projetId, texte)) {
			throw new Refus(HttpStatus.CONFLICT, "deja_declaree");
		}
		limiterDeclarations(moi);
		UUID id = UUID.randomUUID();
		depot.inserer(id, projetId, texte, hebergeur(url.getHost()), horloge.instant());
		Deploiement cree = depot.lire(projetId, id).orElseThrow();
		enArrierePlan.submit(() -> verifier(cree));
		return cree;
	}

	Deploiement relancer(UUID projetId, UUID moi, UUID id) {
		exiger(projetId, moi, AccesProjets.Acces.MEMBRE);
		Deploiement d = depot.lire(projetId, id).orElseThrow(() -> new Refus(HttpStatus.NOT_FOUND, "introuvable"));
		if (d.dernierEssai() != null
				&& d.dernierEssai().le().plus(ENTRE_DEUX_VERIFICATIONS).isAfter(horloge.instant())) {
			throw new Refus(HttpStatus.TOO_MANY_REQUESTS, "trop_de_requetes");
		}
		enArrierePlan.submit(() -> verifier(d));
		return d;
	}

	void retirer(UUID projetId, UUID moi, UUID id) {
		exiger(projetId, moi, AccesProjets.Acces.MEMBRE);
		if (!depot.supprimer(projetId, id)) {
			throw new Refus(HttpStatus.NOT_FOUND, "introuvable");
		}
	}

	/** « Le bon service au bon moment » : l'équipe ouvre l'écran de mise en ligne. */
	PropositionSystalink proposerDatacloud(UUID projetId, UUID moi) {
		exiger(projetId, moi, AccesProjets.Acces.MEMBRE);
		return moments.proposer(ServiceSystalink.DATACLOUD, ContexteMoment.PROJET, projetId);
	}

	/** Toutes les heures (et une minute après le démarrage) : chaque adresse est revérifiée. */
	@Scheduled(initialDelayString = "${jamii.deploiement.premiere-verification:PT1M}",
			fixedDelayString = "${jamii.deploiement.intervalle:PT1H}")
	void verifierTout() {
		depot.aVerifier(LOT_HORAIRE).forEach(this::verifier);
	}

	void verifier(Deploiement d) {
		try {
			Verificateur.Resultat resultat = verificateur.verifier(URI.create(d.url()));
			Instant maintenant = horloge.instant();
			if (depot.enregistrerEssai(d, resultat, maintenant)) {
				evenements.publishEvent(new ProjetMisEnLigne(d.projetId(), d.id(), d.hebergeur(), maintenant));
			}
		}
		catch (RuntimeException e) {
			// Une adresse qui plante ne doit pas arrêter la vérification des autres.
			journal.warn("vérification impossible pour le déploiement {}", d.id(), e);
		}
	}

	/** Contrôles de contracts/deploiement.yaml, avant tout appel réseau. */
	URI valider(String adresse) {
		if (adresse == null || adresse.isBlank() || adresse.length() > 2048) {
			throw Refus.champ("format_invalide");
		}
		URI url;
		try {
			url = new URI(adresse.trim());
		}
		catch (URISyntaxException e) {
			throw Refus.champ("format_invalide");
		}
		if (url.getHost() == null) {
			throw Refus.champ("format_invalide");
		}
		if (!"https".equalsIgnoreCase(url.getScheme())) {
			throw Refus.champ("pas_https");
		}
		if (url.getPort() != -1 && url.getPort() != 443) {
			throw Refus.champ("port_interdit");
		}
		if (url.getRawUserInfo() != null) {
			throw Refus.champ("identifiant_interdit");
		}
		InetAddress[] ips;
		try {
			ips = resolveur.resoudre(url.getHost());
		}
		catch (UnknownHostException e) {
			throw Refus.champ("nom_inconnu");
		}
		if (ips.length == 0 || !Arrays.stream(ips).allMatch(AdressesPubliques::estPublique)) {
			throw Refus.champ("adresse_refusee");
		}
		return url.normalize();
	}

	Hebergeur hebergeur(String nom) {
		String n = nom.toLowerCase(Locale.ROOT);
		return domainesDatacloud.stream().anyMatch(d -> n.equals(d) || n.endsWith("." + d)) ? Hebergeur.DATACLOUD
				: Hebergeur.AUTRE;
	}

	private void exiger(UUID projetId, UUID moi, AccesProjets.Acces minimum) {
		AccesProjets projets = acces.getIfAvailable();
		AccesProjets.Acces obtenu = projets == null ? AccesProjets.Acces.INTROUVABLE : projets.acces(projetId, moi);
		if (obtenu == AccesProjets.Acces.INTROUVABLE) {
			throw new Refus(HttpStatus.NOT_FOUND, "introuvable");
		}
		if (minimum == AccesProjets.Acces.MEMBRE && obtenu != AccesProjets.Acces.MEMBRE) {
			throw new Refus(HttpStatus.FORBIDDEN, "interdit");
		}
	}

	/** Pour les tests : chaque test repart sans déclaration récente. */
	void oublierLimites() {
		declarations.clear();
	}

	private void limiterDeclarations(UUID moi) {
		Instant maintenant = horloge.instant();
		Instant ilYaUneHeure = maintenant.minus(Duration.ofHours(1));
		List<Instant> siennes = declarations.computeIfAbsent(moi, cle -> new ArrayList<>());
		synchronized (siennes) {
			siennes.removeIf(t -> !t.isAfter(ilYaUneHeure));
			if (siennes.size() >= DECLARATIONS_PAR_HEURE) {
				throw new Refus(HttpStatus.TOO_MANY_REQUESTS, "trop_de_requetes");
			}
			siennes.add(maintenant);
		}
	}

	/** Une règle refuse l'action : le contrôleur la traduit en Problem Details. */
	static final class Refus extends RuntimeException {

		final HttpStatus statut;

		final String code;

		final String champ;

		Refus(HttpStatus statut, String code) {
			this(statut, code, null);
		}

		private Refus(HttpStatus statut, String code, String champ) {
			super(code, null, false, false);
			this.statut = statut;
			this.code = code;
			this.champ = champ;
		}

		/** Erreur de validation sur `url` (400, `code` = `validation`). */
		static Refus champ(String codeChamp) {
			return new Refus(HttpStatus.BAD_REQUEST, "validation", codeChamp);
		}

	}

}
