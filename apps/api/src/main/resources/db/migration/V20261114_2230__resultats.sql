-- Les résultats d'un hackathon (contracts/hackathons.yaml, `confirmerResultats`, #174) : un gagnant par prix, confirmé
-- par l'organisateur. S'il diffère du classement proposé, la raison est obligatoire et gardée.

ALTER TABLE hackathon ADD COLUMN resultats_publies_le TIMESTAMPTZ;

CREATE TABLE gagnant (
	hackathon_id      UUID NOT NULL REFERENCES hackathon (id) ON DELETE CASCADE,
	prix_id           UUID NOT NULL REFERENCES prix (id) ON DELETE CASCADE,
	equipe_id         UUID NOT NULL REFERENCES equipe (id) ON DELETE CASCADE,
	raison_ajustement VARCHAR(1000),
	PRIMARY KEY (hackathon_id, prix_id)
);
