-- Les rôles recherchés par un projet et les candidatures qu'ils reçoivent (contracts/projets.yaml, docs/modele-donnees.md).
CREATE TABLE role_recherche (
	id          UUID PRIMARY KEY,
	projet_id   UUID NOT NULL REFERENCES projet (id) ON DELETE CASCADE,
	titre       VARCHAR(80) NOT NULL,
	-- Compétences normalisées (minuscules, sans espace) : flutter, postgresql, ux-design.
	competences TEXT[] NOT NULL CHECK (cardinality(competences) BETWEEN 1 AND 10),
	statut      VARCHAR(10) NOT NULL DEFAULT 'ouvert' CHECK (statut IN ('ouvert', 'pourvu', 'ferme')),
	cree_le     TIMESTAMPTZ NOT NULL
);

CREATE INDEX role_recherche_projet ON role_recherche (projet_id, cree_le);
-- Trouver les projets qui recrutent une compétence.
CREATE INDEX role_recherche_competences ON role_recherche USING GIN (competences);

CREATE TABLE candidature (
	id                UUID PRIMARY KEY,
	role_recherche_id UUID NOT NULL REFERENCES role_recherche (id) ON DELETE CASCADE,
	-- Le message est une donnée personnelle du candidat : il disparaît avec son compte.
	candidat_id       UUID NOT NULL REFERENCES utilisateur (id) ON DELETE CASCADE,
	message           TEXT NOT NULL,
	statut            VARCHAR(10) NOT NULL DEFAULT 'envoyee' CHECK (statut IN ('envoyee', 'acceptee', 'refusee', 'retiree')),
	cree_le           TIMESTAMPTZ NOT NULL,
	decidee_le        TIMESTAMPTZ,
	CONSTRAINT candidature_decision_coherente CHECK ((statut = 'envoyee') = (decidee_le IS NULL))
);

-- Une seule candidature en attente par personne et par rôle.
CREATE UNIQUE INDEX candidature_active ON candidature (role_recherche_id, candidat_id) WHERE statut = 'envoyee';
CREATE INDEX candidature_candidat ON candidature (candidat_id, cree_le DESC, id DESC);
CREATE INDEX candidature_role ON candidature (role_recherche_id, statut);

-- Le rôle qu'un membre occupe dans l'équipe (NULL pour le porteur et pour ceux arrivés sans candidature).
ALTER TABLE membre_equipe ADD COLUMN role_recherche_id UUID REFERENCES role_recherche (id) ON DELETE SET NULL;
