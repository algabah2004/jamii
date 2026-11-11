package dev.jamii.api.auth;

/**
 * Envoi d'un e-mail transactionnel. Le code métier ne parle jamais à un fournisseur : il passe par cette interface,
 * avec un adaptateur simulé (local, tests) et, plus tard, un adaptateur SMTP Systalink. On change par la
 * configuration (`jamii.email.fournisseur`), jamais en réécrivant le code (AGENTS.md, §3).
 */
public interface EnvoiEmail {

	/** Texte brut, en UTF-8. */
	void envoyer(String destinataire, String objet, String texte);

}
