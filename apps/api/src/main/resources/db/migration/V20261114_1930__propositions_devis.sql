-- Propositions et devis d'une mission (contracts/missions.yaml, #177).
-- Un développeur propose (une fois par mission), le client en retient une, le développeur retenu envoie un devis
-- découpé en jalons, et le devis accepté par le client vaut contrat : la mission est attribuée.

CREATE TABLE proposition (
	id             UUID PRIMARY KEY,
	mission_id     UUID NOT NULL REFERENCES mission (id) ON DELETE CASCADE,
	-- Pour l'instant, supprimer le compte supprime ses propositions (la suppression d'un compte est reprise dans #180).
	developpeur_id UUID NOT NULL REFERENCES utilisateur (id) ON DELETE CASCADE,
	message        TEXT NOT NULL,
	prix_centimes  BIGINT NOT NULL CHECK (prix_centimes >= 0),
	devise         CHAR(3) NOT NULL CHECK (devise ~ '^[A-Z]{3}$'),
	delai_jours    INTEGER NOT NULL CHECK (delai_jours BETWEEN 1 AND 365),
	reponses       TEXT[] NOT NULL DEFAULT '{}',
	statut         VARCHAR(12) NOT NULL DEFAULT 'envoyee'
		CHECK (statut IN ('envoyee', 'retenue', 'acceptee', 'non_retenue', 'retiree')),
	cree_le        TIMESTAMPTZ NOT NULL,
	-- Une proposition par développeur et par mission : la contrainte départage deux envois simultanés.
	UNIQUE (mission_id, developpeur_id)
);

-- La liste d'un client (par mission), et la limite de 20 propositions par jour et par développeur.
CREATE INDEX proposition_mission ON proposition (mission_id, cree_le DESC, id DESC);
CREATE INDEX proposition_developpeur ON proposition (developpeur_id, cree_le DESC);
-- Au plus une proposition retenue à la fois par mission.
CREATE UNIQUE INDEX proposition_une_retenue ON proposition (mission_id) WHERE statut = 'retenue';

CREATE TABLE devis (
	id                  UUID PRIMARY KEY,
	mission_id          UUID NOT NULL REFERENCES mission (id) ON DELETE CASCADE,
	proposition_id      UUID NOT NULL REFERENCES proposition (id) ON DELETE CASCADE,
	total_centimes      BIGINT NOT NULL CHECK (total_centimes >= 0),
	devise              CHAR(3) NOT NULL CHECK (devise ~ '^[A-Z]{3}$'),
	retouches_comprises INTEGER NOT NULL CHECK (retouches_comprises BETWEEN 0 AND 10),
	conditions          TEXT,
	cree_le             TIMESTAMPTZ NOT NULL,
	accepte_le          TIMESTAMPTZ
);

CREATE INDEX devis_mission ON devis (mission_id, cree_le DESC);

-- Les étapes du devis. Leur suivi (livraison, acceptation, paiement) vient avec #178.
CREATE TABLE jalon_mission (
	id             UUID PRIMARY KEY,
	devis_id       UUID NOT NULL REFERENCES devis (id) ON DELETE CASCADE,
	ordre          INTEGER NOT NULL CHECK (ordre >= 1),
	titre          VARCHAR(120) NOT NULL,
	description    TEXT,
	montant_centimes BIGINT NOT NULL CHECK (montant_centimes >= 0),
	delai_jours    INTEGER NOT NULL CHECK (delai_jours BETWEEN 1 AND 365),
	statut         VARCHAR(10) NOT NULL DEFAULT 'a_faire' CHECK (statut IN ('a_faire', 'en_cours', 'livre', 'accepte')),
	UNIQUE (devis_id, ordre)
);

-- Le développeur retenu, une fois le devis accepté.
ALTER TABLE mission ADD COLUMN developpeur_id UUID REFERENCES utilisateur (id) ON DELETE SET NULL;
