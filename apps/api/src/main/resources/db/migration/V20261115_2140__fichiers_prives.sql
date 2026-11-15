-- Les fichiers des messages privés restent réservés à leur auteur et à leur destinataire après envoi.
ALTER TABLE fichier ADD COLUMN conversation_avec UUID REFERENCES utilisateur (id) ON DELETE CASCADE;
CREATE INDEX fichier_conversation ON fichier (conversation_avec) WHERE conversation_avec IS NOT NULL;
ALTER TABLE message ADD CONSTRAINT message_pieces_limite CHECK (cardinality(pieces) <= 6);
