package dev.jamii.api.demo;

import static dev.jamii.api.demo.DonneesDemo.AWA;
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
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

import dev.jamii.api.demo.DonneesDemo.Personne;

/**
 * Les communautés de la démo (#155) : toutes les communautés de départ ont des membres, leurs trois salons ont des
 * conversations, et leur fil a des publications à chaque étape de l'entraide (ouverte, réponse de l'IA validée par un
 * pair, réponse humaine acceptée, sans réponse, « Montre ton projet »).
 * <p>
 * Écrit directement en base, seulement avec le profil `demo` (jamais en production). Chaque ligne a un identifiant
 * stable et s'insère avec `ON CONFLICT DO NOTHING` : recharger complète ce qui manque, sans doublon ni écrasement.
 */
final class DemoCommunautes {

	// Des personnes en plus, de tout le continent : elles n'existent que pour faire vivre les communautés.
	static final Personne CHIDI = new Personne("chidi", "Chidi Okafor");
	static final Personne ZAWADI = new Personne("zawadi", "Zawadi Otieno");
	static final Personne AMINA = new Personne("amina", "Amina Diallo");
	static final Personne THABO = new Personne("thabo", "Thabo Nkosi");
	static final Personne SAMIRA = new Personne("samira", "Samira Ben Salah");
	static final Personne IBRAHIMA = new Personne("ibrahima", "Ibrahima Fall");
	static final Personne KWAME = new Personne("kwame", "Kwame Asante");
	static final Personne FATIMA = new Personne("fatima", "Fatima Zahra El Idrissi");
	static final Personne JOSEPH = new Personne("joseph", "Joseph Habimana");
	static final Personne AICHA = new Personne("aicha", "Aïcha Keïta");
	static final Personne TENDAI = new Personne("tendai", "Tendai Moyo");
	static final Personne MARIAM = new Personne("mariam", "Mariam Ngo Biyong");

	/** Un message de salon, envoyé il y a `minutes` minutes (unique par salon : il sert à son identifiant). */
	private record Message(Personne qui, String texte, int minutes) {
	}

	private final JdbcClient base;

	private final Instant maintenant;

	DemoCommunautes(JdbcClient base, Instant maintenant) {
		this.base = base;
		this.maintenant = maintenant;
	}

	void charger() {
		for (Personne p : List.of(CHIDI, ZAWADI, AMINA, THABO, SAMIRA, IBRAHIMA, KWAME, FATIMA, JOSEPH, AICHA, TENDAI, MARIAM)) {
			personne(p);
		}

		adhesions("flutter", AWA, KOFI, FATOU, NIA, YANN, CHIDI, ZAWADI, IBRAHIMA);
		adhesions("fintech", AWA, MOUSSA, YANN, KOFI, AMINA, THABO);
		adhesions("ia", NIA, FATOU, KOFI, AWA, SAMIRA, ZAWADI);
		adhesions("web", YANN, MOUSSA, SAMIRA, IBRAHIMA, AWA, CHIDI);
		adhesions("cloud", MOUSSA, THABO, AWA, KOFI, SAMIRA);
		adhesions("antenne-dakar", AWA, FATOU, MOUSSA, IBRAHIMA);
		adhesions("antenne-nairobi", NIA, ZAWADI);
		adhesions("antenne-abidjan", KOFI, YANN, AMINA);
		adhesions("antenne-lagos", CHIDI, THABO);

		flutter();
		fintech();
		ia();
		web();
		cloud();
		antennes();
		questions();
		encore();
	}

	// ---------------------------------------------------------------- les salons

	private void flutter() {
		salon("flutter", "general", List.of(
				new Message(YANN, "Bonjour à tous, je suis Yann, d'Abidjan. Je cherche une équipe pour une appli de rendez-vous médicaux.", 26 * 60),
				new Message(KOFI, "Bienvenue Yann ! Passe dans #projets, il y a souvent des cercles qui cherchent du monde.", 25 * 60 + 50),
				new Message(YANN, "Merci Kofi, je regarde ça ce soir.", 25 * 60 + 45),
				new Message(FATOU, "Petit rappel : l'atelier Flutter de l'antenne de Dakar a lieu samedi à 10 h.", 95),
				new Message(NIA, "Je le suivrai à distance depuis Nairobi si quelqu'un lance l'appel du salon.", 80),
				new Message(FATOU, "Oui, je l'ouvre ici même, bouton « Rejoindre l'appel » en haut.", 78),
				new Message(AWA, "Je serai là aussi. On montre la carte de compétences à la fin ?", 20)));
		salon("flutter", "entraide", List.of(
				new Message(NIA, "Quelqu'un a déjà gardé des commandes hors ligne avec Flutter ? Mes clients à Nakuru perdent le réseau.", 42),
				new Message(KOFI, "Hive marche bien pour ça. Je synchronise quand le réseau revient, avec un horodatage par commande.", 38),
				new Message(YANN, "Pareil, et j'affiche « En attente d'envoi » sur chaque commande : les vendeurs sont rassurés.", 31),
				new Message(NIA, "Merci, je teste Hive ce soir et je vous dis.", 29),
				new Message(ZAWADI, "Pensez aussi à limiter la taille des images envoyées, la 3G coûte cher ici.", 12)));
		salon("flutter", "projets", List.of(
				new Message(KOFI, "Je montre mon carnet des coopératives de cacao, hors ligne d'abord. Retours bienvenus !", 30 * 60),
				new Message(NIA, "Très propre. La synchro à la reconnexion, tu la fais comment ?", 29 * 60 + 40),
				new Message(KOFI, "Une file de changements en attente, rejouée dans l'ordre quand le réseau revient.", 29 * 60 + 30),
				new Message(AWA, "Une PME de Thiès cherche deux devs Flutter pour une appli de commandes. Qui est dispo ?", 50),
				new Message(YANN, "Moi, à partir de lundi. Je travaille à distance depuis Abidjan.", 44),
				new Message(IBRAHIMA, "Je suis à Saint-Louis, partant aussi si vous formez un cercle.", 40)));
	}

