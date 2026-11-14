-- Hackathons (contracts/hackathons.yaml, #169) : créer et publier. Les inscriptions, rendus, jury et résultats viennent
-- avec #171 à #174.

CREATE TABLE hackathon (
	id                       UUID PRIMARY KEY,
	-- Pour l'instant, supprimer le compte supprime ses hackathons (reprise de la suppression de compte : #181).
	organisateur_id          UUID NOT NULL REFERENCES utilisateur (id) ON DELETE CASCADE,
	titre                    VARCHAR(120) NOT NULL,
	theme                    VARCHAR(120) NOT NULL,
	description              TEXT NOT NULL,
	lieu                     VARCHAR(10) NOT NULL CHECK (lieu IN ('en_ligne', 'sur_place', 'hybride')),
	ville                    VARCHAR(80),
	inscriptions_ouvertes_le TIMESTAMPTZ NOT NULL,
	inscriptions_fermees_le  TIMESTAMPTZ NOT NULL,
	debut_le                 TIMESTAMPTZ NOT NULL,
	fin_rendus_le            TIMESTAMPTZ NOT NULL,
	resultats_le             TIMESTAMPTZ NOT NULL,
	taille_min               INTEGER NOT NULL CHECK (taille_min BETWEEN 1 AND 10),
	taille_max               INTEGER NOT NULL CHECK (taille_max BETWEEN 1 AND 10),
	mode_inscription         VARCHAR(12) NOT NULL CHECK (mode_inscription IN ('ouvert', 'candidature')),
	deploiement_obligatoire  BOOLEAN NOT NULL DEFAULT FALSE,
	code_de_conduite         TEXT,
	questions                TEXT[] NOT NULL DEFAULT '{}',
	-- Seuls ces états sont enregistrés : `en_cours` et `evaluation` se calculent du calendrier, à la lecture.
	statut_base              VARCHAR(10) NOT NULL DEFAULT 'brouillon' CHECK (statut_base IN ('brouillon', 'publie', 'annule', 'resultats')),
	annulation_raison        TEXT,
	version                  INTEGER NOT NULL DEFAULT 1,
	cree_le                  TIMESTAMPTZ NOT NULL,
	publie_le                TIMESTAMPTZ,
	-- Le calendrier est dans l'ordre, et une équipe a une taille possible.
	CHECK (inscriptions_ouvertes_le < inscriptions_fermees_le AND inscriptions_fermees_le <= debut_le
		AND debut_le < fin_rendus_le AND fin_rendus_le < resultats_le),
	CHECK (taille_min <= taille_max),
	CHECK ((publie_le IS NULL) = (statut_base = 'brouillon'))
);

-- La liste publique : par date de début. « Mes hackathons » et la limite de publications par jour : par organisateur.
CREATE INDEX hackathon_debut ON hackathon (debut_le, id) WHERE statut_base IN ('publie', 'resultats');
CREATE INDEX hackathon_organisateur ON hackathon (organisateur_id, publie_le DESC);

CREATE TABLE critere (
	id           UUID PRIMARY KEY,
	hackathon_id UUID NOT NULL REFERENCES hackathon (id) ON DELETE CASCADE,
	ordre        INTEGER NOT NULL CHECK (ordre >= 1),
	nom          VARCHAR(60) NOT NULL,
	poids        INTEGER NOT NULL CHECK (poids BETWEEN 1 AND 100),
	UNIQUE (hackathon_id, ordre)
);

CREATE TABLE prix (
	id               UUID PRIMARY KEY,
	hackathon_id     UUID NOT NULL REFERENCES hackathon (id) ON DELETE CASCADE,
	ordre            INTEGER NOT NULL CHECK (ordre >= 1),
	nom              VARCHAR(80) NOT NULL,
	-- Entier d'unité mineure et devise ISO 4217, ou rien (un prix sans argent).
	montant_centimes BIGINT CHECK (montant_centimes >= 0),
	devise           CHAR(3) CHECK (devise ~ '^[A-Z]{3}$'),
	sponsor          VARCHAR(80),
	UNIQUE (hackathon_id, ordre),
	CHECK ((montant_centimes IS NULL) = (devise IS NULL))
);
