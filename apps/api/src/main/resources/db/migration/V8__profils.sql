-- Module profils (bloc #19). Contrat : contracts/profils.yaml. Modèle : docs/modele-donnees.md §5.
-- Un profil par compte. Il reste privé (publie = FALSE) jusqu'à ce que son propriétaire le publie.
CREATE TABLE profil (
	utilisateur_id         UUID PRIMARY KEY REFERENCES utilisateur (id) ON DELETE CASCADE,
	bio                    VARCHAR(500),
	pays                   VARCHAR(2) CHECK (pays ~ '^[A-Z]{2}$'),
	ville                  VARCHAR(80),
	disponibilite          VARCHAR(20) NOT NULL DEFAULT 'disponible'
		CHECK (disponibilite IN ('disponible', 'occupe', 'indisponible')),
	heures_par_semaine     INTEGER CHECK (heures_par_semaine BETWEEN 1 AND 60),
	publie                 BOOLEAN NOT NULL DEFAULT FALSE,
	afficher_resume_ia     BOOLEAN NOT NULL DEFAULT TRUE,
	resume_ia              VARCHAR(600),
	generation_statut      VARCHAR(20) NOT NULL CHECK (generation_statut IN ('en_cours', 'terminee', 'echec')),
	generation_erreur      VARCHAR(30) CHECK (generation_erreur IN ('github_indisponible', 'github_refus')),
	generation_lancee_le   TIMESTAMPTZ,
	generation_terminee_le TIMESTAMPTZ,
	version                INTEGER NOT NULL DEFAULT 0,
	mis_a_jour_le          TIMESTAMPTZ NOT NULL
);

-- Recherche de coéquipiers : seulement les profils publiés, les disponibles d'abord.
CREATE INDEX profil_recherche ON profil (disponibilite, mis_a_jour_le DESC, utilisateur_id) WHERE publie;

-- Compétences déduites de GitHub ou déclarées. Texte libre normalisé (voir Competences.java).
CREATE TABLE competence_profil (
	utilisateur_id UUID NOT NULL REFERENCES profil (utilisateur_id) ON DELETE CASCADE,
	competence     VARCHAR(50) NOT NULL CHECK (competence ~ '^[a-z0-9][a-z0-9.+#-]*$'),
	source         VARCHAR(10) NOT NULL CHECK (source IN ('github', 'declaree')),
	PRIMARY KEY (utilisateur_id, competence, source)
);
CREATE INDEX competence_profil_recherche ON competence_profil (competence, utilisateur_id);

-- Dernier relevé des données publiques GitHub, remplacé à chaque génération.
CREATE TABLE profil_langage (
	utilisateur_id UUID NOT NULL REFERENCES profil (utilisateur_id) ON DELETE CASCADE,
	rang           INTEGER NOT NULL,
	nom            VARCHAR(50) NOT NULL,
	part           NUMERIC(4, 3) NOT NULL CHECK (part BETWEEN 0 AND 1),
	PRIMARY KEY (utilisateur_id, rang)
);

CREATE TABLE profil_depot_github (
	utilisateur_id UUID NOT NULL REFERENCES profil (utilisateur_id) ON DELETE CASCADE,
	rang           INTEGER NOT NULL,
	nom            VARCHAR(100) NOT NULL,
	url            VARCHAR(2048) NOT NULL,
	description    VARCHAR(350),
	langage        VARCHAR(50),
	etoiles        INTEGER NOT NULL CHECK (etoiles >= 0),
	mis_a_jour_le  TIMESTAMPTZ,
	PRIMARY KEY (utilisateur_id, rang)
);
