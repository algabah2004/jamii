-- Les jalons et les tâches d'un projet (contracts/projets.yaml, tag « equipe » ; docs/modele-donnees.md). Réservés à
-- l'équipe. Un tour de cercle étant un projet, ce sont aussi les tâches du tour (#126).
CREATE TABLE jalon (
	id         UUID PRIMARY KEY,
	projet_id  UUID NOT NULL REFERENCES projet (id) ON DELETE CASCADE,
	titre      VARCHAR(120) NOT NULL,
	echeance   DATE,
	statut     VARCHAR(10) NOT NULL DEFAULT 'prevu' CHECK (statut IN ('prevu', 'atteint')),
	ordre      INTEGER NOT NULL CHECK (ordre >= 0),
	cree_le    TIMESTAMPTZ NOT NULL,
	version    INTEGER NOT NULL DEFAULT 0,
	-- Pour qu'une tâche ne puisse viser qu'un jalon de son propre projet (clé étrangère composée ci-dessous).
	UNIQUE (id, projet_id)
);

CREATE INDEX jalon_projet_ordre ON jalon (projet_id, ordre, cree_le);

CREATE TABLE tache (
	id              UUID PRIMARY KEY,
	projet_id       UUID NOT NULL REFERENCES projet (id) ON DELETE CASCADE,
	jalon_id        UUID,
	titre           VARCHAR(200) NOT NULL,
	-- Un compte supprimé laisse la tâche, sans personne assignée.
	assignee_id     UUID REFERENCES utilisateur (id) ON DELETE SET NULL,
	statut          VARCHAR(10) NOT NULL DEFAULT 'a_faire' CHECK (statut IN ('a_faire', 'en_cours', 'livree')),
	echeance        DATE,
	livree_le       TIMESTAMPTZ,
	proposee_par_ia BOOLEAN NOT NULL DEFAULT FALSE,
	cree_le         TIMESTAMPTZ NOT NULL,
	version         INTEGER NOT NULL DEFAULT 0,
	-- Supprimer un jalon détache ses tâches sans toucher à leur projet (PostgreSQL 15 et plus).
	FOREIGN KEY (jalon_id, projet_id) REFERENCES jalon (id, projet_id) ON DELETE SET NULL (jalon_id),
	-- Une tâche est livrée si et seulement si elle a une date de livraison.
	CONSTRAINT tache_livraison_coherente CHECK ((statut = 'livree') = (livree_le IS NOT NULL))
);

-- La liste d'un projet, page par page, dans l'ordre de création ; et « mes tâches ».
CREATE INDEX tache_projet_cree_le ON tache (projet_id, cree_le, id);
CREATE INDEX tache_assignee ON tache (assignee_id) WHERE assignee_id IS NOT NULL;
