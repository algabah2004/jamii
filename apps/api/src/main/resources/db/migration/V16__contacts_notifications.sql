-- Module reseau (bloc #19). Contrat : contracts/reseau.yaml. Les contacts et les notifications.

-- Une ligne par paire de personnes : la demande, puis l'acceptation. Refuser ou retirer, c'est supprimer la ligne.
CREATE TABLE contact (
	demandeur_id    UUID NOT NULL REFERENCES utilisateur (id) ON DELETE CASCADE,
	destinataire_id UUID NOT NULL REFERENCES utilisateur (id) ON DELETE CASCADE,
	accepte         BOOLEAN NOT NULL DEFAULT FALSE,
	demande_le      TIMESTAMPTZ NOT NULL,
	accepte_le      TIMESTAMPTZ,
	PRIMARY KEY (demandeur_id, destinataire_id),
	CONSTRAINT contact_pas_soi_meme CHECK (demandeur_id <> destinataire_id),
	CONSTRAINT contact_acceptation_datee CHECK (accepte = (accepte_le IS NOT NULL))
);
-- Une seule ligne par paire, dans un sens ou dans l'autre : deux demandes croisées ne créent pas deux liens.
CREATE UNIQUE INDEX contact_paire ON contact (LEAST(demandeur_id, destinataire_id), GREATEST(demandeur_id, destinataire_id));
CREATE INDEX contact_destinataire ON contact (destinataire_id, accepte);

-- Créées par l'API, jamais par le navigateur ; gardées 30 jours (elles ne sont plus lues au-delà).
CREATE TABLE notification (
	id             UUID PRIMARY KEY,
	utilisateur_id UUID NOT NULL REFERENCES utilisateur (id) ON DELETE CASCADE,
	type           VARCHAR(30) NOT NULL CHECK (type IN ('message_recu', 'invitation_cercle', 'demande_contact',
		'contact_accepte', 'membre_rejoint', 'tirage_effectue', 'tour_livre', 'cercle_recommande')),
	-- La personne à l'origine (supprimée avec son compte) et le cercle concerné (module cercles : pas de clé étrangère).
	personne_id    UUID REFERENCES utilisateur (id) ON DELETE CASCADE,
	cercle_id      UUID,
	lien           VARCHAR(200) NOT NULL CHECK (lien ~ '^/[a-z0-9/-]*$'),
	cree_le        TIMESTAMPTZ NOT NULL,
	lu_le          TIMESTAMPTZ
);
CREATE INDEX notification_utilisateur ON notification (utilisateur_id, cree_le DESC, id DESC);
CREATE INDEX notification_non_lues ON notification (utilisateur_id) WHERE lu_le IS NULL;
