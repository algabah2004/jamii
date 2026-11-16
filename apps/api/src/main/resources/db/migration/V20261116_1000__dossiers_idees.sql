-- Un cahier des charges réservé au cercle et un backlog préparé avant la création du projet du tour.
ALTER TABLE fichier ADD COLUMN cercle_id UUID REFERENCES cercle (id) ON DELETE CASCADE;
CREATE INDEX fichier_cercle ON fichier (cercle_id) WHERE cercle_id IS NOT NULL;
ALTER TABLE fichier ADD CONSTRAINT fichier_une_destination CHECK (conversation_avec IS NULL OR cercle_id IS NULL);
ALTER TABLE cercle_membre ADD COLUMN cahier_charges_id UUID REFERENCES fichier (id) ON DELETE SET NULL;
CREATE TABLE cercle_backlog (
 cercle_id UUID NOT NULL,
 utilisateur_id UUID NOT NULL,
 position INTEGER NOT NULL CHECK (position BETWEEN 0 AND 99),
 titre VARCHAR(160) NOT NULL CHECK (length(btrim(titre)) BETWEEN 3 AND 160),
 description VARCHAR(2000),
 priorite VARCHAR(8) NOT NULL DEFAULT 'normale' CHECK (priorite IN ('haute', 'normale', 'basse')),
 echeance DATE,
 PRIMARY KEY (cercle_id, utilisateur_id, position),
 FOREIGN KEY (cercle_id, utilisateur_id) REFERENCES cercle_membre (cercle_id, utilisateur_id) ON DELETE CASCADE
);
