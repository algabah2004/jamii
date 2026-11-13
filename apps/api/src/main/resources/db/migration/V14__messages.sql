-- Module reseau (bloc #19). Contrat : contracts/reseau.yaml. Les messages entre deux personnes.
-- Une conversation n'a pas de table : c'est la paire (expéditeur, destinataire), dans un sens ou dans l'autre.
CREATE TABLE message (
	id              UUID PRIMARY KEY,
	expediteur_id   UUID NOT NULL REFERENCES utilisateur (id) ON DELETE CASCADE,
	destinataire_id UUID NOT NULL REFERENCES utilisateur (id) ON DELETE CASCADE,
	texte           VARCHAR(2000) NOT NULL CHECK (length(btrim(texte)) > 0),
	-- L'invitation à un cercle (module cercles) : pas de clé étrangère entre deux modules.
	cercle_id       UUID,
	envoye_le       TIMESTAMPTZ NOT NULL,
	lu_le           TIMESTAMPTZ,
	CONSTRAINT message_pas_a_soi_meme CHECK (expediteur_id <> destinataire_id)
);

-- Lire une conversation, dans un sens comme dans l'autre, la plus récente d'abord.
CREATE INDEX message_paire ON message (LEAST(expediteur_id, destinataire_id), GREATEST(expediteur_id, destinataire_id),
	envoye_le DESC, id DESC);
-- La pastille du menu : les messages reçus pas encore lus.
CREATE INDEX message_non_lus ON message (destinataire_id) WHERE lu_le IS NULL;
-- Les limites anti-spam : ce que j'ai envoyé dans la dernière heure, et à qui pour la première fois aujourd'hui.
CREATE INDEX message_envoyes ON message (expediteur_id, envoye_le DESC);
