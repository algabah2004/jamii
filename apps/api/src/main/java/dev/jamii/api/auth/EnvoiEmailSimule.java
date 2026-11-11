package dev.jamii.api.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Adaptateur simulé : n'envoie rien. Par défaut il ne laisse AUCUNE trace du message, car un lien de connexion
 * dans les journaux permettrait de se connecter à la place de quelqu'un. En local, `EMAIL_AFFICHER_LIEN=true`
 * l'affiche dans la console pour essayer la connexion sans serveur de messagerie. Jamais en production.
 */
@Component
@ConditionalOnProperty(name = "jamii.email.fournisseur", havingValue = "simule", matchIfMissing = true)
class EnvoiEmailSimule implements EnvoiEmail {

	private static final Logger journal = LoggerFactory.getLogger(EnvoiEmailSimule.class);

	private final boolean afficher;

	EnvoiEmailSimule(@Value("${jamii.email.afficher-lien:false}") boolean afficher) {
		this.afficher = afficher;
	}

	@Override
	public void envoyer(String destinataire, String objet, String texte) {
		if (afficher) {
			journal.warn("E-MAIL SIMULÉ (local seulement) pour {} : {}\n{}", destinataire, objet, texte);
		}
	}

}
