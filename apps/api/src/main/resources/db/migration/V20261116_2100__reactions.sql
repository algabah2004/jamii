-- Réagir à un message avec un emoji (#157). Une ligne par personne et par emoji ; 10 emojis différents au plus par
-- personne et par message (vérifié par l'API). Effacée avec le message ou avec le compte.
CREATE TABLE reaction_message (
	message_id     UUID        NOT NULL REFERENCES message (id) ON DELETE CASCADE,
	utilisateur_id UUID        NOT NULL REFERENCES utilisateur (id) ON DELETE CASCADE,
	emoji          VARCHAR(32) NOT NULL CHECK (length(emoji) BETWEEN 1 AND 32),
	cree_le        TIMESTAMPTZ NOT NULL,
	PRIMARY KEY (message_id, utilisateur_id, emoji)
);
