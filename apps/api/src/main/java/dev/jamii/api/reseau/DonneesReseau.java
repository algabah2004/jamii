package dev.jamii.api.reseau;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import dev.jamii.api.auth.DonneesPersonnelles;

/**
 * Les messages, les contacts et les notifications dans l'export du compte (GET /api/me/export). À la suppression du
 * compte, les clés étrangères en cascade les effacent : rien n'est transmis.
 */
@Component
class DonneesReseau implements DonneesPersonnelles {

	/** Seulement l'identifiant de l'autre personne : jamais ses données. */
	record MessageExporte(UUID id, boolean envoye, UUID autre, String texte, UUID cercleId, String envoyeLe, String luLe) {
	}

	record ContactExporte(UUID personne, String statut, String depuis) {
	}

	record Export(List<MessageExporte> messages, List<ContactExporte> contacts, List<Notifications.Ligne> notifications) {
	}

	private final Messagerie messagerie;

	private final Contacts contacts;

	private final Notifications notifications;

	DonneesReseau(Messagerie messagerie, Contacts contacts, Notifications notifications) {
		this.messagerie = messagerie;
		this.contacts = contacts;
		this.notifications = notifications;
	}

	@Override
	public String section() {
		return "reseau";
	}

	@Override
	public Object exporter(UUID utilisateurId) {
		List<MessageExporte> messages = messagerie.tous(utilisateurId).stream().map(m -> {
			boolean envoye = m.expediteurId().equals(utilisateurId);
			return new MessageExporte(m.id(), envoye, envoye ? m.destinataireId() : m.expediteurId(), m.texte(),
					m.cercleId(), m.envoyeLe().toString(), m.luLe() == null ? null : m.luLe().toString());
		}).toList();
		List<ContactExporte> liens = new java.util.ArrayList<>();
		for (String statut : List.of("accepte", "recue", "envoyee")) {
			contacts.lister(utilisateurId, statut, 10_000)
				.forEach(c -> liens.add(new ContactExporte(c.autre(), c.statut(), c.depuis().toString())));
		}
		return new Export(messages, liens, notifications.toutes(utilisateurId));
	}

}
