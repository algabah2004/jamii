-- Annuler une mission et trancher un désaccord (contracts/missions.yaml, tag « fin », #179).

-- Une demande d'annulation en attente de l'autre partie : `client` ou `developpeur`.
ALTER TABLE mission
	ADD COLUMN annulation_demandee_par VARCHAR(12) CHECK (annulation_demandee_par IN ('client', 'developpeur')),
	ADD COLUMN annulation_raison       TEXT;

-- Au plus un désaccord par mission. Tant qu'il n'est pas tranché, livraisons, retouches et acceptations automatiques
-- sont gelées (la mission est en statut `desaccord`).
CREATE TABLE desaccord (
	mission_id  UUID PRIMARY KEY REFERENCES mission (id) ON DELETE CASCADE,
	ouvert_par  VARCHAR(12) NOT NULL CHECK (ouvert_par IN ('client', 'developpeur')),
	raison      TEXT NOT NULL,
	ouvert_le   TIMESTAMPTZ NOT NULL,
	issue       VARCHAR(10) CHECK (issue IN ('terminee', 'annulee')),
	explication TEXT,
	-- Le modérateur qui a tranché ; son compte supprimé, la décision reste.
	tranche_par UUID REFERENCES utilisateur (id) ON DELETE SET NULL,
	tranche_le  TIMESTAMPTZ
);
