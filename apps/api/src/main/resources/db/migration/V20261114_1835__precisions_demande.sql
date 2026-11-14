-- Les précisions qu'une personne ajoute à son besoin après avoir lu la réponse (contracts/demandes.yaml).
-- Chacune relance l'analyse avec le besoin de départ ET toutes les précisions. Supprimées avec la demande.
CREATE TABLE demande_precision (
	demande_id UUID NOT NULL REFERENCES demande (id) ON DELETE CASCADE,
	rang       INTEGER NOT NULL CHECK (rang BETWEEN 1 AND 5),
	texte      VARCHAR(500) NOT NULL,
	cree_le    TIMESTAMPTZ NOT NULL,
	PRIMARY KEY (demande_id, rang)
);
