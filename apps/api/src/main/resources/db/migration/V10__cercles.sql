-- Les cercles de la Tech Tontine (contracts/cercles.yaml, docs/modele-donnees.md). Un tour n'a pas de table : c'est le
-- projet d'un membre, repéré par son rang.
CREATE TABLE cercle (
	id            UUID PRIMARY KEY,
	nom           VARCHAR(80) NOT NULL,
	ville         VARCHAR(80) NOT NULL,
	taille        SMALLINT NOT NULL CHECK (taille BETWEEN 4 AND 6),
	-- Pas de ON DELETE : supprimer un compte exige d'abord de transmettre ses cercles (DELETE /api/me).
	createur_id   UUID NOT NULL REFERENCES utilisateur (id),
	statut        VARCHAR(10) NOT NULL DEFAULT 'ouvert' CHECK (statut IN ('ouvert', 'en_cours', 'termine')),
	tour_en_cours SMALLINT CHECK (tour_en_cours BETWEEN 1 AND 6),
	tirage_le     TIMESTAMPTZ,
	cree_le       TIMESTAMPTZ NOT NULL,
	version       INTEGER NOT NULL DEFAULT 0,
	-- L'ordre est tiré si et seulement si le cercle n'est plus ouvert.
	CONSTRAINT cercle_tirage_coherent CHECK ((statut = 'ouvert') = (tirage_le IS NULL)),
	CONSTRAINT cercle_tour_coherent CHECK ((statut = 'en_cours') = (tour_en_cours IS NOT NULL))
);

CREATE INDEX cercle_statut_cree_le ON cercle (statut, cree_le DESC, id DESC);
CREATE INDEX cercle_createur_cree_le ON cercle (createur_id, cree_le);

CREATE TABLE cercle_membre (
	cercle_id        UUID NOT NULL REFERENCES cercle (id) ON DELETE CASCADE,
	utilisateur_id   UUID NOT NULL REFERENCES utilisateur (id) ON DELETE CASCADE,
	rejoint_le       TIMESTAMPTZ NOT NULL,
	idee_titre       VARCHAR(120),
	idee_description TEXT,
	-- Ordre de passage, de 1 à la taille du cercle ; NULL avant le tirage.
	rang             SMALLINT CHECK (rang BETWEEN 1 AND 6),
	-- Le projet de son tour (module projets) : pas de clé étrangère entre deux modules.
	projet_id        UUID,
	livre_le         TIMESTAMPTZ,
	depot_fournisseur VARCHAR(10) CHECK (depot_fournisseur IN ('gitea', 'github')),
	depot_url        VARCHAR(2048),
	depot_clone_url  VARCHAR(2048),
	PRIMARY KEY (cercle_id, utilisateur_id),
	-- L'idée est complète ou absente.
	CONSTRAINT cercle_membre_idee_complete CHECK ((idee_titre IS NULL) = (idee_description IS NULL)),
	-- Le dépôt est complet ou absent.
	CONSTRAINT cercle_membre_depot_complet CHECK (
		(depot_fournisseur IS NULL) = (depot_url IS NULL) AND (depot_url IS NULL) = (depot_clone_url IS NULL))
);

-- Deux membres ne partagent jamais le même rang.
CREATE UNIQUE INDEX cercle_membre_rang ON cercle_membre (cercle_id, rang) WHERE rang IS NOT NULL;
CREATE INDEX cercle_membre_utilisateur ON cercle_membre (utilisateur_id);
