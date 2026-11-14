-- Le suivi d'une mission attribuée (contracts/missions.yaml, tag « suivi », #178) : démarrer, livrer chaque jalon,
-- l'accepter (ou l'accepter tout seul 72 h après la livraison), demander une retouche, déclarer un paiement.

ALTER TABLE mission
	ADD COLUMN informations_depart TEXT,
	ADD COLUMN demarre_le          TIMESTAMPTZ,
	-- Les retouches du devis valent pour toute la mission.
	ADD COLUMN retouches_utilisees INTEGER NOT NULL DEFAULT 0 CHECK (retouches_utilisees >= 0);

ALTER TABLE jalon_mission
	ADD COLUMN livre_le                TIMESTAMPTZ,
	ADD COLUMN accepte_le              TIMESTAMPTZ,
	ADD COLUMN acceptation_automatique BOOLEAN NOT NULL DEFAULT FALSE,
	-- Le paiement n'est pas encaissé par Jamii (v1) : chacun le déclare, le client d'abord.
	ADD COLUMN paiement                VARCHAR(12) NOT NULL DEFAULT 'non_paye'
		CHECK (paiement IN ('non_paye', 'declare_paye', 'declare_recu'));

-- La tâche d'acceptation automatique ne lit que les jalons livrés.
CREATE INDEX jalon_mission_livre ON jalon_mission (livre_le) WHERE statut = 'livre';

-- Chaque livraison d'un jalon, et la retouche demandée après elle s'il y en a une.
CREATE TABLE livraison (
	id        UUID PRIMARY KEY,
	jalon_id  UUID NOT NULL REFERENCES jalon_mission (id) ON DELETE CASCADE,
	message   TEXT NOT NULL,
	liens     TEXT[] NOT NULL DEFAULT '{}',
	livree_le TIMESTAMPTZ NOT NULL,
	retouche  TEXT
);

CREATE INDEX livraison_jalon ON livraison (jalon_id, livree_le);
