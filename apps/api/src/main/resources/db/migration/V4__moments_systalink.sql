-- Module systalink (#18). Un « moment Systalink » : un service Systalink proposé à quelqu'un, puis peut-être suivi.
-- Aucun identifiant d'utilisateur, par conception : le tableau de bord ne montre que des comptes agrégés.
CREATE TABLE moment_systalink (
	id          UUID PRIMARY KEY,
	service     VARCHAR(20) NOT NULL
	            CHECK (service IN ('datacloud', 'orion', 'yokk', 'systatalk', 'systaforms', 'domaines')),
	contexte    VARCHAR(20) NOT NULL CHECK (contexte IN ('projet', 'demande', 'communaute')),
	contexte_id UUID NOT NULL,
	propose_le  TIMESTAMPTZ NOT NULL,
	-- Posé au premier clic seulement : un second clic ne le change pas (taux impossible à gonfler).
	accepte_le  TIMESTAMPTZ CHECK (accepte_le IS NULL OR accepte_le >= propose_le)
);

-- Le tableau de bord compte les moments proposés depuis une date.
CREATE INDEX moment_systalink_propose_le ON moment_systalink (propose_le);