	private void fintech() {
		salon("fintech", "general", List.of(
				new Message(MOUSSA, "Qui a déjà branché Wave et Orange Money dans la même appli ? Les webhooks n'ont pas le même format.", 3 * 60),
				new Message(YANN, "Moi. Je les ramène tous les deux à un même événement « paiement reçu » dans mon API, ça simplifie tout.", 2 * 60 + 40),
				new Message(AWA, "Et pensez à vérifier la signature de chaque webhook, on a eu de faux paiements une fois.", 2 * 60 + 30),
				new Message(AMINA, "Bonjour, Amina de Conakry. On lance un portefeuille pour les commerçantes du marché de Madina.", 70)));
		salon("fintech", "entraide", List.of(
				new Message(THABO, "Comment vous gérez les remboursements partiels avec M-Pesa ? L'API ne les fait pas directement.", 5 * 60),
				new Message(KOFI, "On crée un nouveau paiement sortant et on relie les deux dans notre base. Pas élégant, mais traçable.", 4 * 60 + 30),
				new Message(THABO, "D'accord, je fais pareil. Merci Kofi.", 4 * 60)));
		salon("fintech", "projets", List.of(
				new Message(AWA, "La boutique des tisserandes accepte maintenant Wave. Premier vrai paiement reçu ce matin !", 28 * 60),
				new Message(MOUSSA, "Bravo ! Tu montres le parcours de paiement dans le fil ?", 27 * 60 + 30),
				new Message(AWA, "Oui, je poste ça ce soir.", 27 * 60)));
	}

	private void ia() {
		salon("ia", "general", List.of(
				new Message(NIA, "Quelqu'un a testé la reconnaissance vocale en wolof ou en swahili ?", 3 * 60),
				new Message(FATOU, "Pour le wolof, il y a des jeux de données ouverts, mais il faut encore beaucoup nettoyer.", 2 * 60 + 40),
				new Message(KOFI, "Je prépare un petit comparatif, je le partage ici dès qu'il est prêt.", 35)));
		salon("ia", "entraide", List.of(
				new Message(SAMIRA, "Mon modèle de classification de factures se trompe sur les montants en dinars. Une idée ?", 6 * 60),
				new Message(ZAWADI, "Vérifie le séparateur décimal : la virgule et le point changent tout selon le pays.", 5 * 60 + 40),
				new Message(SAMIRA, "C'était exactement ça. Merci !", 5 * 60 + 20)));
		salon("ia", "projets", List.of(
				new Message(ZAWADI, "Je cherche deux personnes pour un assistant agricole en swahili, par SMS.", 22 * 60),
				new Message(NIA, "Je suis partante, j'ai déjà la partie SMS.", 21 * 60 + 30)));
	}

	private void web() {
		salon("web", "general", List.of(
				new Message(SAMIRA, "Bonjour de Tunis ! Qui fait du Vue ou du Svelte ici ?", 9 * 60),
				new Message(YANN, "Vue pour moi. Jamii est fait en Vue d'ailleurs.", 8 * 60 + 40),
				new Message(IBRAHIMA, "Svelte pour mes projets perso, Vue au travail.", 8 * 60 + 10)));
		salon("web", "entraide", List.of(
				new Message(CHIDI, "Mon site met 9 secondes à charger en 3G à Lagos. Par où je commence ?", 4 * 60),
				new Message(MOUSSA, "Images en WebP, polices servies par ton serveur, et du cache. On est passés de 8 s à 2 s.", 3 * 60 + 40),
				new Message(CHIDI, "Je teste tout ça demain, merci Moussa.", 3 * 60 + 20)));
		salon("web", "projets", List.of(
				new Message(MOUSSA, "L'alerte coupure d'eau est en ligne : les habitants reçoivent un SMS avant chaque coupure.", 32 * 60),
				new Message(AWA, "Super utile. Tu l'as déployée où ?", 31 * 60 + 30),
				new Message(MOUSSA, "Sur DataCloud, en un clic depuis Jamii.", 31 * 60)));
	}

