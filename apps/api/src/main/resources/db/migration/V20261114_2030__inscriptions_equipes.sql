-- Inscriptions et équipes d'un hackathon (contracts/hackathons.yaml, #171).
-- Une personne s'inscrit une seule fois par hackathon ; elle est seule ou dans une équipe.

CREATE TABLE equipe (
	id           UUID PRIMARY KEY,
	hackathon_id UUID NOT NULL REFERENCES hackathon (id) ON DELETE CASCADE,
	nom          VARCHAR(60) NOT NULL,
	-- Supprimer le compte du chef supprime son équipe ; ses membres redeviennent « sans équipe » (equipe_id à NULL).
	chef_id      UUID NOT NULL REFERENCES utilisateur (id) ON DELETE CASCADE,
	cree_le      TIMESTAMPTZ NOT NULL
);

CREATE INDEX equipe_hackathon ON equipe (hackathon_id, cree_le, id);

CREATE TABLE inscription (
	id             UUID PRIMARY KEY,
	hackathon_id   UUID NOT NULL REFERENCES hackathon (id) ON DELETE CASCADE,
	utilisateur_id UUID NOT NULL REFERENCES utilisateur (id) ON DELETE CASCADE,
	equipe_id      UUID REFERENCES equipe (id) ON DELETE SET NULL,
	statut         VARCHAR(10) NOT NULL CHECK (statut IN ('en_attente', 'acceptee', 'refusee', 'attente')),
	cherche_equipe BOOLEAN NOT NULL DEFAULT TRUE,
	competences    TEXT[] NOT NULL DEFAULT '{}',
	cree_le        TIMESTAMPTZ NOT NULL,
	-- Une inscription par personne et par hackathon : la contrainte départage deux requêtes simultanées (`deja_inscrit`).
	UNIQUE (hackathon_id, utilisateur_id)
);

CREATE INDEX inscription_hackathon ON inscription (hackathon_id, cree_le, id);
CREATE INDEX inscription_equipe ON inscription (equipe_id) WHERE equipe_id IS NOT NULL;
-- « Je cherche une équipe » : les participants acceptés, seuls et volontaires.
CREATE INDEX inscription_sans_equipe ON inscription (hackathon_id, cree_le, id) WHERE equipe_id IS NULL AND cherche_equipe AND statut = 'acceptee';
