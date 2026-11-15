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
import static dev.jamii.api.demo.DonneesDemo.AWA;
import static dev.jamii.api.demo.DonneesDemo.FATOU;
import static dev.jamii.api.demo.DonneesDemo.KOFI;
import static dev.jamii.api.demo.DonneesDemo.MOUSSA;
import static dev.jamii.api.demo.DonneesDemo.NIA;
import static dev.jamii.api.demo.DonneesDemo.YANN;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;

import dev.jamii.api.demo.DonneesDemo.Personne;

/**
 * Un profil publié pour chaque personne de démo : cliquer sur quelqu'un, dans un cercle ou une communauté, mène à une
 * vraie page (titre, ville, bio, compétences, langues). Compétences « déclarées » seulement : la démo ne prétend pas
 * avoir lu GitHub. Comme le reste de la démo : `ON CONFLICT DO NOTHING`, on complète sans jamais écraser.
 */
final class DemoProfils {

	private record Fiche(Personne qui, String titre, String ville, String pays, String langues, String disponibilite,
			Integer heures, String bio, List<String> competences) {
	}

	private static final List<Fiche> FICHES = List.of(
			new Fiche(AWA, "Développeuse mobile Flutter", "Dakar", "SN", "fr,wo,en", "disponible", 10,
					"J'écris des applis qui tiennent sur un téléphone d'entrée de gamme et une connexion 3G. Paiement mobile, mode hors ligne.",
					List.of("flutter", "dart", "firebase", "wave")),
			new Fiche(KOFI, "Développeur back-end", "Accra", "GH", "en,fr", "disponible", 8,
					"API, bases de données et un peu de DevOps. J'aime quand ça reste simple et que ça tient la charge.",
					List.of("java", "spring", "postgresql", "docker")),
			new Fiche(FATOU, "Product designer", "Thiès", "SN", "fr,wo", "occupe", 4,
					"Je conçois des parcours pour des gens qui n'ont jamais utilisé d'appli de leur vie. Tests sur le terrain, au marché.",
					List.of("figma", "ux-design", "recherche-utilisateur")),
			new Fiche(MOUSSA, "Développeur full-stack", "Bamako", "ML", "fr,bm", "disponible", 12,
					"Vue, Node et PostgreSQL. Je construis des outils pour les coopératives agricoles du Mali.",
					List.of("vue", "typescript", "node", "postgresql")),
			new Fiche(NIA, "Data engineer", "Nairobi", "KE", "en,sw", "disponible", 6,
					"Pipelines de données et tableaux de bord pour la santé publique. M-Pesa n'a plus de secret pour moi.",
					List.of("python", "sql", "airflow", "m-pesa")),
			new Fiche(YANN, "Développeur web", "Abidjan", "CI", "fr,en", "disponible", 10,
					"Sites et applis web pour les PME d'Abidjan. J'aime transmettre : j'anime un club de code le samedi.",
					List.of("javascript", "vue", "laravel", "mysql")),
			new Fiche(CHIDI, "Ingénieur fintech", "Lagos", "NG", "en", "occupe", 5,
					"Paiements, rapprochement bancaire et conformité. J'ai monté deux intégrations Paystack en production.",
					List.of("go", "kotlin", "paystack", "postgresql")),
			new Fiche(ZAWADI, "Développeuse Android", "Kisumu", "KE", "en,sw", "disponible", 8,
					"Kotlin, Jetpack Compose et applis qui marchent hors ligne pour les agents de terrain.",
					List.of("kotlin", "android", "jetpack-compose")),
			new Fiche(AMINA, "Développeuse front-end", "Conakry", "GN", "fr,en", "disponible", 10,
					"Accessibilité et performance : une page doit s'afficher en 3G en moins de trois secondes.",
					List.of("react", "typescript", "accessibilite", "css")),
			new Fiche(THABO, "Ingénieur cloud", "Johannesburg", "ZA", "en,zu", "disponible", 6,
					"Kubernetes, Terraform et coûts maîtrisés. J'aide les petites équipes à déployer sans se ruiner.",
					List.of("kubernetes", "terraform", "aws", "linux")),
			new Fiche(SAMIRA, "Ingénieure IA", "Tunis", "TN", "fr,ar,en", "occupe", 4,
					"Traitement du langage pour l'arabe dialectal et le français. Modèles légers, déployables sur un petit serveur.",
					List.of("python", "pytorch", "nlp", "fastapi")),
			new Fiche(IBRAHIMA, "Développeur mobile", "Saint-Louis", "SN", "fr,wo", "disponible", 10,
					"React Native et Expo. Mon dernier projet : le suivi des pirogues de pêche par SMS.",
					List.of("react-native", "expo", "typescript")),
			new Fiche(KWAME, "Développeur blockchain", "Kumasi", "GH", "en", "indisponible", null,
					"Traçabilité du cacao, de la ferme au port. En pause jusqu'à la fin de la récolte.",
					List.of("solidity", "typescript", "node")),
			new Fiche(FATIMA, "Développeuse back-end", "Casablanca", "MA", "fr,ar,en", "disponible", 8,
					"Python et Django pour des applis de gestion. Je relis volontiers le code des autres.",
					List.of("python", "django", "postgresql", "redis")),
			new Fiche(JOSEPH, "Développeur full-stack", "Kigali", "RW", "en,fr,rw", "disponible", 12,
					"Next.js et Supabase. Je construis des outils pour les écoles rwandaises.",
					List.of("nextjs", "supabase", "typescript")),
			new Fiche(AICHA, "Cheffe de projet tech", "Bamako", "ML", "fr,bm,en", "disponible", 6,
					"Je fais avancer les équipes : découpage, priorités, et des démos chaque vendredi.",
					List.of("gestion-de-projet", "scrum", "notion")),
			new Fiche(TENDAI, "Développeur Python", "Harare", "ZW", "en,sn", "disponible", 8,
					"Automatisation et scripts qui font gagner des heures aux petites entreprises.",
					List.of("python", "fastapi", "selenium")),
			new Fiche(MARIAM, "Designer UI", "Douala", "CM", "fr,en", "disponible", 6,
					"Interfaces colorées et lisibles, design systems pour les startups d'Afrique centrale.",
					List.of("figma", "design-system", "illustration")));