	private void cloud() {
		salon("cloud", "general", List.of(
				new Message(THABO, "Retour d'expérience : DataCloud tient très bien la charge pour notre API de réservations.", 10 * 60),
				new Message(MOUSSA, "Vous avez gardé une base PostgreSQL gérée ou la vôtre ?", 9 * 60 + 30),
				new Message(THABO, "Gérée. Les sauvegardes automatiques nous ont déjà sauvés une fois.", 9 * 60)));
		salon("cloud", "entraide", List.of(
				new Message(SAMIRA, "Mon conteneur redémarre en boucle après le déploiement. Le journal dit « port déjà utilisé ».", 2 * 60),
				new Message(KOFI, "Lis la variable PORT imposée par l'hébergeur au lieu de mettre 8080 en dur.", 100),
				new Message(SAMIRA, "Réglé, merci !", 90)));
		salon("cloud", "projets", List.of(
				new Message(AWA, "Je cherche quelqu'un pour relire notre Dockerfile avant la mise en ligne de lundi.", 15 * 60),
				new Message(THABO, "Envoie, je regarde ce soir.", 14 * 60 + 30)));
	}

	private void antennes() {
		salon("antenne-dakar", "general", List.of(
				new Message(FATOU, "Rencontre de l'antenne jeudi à 19 h. On parle déploiement sur le cloud Systalink.", 4 * 60),
				new Message(AWA, "Je viens, et j'amène deux personnes de mon cercle.", 3 * 60 + 30),
				new Message(MOUSSA, "Je prends l'appel en vidéo pour ceux qui sont à Thiès.", 3 * 60)));
		salon("antenne-dakar", "entraide", List.of(
				new Message(IBRAHIMA, "Quelqu'un connaît un espace de travail calme à Saint-Louis avec une bonne connexion ?", 7 * 60),
				new Message(FATOU, "Demande à l'université, ils ouvrent la salle informatique le samedi.", 6 * 60 + 30)));
		salon("antenne-dakar", "projets", List.of(
				new Message(AWA, "Une association de Rufisque cherche un site simple pour ses inscriptions. Bénévole, mais utile.", 20 * 60),
				new Message(IBRAHIMA, "Je prends, j'ai du temps ce mois-ci.", 19 * 60)));

		salon("antenne-abidjan", "general", List.of(
				new Message(KOFI, "On organise un apéro dev vendredi à Cocody. Qui vient ?", 12 * 60),
				new Message(YANN, "Présent !", 11 * 60 + 40),
				new Message(AMINA, "Je passe à Abidjan ce week-end, je me joins à vous.", 11 * 60)));
		salon("antenne-abidjan", "entraide", List.of(
				new Message(YANN, "Quel hébergeur pour un petit site à moins de 5 000 FCFA par mois ?", 30 * 60),
				new Message(KOFI, "DataCloud a une offre d'entrée qui suffit largement.", 29 * 60)));
		salon("antenne-abidjan", "projets", List.of(
				new Message(KOFI, "L'annuaire des artisans d'Abidjan cherche quelqu'un pour la carte interactive.", 40 * 60)));

		salon("antenne-lagos", "general", List.of(
				new Message(CHIDI, "Welcome to the Lagos chapter! On peut écrire en anglais ou en français ici.", 48 * 60),
				new Message(THABO, "Hello from Johannesburg, I'll follow the meetups online.", 47 * 60)));
		salon("antenne-lagos", "entraide", List.of(
				new Message(CHIDI, "Anyone tried Paystack with USSD fallback?", 26 * 60)));
		salon("antenne-lagos", "projets", List.of(
				new Message(THABO, "Looking for a backend dev for a ride-sharing pilot in Lagos and Joburg.", 50 * 60)));

		salon("antenne-nairobi", "general", List.of(
				new Message(NIA, "Habari ! Prochaine rencontre de l'antenne au mois de décembre.", 36 * 60),
				new Message(ZAWADI, "Je m'occupe de la salle.", 35 * 60)));
		salon("antenne-nairobi", "entraide", List.of(
				new Message(ZAWADI, "Quelqu'un a l'habitude d'Africa's Talking pour les SMS ?", 18 * 60),
				new Message(NIA, "Oui, je t'envoie mon exemple en message.", 17 * 60)));
		salon("antenne-nairobi", "projets", List.of(
				new Message(NIA, "Le covoiturage vers le marché tourne à Nakuru depuis une semaine !", 44 * 60)));
	}

	// ---------------------------------------------------------------- le fil

