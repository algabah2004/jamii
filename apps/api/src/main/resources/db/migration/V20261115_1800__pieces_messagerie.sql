-- Les pièces jointes des messages privés (contracts/reseau.yaml, fichiers.yaml) : comme dans les salons, un message
-- peut n'être qu'une image ou un fichier.
ALTER TABLE message ADD COLUMN pieces UUID[] NOT NULL DEFAULT '{}';
ALTER TABLE message DROP CONSTRAINT message_texte_check;
ALTER TABLE message ADD CONSTRAINT message_texte_ou_pieces CHECK (length(btrim(texte)) > 0 OR cardinality(pieces) > 0);
