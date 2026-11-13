package dev.jamii.api.profils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embeddable;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Version;

/** Le profil d'un compte (tables de la migration V4). Une ligne par compte, créée à la première génération. */
@Entity
class Profil {

	/** Au-delà, une génération restée « en cours » est considérée comme perdue (redémarrage du serveur). */
	static final Duration GENERATION_PERDUE = Duration.ofMinutes(2);

	@Id
	private UUID utilisateurId;

	private String bio;

	private String pays;

	private String ville;

	private String disponibilite = "disponible";

	private Integer heuresParSemaine;

	private boolean publie;

	private boolean afficherResumeIa = true;

	private String resumeIa;

	private String generationStatut;

	private String generationErreur;

	private Instant generationLanceeLe;

	private Instant generationTermineeLe;

	@Version
	private int version;

	@Column(name = "mis_a_jour_le")
	private Instant misAJourLe;

	@ElementCollection
	@CollectionTable(name = "competence_profil", joinColumns = @JoinColumn(name = "utilisateur_id"))
	private Set<CompetenceProfil> competences = new HashSet<>();

	@ElementCollection
	@CollectionTable(name = "profil_langage", joinColumns = @JoinColumn(name = "utilisateur_id"))
	@OrderColumn(name = "rang")
	private List<Langage> langages = new ArrayList<>();

	@ElementCollection
	@CollectionTable(name = "profil_depot_github", joinColumns = @JoinColumn(name = "utilisateur_id"))
	@OrderColumn(name = "rang")
	private List<DepotGithub> depots = new ArrayList<>();

	protected Profil() {
	}

	Profil(UUID utilisateurId, Instant maintenant) {
		this.utilisateurId = utilisateurId;
		this.misAJourLe = maintenant;
	}

	@Embeddable
	record CompetenceProfil(String competence, String source) {
	}

	@Embeddable
	record Langage(String nom, BigDecimal part) {
	}

	@Embeddable
	record DepotGithub(String nom, String url, String description, String langage, int etoiles,
			@Column(name = "mis_a_jour_le") Instant misAJourLe) {
	}

	boolean generationEnCours(Instant maintenant) {
		return "en_cours".equals(generationStatut) && generationLanceeLe != null
				&& generationLanceeLe.plus(GENERATION_PERDUE).isAfter(maintenant);
	}

	void demarrerGeneration(Instant maintenant) {
		generationStatut = "en_cours";
		generationErreur = null;
		generationLanceeLe = maintenant;
		generationTermineeLe = null;
	}

	/** Remplace le relevé GitHub. Ce que la personne a déclaré (bio, pays, compétences déclarées) n'est jamais touché. */
	void appliquerGithub(AnalyseGithub.Resultat github, Instant maintenant) {
		langages.clear();
		langages.addAll(github.langages());
		depots.clear();
		depots.addAll(github.depots());
		competences.removeIf(c -> "github".equals(c.source()));
		github.competences().forEach(c -> competences.add(new CompetenceProfil(c, "github")));
		generationStatut = "terminee";
		generationTermineeLe = maintenant;
		misAJourLe = maintenant;
	}

	/** Le relevé précédent reste affiché : un échec de GitHub ne vide jamais un profil. */
	void echecGeneration(String erreur, Instant maintenant) {
		generationStatut = "echec";
		generationErreur = erreur;
		generationTermineeLe = maintenant;
	}

	void remplacerCompetencesDeclarees(Set<String> declarees) {
		competences.removeIf(c -> "declaree".equals(c.source()));
		declarees.forEach(c -> competences.add(new CompetenceProfil(c, "declaree")));
	}

	void publier(boolean publie, Instant maintenant) {
		if (this.publie != publie) {
			this.publie = publie;
			this.misAJourLe = maintenant;
		}
	}

	void toucher(Instant maintenant) {
		this.misAJourLe = maintenant;
	}

	// Modifications simples, déjà validées par ModificationProfil.
	void bio(String bio) {
		this.bio = bio;
	}

	void pays(String pays) {
		this.pays = pays;
	}

	void ville(String ville) {
		this.ville = ville;
	}

	void disponibilite(String disponibilite) {
		this.disponibilite = disponibilite;
	}

	void heuresParSemaine(Integer heures) {
		this.heuresParSemaine = heures;
	}

	void afficherResumeIa(boolean afficher) {
		this.afficherResumeIa = afficher;
	}

	UUID utilisateurId() {
		return utilisateurId;
	}

	String bio() {
		return bio;
	}

	String pays() {
		return pays;
	}

	String ville() {
		return ville;
	}

	String disponibilite() {
		return disponibilite;
	}

	Integer heuresParSemaine() {
		return heuresParSemaine;
	}

	boolean publie() {
		return publie;
	}

	boolean afficherResumeIa() {
		return afficherResumeIa;
	}

	String resumeIa() {
		return resumeIa;
	}

	String generationStatut() {
		return generationStatut;
	}

	String generationErreur() {
		return generationErreur;
	}

	Instant generationLanceeLe() {
		return generationLanceeLe;
	}

	Instant generationTermineeLe() {
		return generationTermineeLe;
	}

	int version() {
		return version;
	}

	Instant misAJourLe() {
		return misAJourLe;
	}

	Set<CompetenceProfil> competences() {
		return Set.copyOf(competences);
	}

	List<Langage> langages() {
		return List.copyOf(langages);
	}

	List<DepotGithub> depots() {
		return List.copyOf(depots);
	}

}