	private void questions() {
		UUID flutter = communaute("flutter");
		// Une question ouverte : l'IA a proposé une réponse, personne ne l'a encore vérifiée.
		UUID riverpod = publication("riverpod", flutter, KOFI, "question", "Comment tester un provider Riverpod qui appelle le réseau ?",
				"Mes tests passent en local mais échouent sur la CI : le provider appelle vraiment l'API. Comment le simuler proprement ?",
				"prete", 3, null);
		reponse("riverpod-ia", riverpod, null, null,
				"Remplace le client HTTP par une version simulée avec `overrides` dans `ProviderContainer`. Tes tests ne touchent plus le réseau, et la CI devient stable. À vérifier par un membre.",
				Duration.ofHours(3).minusMinutes(1));

		// Une question résolue par la réponse de l'IA, validée par un pair (elle ne compte sur le profil de personne).
		UUID apk = publication("apk", flutter, NIA, "question", "Réduire la taille de l'APK pour les téléphones d'entrée de gamme",
				"Mon APK fait 48 Mo et beaucoup d'utilisateurs ont peu de stockage. Par où commencer ?", "prete", 26, null);
		UUID apkIa = reponse("apk-ia", apk, null, YANN,
				"Publie un App Bundle (un APK par architecture), active `--split-debug-info` et `--obfuscate`, et compresse les images en WebP. On passe souvent sous les 20 Mo.",
				Duration.ofHours(25));
		accepter(apk, apkIa, 24);

		publication("demo-vendredi", flutter, FATOU, "discussion", "Qui vient à l'atelier Flutter de samedi ?",
				"On sera une quinzaine à Dakar, et l'appel du salon sera ouvert pour celles et ceux qui suivent à distance.", null, 20, null);
		montrer("carnet-cacao", flutter, KOFI, "Le carnet des coopératives de cacao, hors ligne d'abord",
				"Les agents de terrain saisissent les pesées sans réseau ; tout se synchronise au retour en ville. Vos retours sur l'écran de saisie m'intéressent.",
				30);

		// Une question résolue par un humain : la réponse acceptée de Kofi.
		UUID fintech = communaute("fintech");
		UUID webhook = publication("webhook", fintech, MOUSSA, "question", "Comment vérifier qu'un webhook de paiement vient bien de l'opérateur ?",
				"On a reçu un faux « paiement reçu » sur notre boutique. Comment s'assurer qu'un webhook est authentique ?", "indisponible", 50, null);
		UUID deKofi = reponse("webhook-kofi", webhook, KOFI, null,
				"Vérifie la signature HMAC de chaque requête avec le secret que l'opérateur te donne, refuse les horodatages de plus de 5 minutes, et ne crois jamais le montant du webhook : relis le paiement auprès de l'API de l'opérateur.",
				Duration.ofHours(48));
		accepter(webhook, deKofi, 47);
		publication("ussd", fintech, YANN, "question", "Une appli USSD peut-elle partager le code de notre API REST ?",
				"Nos clients sans smartphone passent par l'USSD. Peut-on réutiliser la même logique métier que l'appli mobile ?", "indisponible", 6, null);
		montrer("tisserandes", fintech, AWA, "La boutique des tisserandes accepte Wave",
				"Premier vrai paiement reçu ce matin. Le parcours tient en trois écrans, je vous montre comment on gère l'attente de confirmation.",
				26);

		UUID ia = communaute("ia");
		UUID wolof = publication("wolof", ia, NIA, "question", "Où trouver des données vocales en wolof pour un premier modèle ?",
				"Je veux transcrire des messages vocaux de commerçants. Quels jeux de données ouverts existent, et sous quelle licence ?", "prete", 30, null);
		UUID wolofFatou = reponse("wolof-fatou", wolof, FATOU, null,
				"Commence par les corpus ouverts des universités de Dakar, et vérifie bien la licence de chacun. Enregistre aussi une centaine de phrases avec tes commerçants, avec leur accord écrit.",
				Duration.ofHours(28));
		accepter(wolof, wolofFatou, 27);
		publication("budget-ia", ia, SAMIRA, "discussion", "Combien vous coûte un modèle de langue en production ?",
				"On partage nos chiffres ? Chez nous, environ 40 000 FCFA par mois pour 2 000 utilisateurs, avec un cache.", null, 9, null);

		UUID web = communaute("web");
		UUID troisG = publication("3g", web, CHIDI, "question", "Comment rendre un site rapide en 3G sans tout réécrire ?",
				"Mon site met 9 secondes à charger à Lagos. Je n'ai pas le temps de tout refaire : quelles sont les trois premières choses à faire ?", "prete", 5, null);
		reponse("3g-ia", troisG, null, null,
				"Mesure d'abord avec un test en 3G simulée. Ensuite : images en WebP et à la bonne taille, polices servies par ton serveur avec `font-display: swap`, et cache long sur les fichiers statiques. À vérifier par un membre.",
				Duration.ofHours(5).minusMinutes(1));
		montrer("alerte-eau", web, MOUSSA, "L'alerte coupure d'eau, en ligne sur DataCloud",
				"Les habitants s'inscrivent avec leur numéro et reçoivent un SMS avant chaque coupure. Déployé en un clic depuis Jamii.", 32);

		UUID cloud = communaute("cloud");
		publication("sauvegardes", cloud, THABO, "question", "Quelle stratégie de sauvegarde pour une petite base PostgreSQL ?",
				"Une base de 2 Go, une PME de 10 personnes. Sauvegarde chaque nuit, mais où, et combien de temps on garde ?", "indisponible", 11, null);
		publication("dockerfile", cloud, AWA, "discussion", "Vos astuces pour un Dockerfile léger ?",
				"On passe de 900 Mo à combien chez vous ? Je partage le nôtre dans #projets.", null, 15, null);

		UUID dakar = communaute("antenne-dakar");
		publication("rencontre-jeudi", dakar, FATOU, "discussion", "Rencontre de l'antenne jeudi à 19 h",
				"Au programme : déployer un projet sur le cloud Systalink, puis les projets de chacun. L'appel du salon sera ouvert.", null, 4, null);
		UUID abidjan = communaute("antenne-abidjan");
		publication("apero-cocody", abidjan, KOFI, "discussion", "Apéro dev vendredi à Cocody",
				"On se retrouve à 19 h pour parler projets et missions. Ceux qui ne peuvent pas venir : l'appel du salon sera ouvert.", null, 12, null);
		UUID lagos = communaute("antenne-lagos");
		publication("paystack-ussd", lagos, CHIDI, "question", "Paystack avec repli USSD : quelqu'un l'a fait ?",
				"Une partie de nos clients n'a pas de carte. Comment proposer un repli USSD sans dupliquer toute la logique de paiement ?", "indisponible", 26, null);
		UUID nairobi = communaute("antenne-nairobi");
		montrer("covoiturage", nairobi, NIA, "Le covoiturage vers le marché tourne à Nakuru",
				"Une semaine en vrai : 40 trajets partagés. Je cherche quelqu'un pour la partie paiement M-Pesa.", 44);
	}

