-- Missions freelance (contracts/missions.yaml, #176) : l'annonce d'une mission et ses invitations.
-- Les propositions, devis, jalons et avis viennent avec #177 à #180.

CREATE TABLE mission (
	id              UUID PRIMARY KEY,
	-- Pour l'instant, supprimer le compte supprime ses missions. #180 les fermera plutôt (les propositions des autres comptent).
	client_id       UUID NOT NULL REFERENCES utilisateur (id) ON DELETE CASCADE,
	-- La demande publiée (module demandes) dont part la mission. Pas de clé étrangère : un module ne lit pas les tables d'un autre.
	demande_id      UUID,
	titre           VARCHAR(120) NOT NULL,
	besoin          TEXT NOT NULL,
	competences     TEXT[] NOT NULL,
	-- Unité mineure de la devise (en francs pour XOF et XAF) : un entier, jamais un nombre à virgule.
	budget_centimes BIGINT CHECK (budget_centimes >= 0),
	devise          CHAR(3) CHECK (devise ~ '^[A-Z]{3}$'),
	date_souhaitee  DATE,
	visibilite      VARCHAR(12) NOT NULL CHECK (visibilite IN ('publique', 'invitation')),
	ville           VARCHAR(80),
	pays            CHAR(2) CHECK (pays ~ '^[A-Z]{2}$'),
	a_distance      BOOLEAN NOT NULL DEFAULT TRUE,
	statut          VARCHAR(12) NOT NULL DEFAULT 'brouillon'
		CHECK (statut IN ('brouillon', 'publiee', 'fermee', 'attribuee', 'en_cours', 'terminee', 'annulee', 'desaccord')),
	version         INTEGER NOT NULL DEFAULT 1,
	cree_le         TIMESTAMPTZ NOT NULL,
	publiee_le      TIMESTAMPTZ,
	-- Un budget a toujours sa devise, et une mission publiée a toujours sa date de publication.
	CHECK ((budget_centimes IS NULL) = (devise IS NULL)),
	CHECK ((publiee_le IS NULL) = (statut = 'brouillon'))
);

-- La liste publique : les missions ouvertes, la plus récente d'abord.
CREATE INDEX mission_publiques ON mission (publiee_le DESC, id DESC) WHERE statut = 'publiee' AND visibilite = 'publique';
-- « Mes missions » et la limite de publications par jour.
CREATE INDEX mission_client ON mission (client_id, publiee_le DESC);

CREATE TABLE invitation_mission (
	mission_id     UUID NOT NULL REFERENCES mission (id) ON DELETE CASCADE,
	utilisateur_id UUID NOT NULL REFERENCES utilisateur (id) ON DELETE CASCADE,
	cree_le        TIMESTAMPTZ NOT NULL,
	PRIMARY KEY (mission_id, utilisateur_id)
);

CREATE INDEX invitation_mission_utilisateur ON invitation_mission (utilisateur_id);
