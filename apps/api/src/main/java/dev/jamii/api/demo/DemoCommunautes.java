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
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

import dev.jamii.api.demo.DonneesDemo.Personne;

/**
 * Les communautés de la démo (#155) : des membres, des conversations dans les salons, et des questions à toutes les
 * étapes de l'entraide (sans réponse, réponse de l'IA validée par un pair, réponse humaine acceptée). Écrit directement
 * en base, dans la même transaction que `DonneesDemo`, seulement avec le profil `demo` : jamais en production.
 */
final class DemoCommunautes {

	/** Un message de salon, envoyé il y a `minutes` minutes. */
	private record Message(Personne qui, String texte, int minutes) {
	}

	private final JdbcClient base;

	private final Instant maintenant;

	DemoCommunautes(JdbcClient base, Instant maintenant) {
		this.base = base;
		this.maintenant = maintenant;
	}

	void charger() {
		adhesions("flutter", AWA, KOFI, FATOU, NIA, YANN);
		adhesions("fintech", AWA, MOUSSA, YANN, KOFI);
		adhesions("ia", NIA, FATOU, KOFI, AWA);
		adhesions("antenne-dakar", AWA, FATOU, MOUSSA);
		adhesions("antenne-nairobi", NIA);
		adhesions("antenne-abidjan", KOFI, YANN);

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
				new Message(NIA, "Merci, je teste Hive ce soir et je vous dis.", 29)));
		salon("flutter", "projets", List.of(
				new Message(KOFI, "Je montre mon carnet des coopératives de cacao, hors ligne d'abord. Retours bienvenus !", 30 * 60),
				new Message(NIA, "Très propre. La synchro à la reconnexion, tu la fais comment ?", 29 * 60 + 40),
				new Message(KOFI, "Une file de changements en attente, rejouée dans l'ordre quand le réseau revient.", 29 * 60 + 30),
				new Message(AWA, "Une PME de Thiès cherche deux devs Flutter pour une appli de commandes. Qui est dispo ?", 50),
				new Message(YANN, "Moi, à partir de lundi. Je travaille à distance depuis Abidjan.", 44)));
		salon("fintech", "general", List.of(
				new Message(MOUSSA, "Qui a déjà branché Wave et Orange Money dans la même appli ? Les webhooks n'ont pas le même format.", 3 * 60),
				new Message(YANN, "Moi. Je les ramène tous les deux à un même événement « paiement reçu » dans mon API, ça simplifie tout.", 2 * 60 + 40),
				new Message(AWA, "Et pensez à vérifier la signature de chaque webhook, on a eu de faux paiements une fois.", 2 * 60 + 30)));
		salon("ia", "general", List.of(
				new Message(NIA, "Quelqu'un a testé la reconnaissance vocale en wolof ou en swahili ?", 3 * 60),
				new Message(FATOU, "Pour le wolof, il y a des jeux de données ouverts, mais il faut encore beaucoup nettoyer.", 2 * 60 + 40),
				new Message(KOFI, "Je prépare un petit comparatif, je le partage ici dès qu'il est prêt.", 35)));
		salon("antenne-dakar", "general", List.of(
				new Message(FATOU, "Rencontre de l'antenne jeudi à 19 h. On parle déploiement sur le cloud Systalink.", 4 * 60),
				new Message(AWA, "Je viens, et j'amène deux personnes de mon cercle.", 3 * 60 + 30),
				new Message(MOUSSA, "Je prends l'appel en vidéo pour ceux qui sont à Thiès.", 3 * 60)));

		questions();
	}

	private Timestamp ilYA(Duration duree) {
		return Timestamp.from(maintenant.minus(duree));
	}

	private UUID communaute(String slug) {
		return base.sql("SELECT id FROM communaute WHERE slug = :s").param("s", slug).query(UUID.class).single();
	}

	private void adhesions(String slug, Personne... membres) {
		UUID c = communaute(slug);
		int rang = 0;
		for (Personne p : membres) {
			base.sql("INSERT INTO adhesion (communaute_id, utilisateur_id, role, rejoint_le) VALUES (:c, :u, :r, :t)")
				.param("c", c).param("u", p.id()).param("r", rang == 0 ? "moderateur" : "membre")
				.param("t", ilYA(Duration.ofDays(20 - rang))).update();
			rang++;
		}
	}

	/** Dans l'ordre d'envoi : le numéro des messages suit le temps, comme en vrai. */
	private void salon(String communaute, String salon, List<Message> messages) {
		UUID s = base.sql("SELECT id FROM salon WHERE communaute_id = :c AND slug = :s")
			.param("c", communaute(communaute)).param("s", salon).query(UUID.class).single();
		for (Message m : messages) {
			base.sql("INSERT INTO message_salon (id, salon_id, auteur_id, texte, envoye_le) VALUES (:id, :s, :a, :t, :le)")
				.param("id", id("message:" + communaute + "/" + salon + "/" + m.minutes())).param("s", s).param("a", m.qui().id())
				.param("t", m.texte()).param("le", ilYA(Duration.ofMinutes(m.minutes()))).update();
		}
	}

	private void questions() {
		UUID flutter = communaute("flutter");
		// Une question ouverte : l'IA a proposé une réponse, personne ne l'a encore vérifiée.
		UUID riverpod = publication("riverpod", flutter, KOFI, "question", "Comment tester un provider Riverpod qui appelle le réseau ?",
				"Mes tests passent en local mais échouent sur la CI : le provider appelle vraiment l'API. Comment le simuler proprement ?",
				"prete", 3);
		reponse("riverpod-ia", riverpod, null, null,
				"Remplace le client HTTP par une version simulée avec `overrides` dans `ProviderContainer`. Tes tests ne touchent plus le réseau, et la CI devient stable. À vérifier par un membre.",
				Duration.ofHours(3).minusMinutes(1));

		// Une question résolue par la réponse de l'IA, validée par un pair (elle ne compte sur le profil de personne).
		UUID apk = publication("apk", flutter, NIA, "question", "Réduire la taille de l'APK pour les téléphones d'entrée de gamme",
				"Mon APK fait 48 Mo et beaucoup d'utilisateurs ont peu de stockage. Par où commencer ?", "prete", 26);
		UUID apkIa = reponse("apk-ia", apk, null, YANN,
				"Publie un App Bundle (un APK par architecture), active `--split-debug-info` et `--obfuscate`, et compresse les images en WebP. On passe souvent sous les 20 Mo.",
				Duration.ofHours(25));
		accepter(apk, apkIa, 24);

		// Une question résolue par un humain : la réponse acceptée devient une preuve sur le profil de Kofi.
		UUID fintech = communaute("fintech");
		UUID webhook = publication("webhook", fintech, MOUSSA, "question", "Comment vérifier qu'un webhook de paiement vient bien de l'opérateur ?",
				"On a reçu un faux « paiement reçu » sur notre boutique. Comment s'assurer qu'un webhook est authentique ?", "indisponible", 50);
		UUID deKofi = reponse("webhook-kofi", webhook, KOFI, null,
				"Vérifie la signature HMAC de chaque requête avec le secret que l'opérateur te donne, refuse les horodatages de plus de 5 minutes, et ne crois jamais le montant du webhook : relis le paiement auprès de l'API de l'opérateur.",
				Duration.ofHours(48));
		accepter(webhook, deKofi, 47);

		// Une discussion, et une question sans aucune réponse pour montrer « Personne n'a encore répondu ».
		publication("demo-vendredi", flutter, FATOU, "discussion", "Qui vient à l'atelier Flutter de samedi ?",
				"On sera une quinzaine à Dakar, et l'appel du salon sera ouvert pour celles et ceux qui suivent à distance.", null, 20);
		publication("ussd", fintech, YANN, "question", "Une appli USSD peut-elle partager le code de notre API REST ?",
				"Nos clients sans smartphone passent par l'USSD. Peut-on réutiliser la même logique métier que l'appli mobile ?", "indisponible", 6);
	}

	private UUID publication(String cle, UUID communaute, Personne auteur, String type, String titre, String contenu,
			String propositionIa, int heures) {
		UUID pid = id("publication:" + cle);
		base.sql("""
				INSERT INTO publication (id, communaute_id, auteur_id, type, titre, contenu, proposition_ia, cree_le)
				VALUES (:id, :c, :a, :type, :titre, :contenu, :ia, :t)
				""")
			.param("id", pid).param("c", communaute).param("a", auteur.id()).param("type", type).param("titre", titre)
			.param("contenu", contenu).param("ia", propositionIa).param("t", ilYA(Duration.ofHours(heures))).update();
		return pid;
	}

	/** `auteur` vide : la réponse de l'IA, éventuellement validée par `validePar`. */
	private UUID reponse(String cle, UUID publication, Personne auteur, Personne validePar, String contenu, Duration ilYa) {
		UUID rid = id("reponse:" + cle);
		base.sql("""
				INSERT INTO reponse (id, publication_id, auteur_id, contenu, proposee_par_ia, validee_par_id, cree_le)
				VALUES (:id, :p, :a, :contenu, :ia, :v, :t)
				""")
			.param("id", rid).param("p", publication).param("a", auteur == null ? null : auteur.id()).param("contenu", contenu)
			.param("ia", auteur == null).param("v", validePar == null ? null : validePar.id()).param("t", ilYA(ilYa)).update();
		return rid;
	}

	private void accepter(UUID publication, UUID reponse, int heures) {
		base.sql("UPDATE publication SET reponse_acceptee_id = :r, acceptee_le = :t WHERE id = :p")
			.param("r", reponse).param("t", ilYA(Duration.ofHours(heures))).param("p", publication).update();
	}

}