	// ---------------------------------------------------------------- encore plus de vie

	/** Plus de monde, des conversations plus longues, et des publications avec plusieurs réponses. */
	private void encore() {
		membres("flutter", KWAME, MARIAM, TENDAI);
		membres("fintech", FATIMA, KWAME, MARIAM, AICHA);
		membres("ia", JOSEPH, FATIMA, AICHA);
		membres("web", KWAME, JOSEPH, MARIAM, TENDAI);
		membres("cloud", TENDAI, JOSEPH, FATIMA);
		membres("antenne-dakar", AICHA);
		membres("antenne-abidjan", MARIAM);
		membres("antenne-lagos", KWAME, TENDAI);
		membres("antenne-nairobi", JOSEPH);

		salon("flutter", "general", List.of(
				new Message(KWAME, "Salut tout le monde, Kwame d'Accra. Je fais du Flutter pour des cliniques depuis deux ans.", 18),
				new Message(MARIAM, "Bienvenue Kwame ! Mariam, de Douala. On cherche justement quelqu'un qui connaît le secteur santé.", 16),
				new Message(KWAME, "Avec plaisir, écris-moi en message.", 14),
				new Message(TENDAI, "Petite question : qui est passé à Impeller ? Les animations sont enfin fluides sur mon vieux Tecno.", 9),
				new Message(CHIDI, "Moi. Le gain est net sur les téléphones à 80 000 FCFA.", 5)));
		salon("flutter", "entraide", List.of(
				new Message(MARIAM, "Mon appli plante au démarrage seulement sur Android 8. Quelqu'un a déjà eu ça ?", 11),
				new Message(KWAME, "Regarde la version minimale du SDK et les plugins : un seul plugin trop récent suffit.", 10),
				new Message(MARIAM, "C'était le plugin de caméra. Merci !", 8),
				new Message(IBRAHIMA, "Bon réflexe à noter dans le fil, ça va resservir.", 7)));
		salon("flutter", "projets", List.of(
				new Message(KWAME, "Je monte un carnet de vaccination hors ligne pour des cliniques rurales. Qui veut faire le cercle avec moi ?", 33),
				new Message(TENDAI, "Partant. J'ai fait la synchro d'un projet similaire à Harare.", 30),
				new Message(MARIAM, "Moi aussi, je prends l'interface.", 26)));

		salon("fintech", "general", List.of(
				new Message(FATIMA, "Bonjour de Casablanca ! On travaille sur des paiements par QR code pour les petits commerces.", 65),
				new Message(AMINA, "Intéressant, on a le même besoin à Conakry. Vous utilisez quel format de QR ?", 60),
				new Message(FATIMA, "Un format ouvert, je te partage le lien de la spécification dans #projets.", 55),
				new Message(KWAME, "Au Ghana, MoMo fait déjà ça, je peux comparer avec vous.", 47)));
		salon("fintech", "entraide", List.of(
				new Message(AICHA, "Comment vous testez les paiements sans dépenser de vrai argent ?", 230),
				new Message(MOUSSA, "Les opérateurs ont presque tous un mode bac à sable. Sinon, on simule l'opérateur derrière une interface.", 210),
				new Message(YANN, "Et on garde un test de bout en bout avec 100 FCFA réels, une fois par semaine.", 200),
				new Message(AICHA, "Bonne idée, merci à vous deux.", 190)));
		salon("fintech", "projets", List.of(
				new Message(FATIMA, "Voici notre prototype de paiement par QR, à tester avec n'importe quel portefeuille.", 1600),
				new Message(MARIAM, "Testé à Douala, ça marche avec Orange Money. Bravo !", 1580)));

		salon("ia", "general", List.of(
				new Message(JOSEPH, "Joseph, de Kigali. Je travaille sur un modèle de traduction kinyarwanda-français.", 30),
				new Message(AICHA, "Génial ! Et pour le bambara, quelqu'un a des pistes ?", 25),
				new Message(FATOU, "Il y a des enregistrements de radios communautaires, à demander avec leur accord.", 22)));
		salon("ia", "entraide", List.of(
				new Message(JOSEPH, "Mon modèle tient en mémoire sur mon PC mais pas sur le serveur. Des astuces ?", 300),
				new Message(FATIMA, "Quantifie-le en 8 bits : on a divisé la mémoire par quatre sans perte visible.", 280),
				new Message(JOSEPH, "Je teste ça, merci Fatima.", 270)));
		salon("ia", "projets", List.of(
				new Message(AICHA, "On lance un correcteur d'orthographe pour le bambara. Il nous manque une personne pour l'API.", 1250),
				new Message(SAMIRA, "Je peux aider le soir, je fais de l'API depuis cinq ans.", 1230)));

		salon("web", "general", List.of(
				new Message(KWAME, "Qui utilise des PWA en production ? Nos utilisateurs adorent ne rien installer.", 470),
				new Message(MARIAM, "Nous, pour une boutique en ligne. L'icône sur l'écran d'accueil change tout.", 450),
				new Message(TENDAI, "Attention au cache, on a servi une vieille version pendant deux jours.", 430)));
		salon("web", "entraide", List.of(
				new Message(JOSEPH, "Comment vous gérez les traductions français, anglais et kinyarwanda dans une appli Vue ?", 180),
				new Message(YANN, "Vue I18n, un fichier par langue, et un test qui vérifie que les clés sont les mêmes partout.", 170),
				new Message(JOSEPH, "Le test des clés, je n'y avais pas pensé. Merci !", 160)));
		salon("web", "projets", List.of(
				new Message(TENDAI, "Je cherche des testeurs pour un site d'offres d'emploi qui marche en 2G.", 1800),
				new Message(CHIDI, "Je teste depuis Lagos ce week-end.", 1780)));

		salon("cloud", "general", List.of(
				new Message(TENDAI, "Quelqu'un a mesuré le temps de réponse de DataCloud depuis l'Afrique australe ?", 520),
				new Message(THABO, "Depuis Johannesburg, c'est correct pour une API. Pour la vidéo, on passe par un réseau de diffusion.", 500),
				new Message(JOSEPH, "Depuis Kigali aussi, aucun souci pour nous.", 480)));
		salon("cloud", "entraide", List.of(
				new Message(FATIMA, "Mes certificats HTTPS expirent sans prévenir. Comment vous faites ?", 80),
				new Message(MOUSSA, "Renouvellement automatique, et une alerte 15 jours avant au cas où.", 70),
				new Message(FATIMA, "Parfait, je mets ça en place.", 60)));
		salon("cloud", "projets", List.of(
				new Message(JOSEPH, "On ouvre notre modèle de traduction en API. Retour bienvenu sur la configuration.", 850)));

		salon("antenne-dakar", "general", List.of(
				new Message(AICHA, "Bonjour l'antenne ! Je suis de Bamako mais à Dakar pour trois mois.", 170),
				new Message(FATOU, "Bienvenue Aïcha ! Viens jeudi, on se présente tous.", 160)));
		salon("antenne-abidjan", "general", List.of(
				new Message(MARIAM, "Je serai à Abidjan pour la conférence, on se retrouve à l'apéro ?", 640),
				new Message(KOFI, "Avec plaisir, je t'ajoute à la liste.", 630)));
		salon("antenne-lagos", "general", List.of(
				new Message(KWAME, "Greetings from Accra! Il y a beaucoup de liens à faire entre nos deux villes.", 2700),
				new Message(TENDAI, "Count me in for an online meetup.", 2650)));
		salon("antenne-nairobi", "general", List.of(
				new Message(JOSEPH, "Je viens de Kigali pour la rencontre de décembre, quelqu'un peut m'héberger une nuit ?", 2000),
				new Message(ZAWADI, "Oui, écris-moi.", 1980)));

		UUID flutter = communaute("flutter");
		UUID android8 = publication("android8", flutter, MARIAM, "question", "Mon appli plante au démarrage sur Android 8",
				"Tout marche sur les téléphones récents, mais sur Android 8 l'appli se ferme dès l'ouverture. Par où chercher ?", "indisponible", 1, null);
		reponse("android8-kwame", android8, KWAME, null,
				"Regarde d'abord la version minimale du SDK de chaque plugin : un seul plugin trop récent suffit à faire planter les anciens Android.",
				Duration.ofMinutes(40));
		UUID android8Ok = reponse("android8-ibrahima", android8, IBRAHIMA, null,
				"Lance `flutter run` sur un émulateur Android 8 et lis la première erreur du journal : chez moi c'était le plugin de caméra.",
				Duration.ofMinutes(30));
		accepter(android8, android8Ok, 0);
		montrer("vaccination", flutter, KOFI, "Un carnet de vaccination hors ligne pour les cliniques rurales",
				"Avec Kwame, Tendai et Mariam, on monte un cercle autour de ce projet. Voici les premiers écrans.", 2);
		publication("impeller", flutter, TENDAI, "discussion", "Impeller sur les téléphones d'entrée de gamme : vos retours ?",
				"Chez moi les animations sont enfin fluides sur un vieux Tecno. Et vous ?", null, 1, null);

		UUID fintech = communaute("fintech");
		UUID tests = publication("tests-paiement", fintech, AICHA, "question", "Comment tester des paiements sans dépenser de vrai argent ?",
				"On veut des tests automatiques sur nos paiements Mobile Money. Comment faites-vous ?", "prete", 4, null);
		reponse("tests-paiement-ia", tests, null, null,
				"Place l'opérateur derrière une interface et branche une version simulée dans tes tests. Garde un test réel de petit montant, lancé à la main. À vérifier par un membre.",
				Duration.ofHours(4).minusMinutes(1));
		reponse("tests-paiement-moussa", tests, MOUSSA, null,
				"Les opérateurs ont presque tous un bac à sable. On l'utilise pour les tests de bout en bout, et une simulation pour le reste.",
				Duration.ofHours(3));
		montrer("qr-commerce", fintech, MOUSSA, "Payer par QR code chez le petit commerçant",
				"Un prototype qui marche avec n'importe quel portefeuille mobile. Fatima l'a testé à Casablanca, Mariam à Douala.", 25);

		UUID ia = communaute("ia");
		UUID memoire = publication("memoire-modele", ia, JOSEPH, "question", "Mon modèle ne tient pas en mémoire sur le serveur",
				"Il marche sur mon PC avec 32 Go, mais le serveur n'en a que 8. Que faire sans tout réentraîner ?", "prete", 5, null);
		UUID memoireIa = reponse("memoire-ia", memoire, null, FATIMA,
				"Quantifie le modèle en 8 bits : la mémoire est divisée par quatre, avec une perte de qualité souvent invisible. Charge-le en paresseux, au premier appel.",
				Duration.ofHours(5).minusMinutes(1));
		accepter(memoire, memoireIa, 4);
		publication("bambara", ia, AICHA, "discussion", "Un correcteur d'orthographe pour le bambara",
				"On cherche des textes libres de droits et une personne pour l'API. Toutes les idées sont bienvenues.", null, 20, null);

		UUID web = communaute("web");
		UUID traductions = publication("traductions", web, JOSEPH, "question", "Gérer trois langues dans une appli Vue sans s'y perdre",
				"Français, anglais et kinyarwanda : comment éviter qu'une clé manque dans une langue ?", "indisponible", 3, null);
		UUID tradYann = reponse("traductions-yann", traductions, YANN, null,
				"Un fichier par langue avec Vue I18n, et un test qui compare les clés de chaque fichier : il échoue dès qu'une traduction manque.",
				Duration.ofHours(2));
		accepter(traductions, tradYann, 1);
		publication("pwa", web, KWAME, "discussion", "Les PWA en production : vos pièges",
				"On a servi une vieille version pendant deux jours à cause du cache. Partagez les vôtres !", null, 8, null);

		UUID cloud = communaute("cloud");
		UUID https = publication("https", cloud, FATIMA, "question", "Mes certificats HTTPS expirent sans prévenir",
				"Deux fois ce mois-ci, le site est tombé un dimanche. Comment l'éviter ?", "prete", 2, null);
		reponse("https-moussa", https, MOUSSA, null,
				"Renouvellement automatique par ton hébergeur, et une alerte quinze jours avant l'expiration au cas où le renouvellement échoue.",
				Duration.ofMinutes(70));
		publication("latence", cloud, TENDAI, "discussion", "Temps de réponse de DataCloud depuis votre ville",
				"Partagez vos mesures : on fait une carte des temps de réponse sur le continent.", null, 9, null);

		publication("presentations-dakar", communaute("antenne-dakar"), AICHA, "discussion", "Présentez-vous avant jeudi !",
				"Un mot sur vous et vos projets, pour qu'on sache qui chercher à la rencontre.", null, 3, null);
		publication("accra-lagos", communaute("antenne-lagos"), KWAME, "discussion", "Une rencontre en ligne Accra et Lagos ?",
				"Beaucoup de projets en commun entre nos deux villes. Qui serait partant pour un appel le mois prochain ?", null, 45, null);
	}

