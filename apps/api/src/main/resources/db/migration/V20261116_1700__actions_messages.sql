-- Répondre à un message, le modifier, le supprimer pour tout le monde, le transférer (#157).
ALTER TABLE message ADD COLUMN reponse_a UUID REFERENCES message (id) ON DELETE SET NULL;
ALTER TABLE message ADD COLUMN modifie_le TIMESTAMPTZ;
-- Un message supprimé garde sa place dans la conversation, sans son texte ni ses fichiers (effacés pour de bon).
ALTER TABLE message ADD COLUMN supprime_le TIMESTAMPTZ;
ALTER TABLE message ADD COLUMN transfere BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE message DROP CONSTRAINT message_texte_ou_pieces;
ALTER TABLE message ADD CONSTRAINT message_texte_ou_pieces
	CHECK (supprime_le IS NOT NULL OR length(btrim(texte)) > 0 OR cardinality(pieces) > 0);