	private final JdbcClient base;

	private final Instant maintenant;

	DemoProfils(JdbcClient base, Instant maintenant) {
		this.base = base;
		this.maintenant = maintenant;
	}

	void charger() {
		Timestamp t = Timestamp.from(maintenant);
		for (Fiche f : FICHES) {
			base.sql("""
					INSERT INTO profil (utilisateur_id, bio, pays, ville, disponibilite, heures_par_semaine, publie, generation_statut,
					                    mis_a_jour_le, titre, langues, competences_phares)
					SELECT :id, :bio, :pays, :ville, :dispo, :heures, TRUE, 'terminee', :t, :titre, :langues, :phares
					WHERE EXISTS (SELECT 1 FROM utilisateur WHERE id = :id)
					ON CONFLICT (utilisateur_id) DO NOTHING
					""")
				.param("id", f.qui().id()).param("bio", f.bio()).param("pays", f.pays()).param("ville", f.ville())
				.param("dispo", f.disponibilite()).param("heures", f.heures()).param("t", t).param("titre", f.titre())
				.param("langues", f.langues()).param("phares", String.join(",", f.competences().subList(0, Math.min(3, f.competences().size()))))
				.update();
			for (String c : f.competences()) {
				base.sql("""
						INSERT INTO competence_profil (utilisateur_id, competence, source)
						SELECT :id, :c, 'declaree' WHERE EXISTS (SELECT 1 FROM profil WHERE utilisateur_id = :id)
						ON CONFLICT DO NOTHING
						""").param("id", f.qui().id()).param("c", c).update();
			}
		}
	}

}