	// ---------------------------------------------------------------- écriture, sans doublon

	private Timestamp ilYA(Duration duree) {
		return Timestamp.from(maintenant.minus(duree));
	}

	private void personne(Personne p) {
		Timestamp t = ilYA(Duration.ofDays(40));
		base.sql("""
				INSERT INTO utilisateur (id, nom, email, cree_le, mis_a_jour_le) VALUES (:id, :nom, :email, :t, :t)
				ON CONFLICT (id) DO NOTHING
				""").param("id", p.id()).param("nom", p.nom()).param("email", p.email()).param("t", t).update();
		base.sql("INSERT INTO utilisateur_role (utilisateur_id, role) VALUES (:id, 'membre') ON CONFLICT DO NOTHING")
			.param("id", p.id()).update();
	}

	private UUID communaute(String slug) {
		return base.sql("SELECT id FROM communaute WHERE slug = :s").param("s", slug).query(UUID.class).single();
	}

	/** Le premier de la liste modère la communauté. */
	private void adhesions(String slug, Personne... membres) {
		UUID c = communaute(slug);
		int rang = 0;
		for (Personne p : membres) {
			base.sql("""
					INSERT INTO adhesion (communaute_id, utilisateur_id, role, rejoint_le) VALUES (:c, :u, :r, :t)
					ON CONFLICT (communaute_id, utilisateur_id) DO NOTHING
					""")
				.param("c", c).param("u", p.id()).param("r", rang == 0 ? "moderateur" : "membre")
				.param("t", ilYA(Duration.ofDays(30 - rang))).update();
			rang++;
		}
	}

