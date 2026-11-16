-- Répondre, modifier, supprimer et réagir dans le salon d'un cercle (#157).
--
-- `version` : avance à chaque changement d'un message (écrit, modifié, supprimé, réaction, coup de main résolu). Le
-- front relit `apres` = la plus grande version qu'il connaît : il reçoit les nouveaux messages ET ceux qui ont changé.
-- La séquence démarre au-dessus des numéros existants : un ancien front qui envoie encore un numéro reste correct
-- (il reçoit au pire quelques messages en double, fusionnés par identifiant).
CREATE SEQUENCE message_cercle_version;
SELECT setval('message_cercle_version', coalesce((SELECT max(numero) FROM message_cercle), 0) + 1);
ALTER TABLE message_cercle ADD COLUMN version BIGINT;
UPDATE message_cercle SET version = numero;
ALTER TABLE message_cercle ALTER COLUMN version SET NOT NULL;
ALTER TABLE message_cercle ALTER COLUMN version SET DEFAULT nextval('message_cercle_version');
CREATE INDEX message_cercle_versions ON message_cercle (cercle_id, version);

ALTER TABLE message_cercle ADD COLUMN reponse_a UUID REFERENCES message_cercle (id) ON DELETE SET NULL;
ALTER TABLE message_cercle ADD COLUMN modifie_le TIMESTAMPTZ;
ALTER TABLE message_cercle ADD COLUMN supprime_le TIMESTAMPTZ;

-- Un message supprimé garde sa place (« Ce message a été supprimé ») mais perd son texte et ses fichiers.
ALTER TABLE message_cercle DROP CONSTRAINT message_cercle_texte_ou_pieces;
ALTER TABLE message_cercle ADD CONSTRAINT message_cercle_texte_ou_pieces
	CHECK (supprime_le IS NOT NULL OR type <> 'texte' OR length(btrim(coalesce(texte, ''))) > 0 OR cardinality(pieces) > 0);

CREATE TABLE reaction_message_cercle (
	message_id     UUID        NOT NULL REFERENCES message_cercle (id) ON DELETE CASCADE,
	utilisateur_id UUID        NOT NULL REFERENCES utilisateur (id) ON DELETE CASCADE,
	emoji          VARCHAR(32) NOT NULL CHECK (length(emoji) BETWEEN 1 AND 32),
	cree_le        TIMESTAMPTZ NOT NULL,
	PRIMARY KEY (message_id, utilisateur_id, emoji)
);
