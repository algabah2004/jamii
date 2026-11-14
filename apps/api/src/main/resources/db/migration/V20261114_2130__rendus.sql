-- Le rendu d'une équipe de hackathon (contracts/hackathons.yaml, tag « rendus », #172). Un seul par équipe, modifiable
-- jusqu'à la fin des rendus. Le lien du produit est vérifié après l'enregistrement : `lien_repond` reste NULL tant
-- qu'il ne l'a pas été.

CREATE TABLE rendu (
	id              UUID PRIMARY KEY,
	hackathon_id    UUID NOT NULL REFERENCES hackathon (id) ON DELETE CASCADE,
	equipe_id       UUID NOT NULL UNIQUE REFERENCES equipe (id) ON DELETE CASCADE,
	titre           VARCHAR(120) NOT NULL,
	description     TEXT NOT NULL,
	lien_produit    VARCHAR(500) NOT NULL,
	depot           VARCHAR(500),
	video           VARCHAR(500),
	reponses        TEXT[] NOT NULL DEFAULT '{}',
	lien_repond     BOOLEAN,
	lien_verifie_le TIMESTAMPTZ,
	version         INTEGER NOT NULL DEFAULT 0,
	modifie_le      TIMESTAMPTZ NOT NULL
);

CREATE INDEX rendu_hackathon ON rendu (hackathon_id, modifie_le, id);