	/** Des membres en plus, jamais modérateurs (le premier modérateur reste celui de `adhesions`). */
	private void membres(String slug, Personne... membres) {
		UUID c = communaute(slug);
		int rang = 0;
		for (Personne p : membres) {
			base.sql("""
					INSERT INTO adhesion (communaute_id, utilisateur_id, role, rejoint_le) VALUES (:c, :u, 'membre', :t)
					ON CONFLICT (communaute_id, utilisateur_id) DO NOTHING
					""")
				.param("c", c).param("u", p.id()).param("t", ilYA(Duration.ofDays(10 - rang))).update();
			rang++;
		}
	}

	/** Dans l'ordre d'envoi : le numéro des messages suit le temps, comme en vrai. */
	private void salon(String communaute, String salon, List<Message> messages) {
		UUID s = base.sql("SELECT id FROM salon WHERE communaute_id = :c AND slug = :s")
			.param("c", communaute(communaute)).param("s", salon).query(UUID.class).single();
		for (Message m : messages) {
			base.sql("""
					INSERT INTO message_salon (id, salon_id, auteur_id, texte, envoye_le) VALUES (:id, :s, :a, :t, :le)
					ON CONFLICT (id) DO NOTHING
					""")
				.param("id", id("message:" + communaute + "/" + salon + "/" + m.minutes())).param("s", s).param("a", m.qui().id())
				.param("t", m.texte()).param("le", ilYA(Duration.ofMinutes(m.minutes()))).update();
		}
	}

