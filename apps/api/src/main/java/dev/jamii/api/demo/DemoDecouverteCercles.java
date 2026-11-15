package dev.jamii.api.demo;

import static dev.jamii.api.demo.DemoCommunautes.AICHA;
import static dev.jamii.api.demo.DemoCommunautes.AMINA;
import static dev.jamii.api.demo.DemoCommunautes.CHIDI;
import static dev.jamii.api.demo.DemoCommunautes.FATIMA;
import static dev.jamii.api.demo.DemoCommunautes.IBRAHIMA;
import static dev.jamii.api.demo.DemoCommunautes.JOSEPH;
import static dev.jamii.api.demo.DemoCommunautes.KWAME;
import static dev.jamii.api.demo.DemoCommunautes.MARIAM;
import static dev.jamii.api.demo.DemoCommunautes.SAMIRA;
import static dev.jamii.api.demo.DemoCommunautes.TENDAI;
import static dev.jamii.api.demo.DemoCommunautes.THABO;
import static dev.jamii.api.demo.DemoCommunautes.ZAWADI;
import static dev.jamii.api.demo.DonneesDemo.FATOU;
import static dev.jamii.api.demo.DonneesDemo.KOFI;
import static dev.jamii.api.demo.DonneesDemo.MOUSSA;
import static dev.jamii.api.demo.DonneesDemo.NIA;
import static dev.jamii.api.demo.DonneesDemo.YANN;
import static dev.jamii.api.demo.DonneesDemo.id;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

import dev.jamii.api.demo.DonneesDemo.Personne;

/**
 * Le répertoire des cercles de la démo distante. Complète aussi une base déjà initialisée, uniquement depuis le
 * chargeur du profil demo. Identifiants stables : aucun doublon et aucun écrasement des cercles que les visiteurs
 * ont rejoints ou modifiés. Les membres sont les comptes fictifs existants, jamais des comptes réels.
 */
final class DemoDecouverteCercles {

	private record Decouverte(String cle, String nom, String ville, Personne createur, String idee) {
	}

	private static final List<Decouverte> CERCLES = List.of(
			new Decouverte("sahel-mobile", "Sahel Mobile", "Bamako", AICHA, "Les commandes du marché par SMS"),
			new Decouverte("lagos-builders", "Lagos Builders", "Lagos", CHIDI, "Le carnet des livreurs du quartier"),
			new Decouverte("nairobi-open-source", "Nairobi Open Source", "Nairobi", ZAWADI, "Les contributions open source en swahili"),
			new Decouverte("design-impact", "Design & Impact", "Accra", KWAME, "Un annuaire accessible pour les associations"),
			new Decouverte("sante-connect", "Santé Connect", "Kigali", JOSEPH, "Le suivi des rendez-vous au dispensaire"),
			new Decouverte("makers-thies", "Les Makers de Thiès", "Thiès", FATOU, "Les ateliers de réparation partagés"),
			new Decouverte("casa-web", "Casa Web Studio", "Casablanca", FATIMA, "La vitrine des ateliers de couture"),
			new Decouverte("douala-numerique", "Douala Numérique", "Douala", MARIAM, "La réservation des terrains de quartier"),
			new Decouverte("cotonou-creatif", "Cotonou Créatif", "Cotonou", YANN, "Les commandes des artisans locaux"),
			new Decouverte("tunis-code", "Tunis Code Lab", "Tunis", SAMIRA, "Les factures des petites entreprises"),
			new Decouverte("ubuntu-builders", "Ubuntu Builders", "Cape Town", THABO, "Le partage de matériel entre associations"),
			new Decouverte("freetown-devs", "Freetown Devs", "Freetown", AMINA, "Les annonces des commerces de proximité"),
			new Decouverte("agritech", "AgriTech Ensemble", "Ouagadougou", MOUSSA, "Les alertes météo pour les maraîchers"),
			new Decouverte("artisanes-web", "Les Artisanes du Web", "Dakar", FATOU, "Les catalogues des créatrices de Dakar"),
			new Decouverte("conakry-connect", "Conakry Connect", "Conakry", AMINA, "Le suivi des cotisations des associations"),
			new Decouverte("luanda-digital", "Luanda Digital", "Luanda", TENDAI, "Les disponibilités des salles de formation"),
			new Decouverte("maputo-makers", "Maputo Makers", "Maputo", NIA, "La collecte des déchets du quartier"),
			new Decouverte("tech-ecole", "Tech pour l’école", "Abidjan", YANN, "Le prêt de manuels entre élèves"),
			new Decouverte("addis-code", "Addis Creative Code", "Addis-Abeba", KOFI, "Les ressources de cours hors ligne"),
			new Decouverte("nouakchott", "Nouakchott Ensemble", "Nouakchott", IBRAHIMA, "Les trajets partagés vers l’université"),
			new Decouverte("antananarivo", "Antananarivo Studio", "Antananarivo", ZAWADI, "Les stocks des coopératives artisanales"));

	private static final List<Personne> MEMBRES = List.of(KOFI, FATOU, MOUSSA, NIA, YANN, CHIDI, ZAWADI,
			AMINA, THABO, SAMIRA, IBRAHIMA, KWAME, FATIMA, JOSEPH, AICHA, TENDAI, MARIAM);

	private final JdbcClient base;

	private final Instant maintenant;

	DemoDecouverteCercles(JdbcClient base, Instant maintenant) {
		this.base = base;
		this.maintenant = maintenant;
	}

	static List<UUID> identifiants() {
		return CERCLES.stream().map(c -> id("cercle:decouverte:" + c.cle())).toList();
	}

	void charger() {
		for (int i = 0; i < CERCLES.size(); i++) {
			Decouverte c = CERCLES.get(i);
			var cercleId = id("cercle:decouverte:" + c.cle());
			Instant creeLe = maintenant.minus(Duration.ofDays(i + 3L));
			int taille = 4 + i % 3;
			int insere = base.sql("""
					INSERT INTO cercle (id, nom, ville, taille, createur_id, statut, cree_le)
					VALUES (:id, :nom, :ville, :taille, :createur, 'ouvert', :t)
					ON CONFLICT (id) DO NOTHING
					""")
				.param("id", cercleId).param("nom", c.nom()).param("ville", c.ville()).param("taille", taille)
				.param("createur", c.createur().id()).param("t", Timestamp.from(creeLe)).update();
			if (insere == 0) {
				continue;
			}
			List<Personne> autres = MEMBRES.stream().filter(p -> !p.id().equals(c.createur().id()))
				.limit(i % (taille - 1)).toList();
			var membres = new java.util.ArrayList<Personne>();
			membres.add(c.createur());
			membres.addAll(autres);
			for (int n = 0; n < membres.size(); n++) {
				Personne p = membres.get(n);
				String titre = n == 0 ? c.idee() : "Le projet de " + p.nom();
				base.sql("""
						INSERT INTO cercle_membre (cercle_id, utilisateur_id, rejoint_le, idee_titre, idee_description)
						VALUES (:c, :u, :t, :titre, :description)
						""")
					.param("c", cercleId).param("u", p.id()).param("t", Timestamp.from(creeLe.plus(Duration.ofHours(n))))
					.param("titre", titre).param("description", titre + ", à construire ensemble avec une connexion limitée.")
					.update();
			}
		}
	}

}
