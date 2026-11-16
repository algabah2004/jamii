-- Répondre, modifier, supprimer et réagir dans les salons d'une communauté (#155, #157). Même principe que le salon
-- d'un cercle (V20261116_2200) : `version` avance à chaque changement d'un message, et sert de curseur de relecture.
CREATE SEQUENCE message_salon_version;
SELECT setval('message_salon_version', coalesce((SELECT max(numero) FROM message_salon), 0) + 1);
ALTER TABLE message_salon ADD COLUMN version BIGINT;
UPDATE message_salon SET version = numero;
ALTER TABLE message_salon ALTER COLUMN version SET NOT NULL;
ALTER TABLE message_salon ALTER COLUMN version SET DEFAULT nextval('message_salon_version');
CREATE INDEX message_salon_versions ON message_salon (salon_id, version);

ALTER TABLE message_salon ADD COLUMN reponse_a UUID REFERENCES message_salon (id) ON DELETE SET NULL;
ALTER TABLE message_salon ADD COLUMN modifie_le TIMESTAMPTZ;
ALTER TABLE message_salon ADD COLUMN supprime_le TIMESTAMPTZ;
-- Qui l'a supprimé : son auteur, ou un modérateur de la communauté (« Supprimé par un modérateur »).
ALTER TABLE message_salon ADD COLUMN supprime_par UUID REFERENCES utilisateur (id) ON DELETE SET NULL;

-- Un message supprimé garde sa place mais perd son texte (vide) et ses fichiers.
ALTER TABLE message_salon DROP CONSTRAINT message_salon_texte_ou_pieces;
ALTER TABLE message_salon ADD CONSTRAINT message_salon_texte_ou_pieces
	CHECK (supprime_le IS NOT NULL OR length(btrim(texte)) > 0 OR cardinality(pieces) > 0);

CREATE TABLE reaction_message_salon (
	message_id     UUID        NOT NULL REFERENCES message_salon (id) ON DELETE CASCADE,
	utilisateur_id UUID        NOT NULL REFERENCES utilisateur (id) ON DELETE CASCADE,
	emoji          VARCHAR(32) NOT NULL CHECK (length(emoji) BETWEEN 1 AND 32),
	cree_le        TIMESTAMPTZ NOT NULL,
	PRIMARY KEY (message_id, utilisateur_id, emoji)
);