	private UUID publication(String cle, UUID communaute, Personne auteur, String type, String titre, String contenu,
			String propositionIa, int heures, UUID projet) {
		UUID pid = id("publication:" + cle);
		base.sql("""
				INSERT INTO publication (id, communaute_id, auteur_id, type, titre, contenu, projet_id, proposition_ia, cree_le)
				VALUES (:id, :c, :a, :type, :titre, :contenu, :projet, :ia, :t)
				ON CONFLICT (id) DO NOTHING
				""")
			.param("id", pid).param("c", communaute).param("a", auteur.id()).param("type", type).param("titre", titre)
			.param("contenu", contenu).param("projet", projet).param("ia", propositionIa).param("t", ilYA(Duration.ofHours(heures)))
			.update();
		return pid;
	}

	/** « Montre ton projet » : seulement si l'auteur porte un projet de la démo (sinon, rien n'est montré). */
	private void montrer(String cle, UUID communaute, Personne auteur, String titre, String contenu, int heures) {
		Optional<UUID> projet = base.sql("SELECT id FROM projet WHERE porteur_id = :u ORDER BY cree_le, id LIMIT 1")
			.param("u", auteur.id()).query(UUID.class).optional();
		projet.ifPresent(p -> publication(cle, communaute, auteur, "montre_ton_projet", titre, contenu, null, heures, p));
	}

	/** `auteur` vide : la réponse de l'IA, éventuellement validée par `validePar`. */
	private UUID reponse(String cle, UUID publication, Personne auteur, Personne validePar, String contenu, Duration ilYa) {
		UUID rid = id("reponse:" + cle);
		base.sql("""
				INSERT INTO reponse (id, publication_id, auteur_id, contenu, proposee_par_ia, validee_par_id, cree_le)
				VALUES (:id, :p, :a, :contenu, :ia, :v, :t)
				ON CONFLICT (id) DO NOTHING
				""")
			.param("id", rid).param("p", publication).param("a", auteur == null ? null : auteur.id()).param("contenu", contenu)
			.param("ia", auteur == null).param("v", validePar == null ? null : validePar.id()).param("t", ilYA(ilYa)).update();
		return rid;
	}

	/** Une seule fois : une réponse déjà acceptée ne change pas. */
	private void accepter(UUID publication, UUID reponse, int heures) {
		base.sql("UPDATE publication SET reponse_acceptee_id = :r, acceptee_le = :t WHERE id = :p AND reponse_acceptee_id IS NULL")
			.param("r", reponse).param("t", ilYA(Duration.ofHours(heures))).param("p", publication).update();
	}

}
