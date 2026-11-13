package dev.jamii.api.reseau;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Component;

import dev.jamii.api.auth.DonneesPersonnelles;

/**
 * Les messages dans l'export du compte (GET /api/me/export). À la suppression du compte, la clé étrangère en cascade
 * efface ses messages, envoyés et reçus : rien n'est transmis.
 */
@Component
class DonneesReseau implements DonneesPersonnelles {

	/** Seulement l'identifiant de l'autre personne : jamais ses données. */
	record MessageExporte(UUID id, boolean envoye, UUID autre, String texte, UUID cercleId, String envoyeLe, String luLe) {
	}

	private final Messagerie messagerie;

	DonneesReseau(Messagerie messagerie) {
		this.messagerie = messagerie;
	}

	@Override
	public String section() {
		return "messages";
	}

	@Override
	public Object exporter(UUID utilisateurId) {
		List<MessageExporte> messages = messagerie.tous(utilisateurId).stream().map(m -> {
			boolean envoye = m.expediteurId().equals(utilisateurId);
			return new MessageExporte(m.id(), envoye, envoye ? m.destinataireId() : m.expediteurId(), m.texte(),
					m.cercleId(), m.envoyeLe().toString(), m.luLe() == null ? null : m.luLe().toString());
		}).toList();
		return messages;
	}

}
