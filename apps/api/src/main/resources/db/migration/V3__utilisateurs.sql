-- Les comptes Jamii. Un compte GitHub est reconnu par son identifiant numérique, qui ne change jamais
-- (le login GitHub, lui, peut être renommé). Un compte créé par e-mail n'a pas de github_id.
CREATE TABLE utilisateur (
	id            UUID PRIMARY KEY,
	nom           VARCHAR(100) NOT NULL,
	email         VARCHAR(254),
	avatar_url    VARCHAR(2048),
	github_id     BIGINT UNIQUE,
	login_github  VARCHAR(39),
	cree_le       TIMESTAMPTZ NOT NULL,
	mis_a_jour_le TIMESTAMPTZ NOT NULL
);

-- Une adresse e-mail ne peut appartenir qu'à un seul compte (comparaison sans tenir compte des majuscules).
CREATE UNIQUE INDEX utilisateur_email_unique ON utilisateur (lower(email));

CREATE TABLE utilisateur_role (
	utilisateur_id UUID NOT NULL REFERENCES utilisateur (id) ON DELETE CASCADE,
	role           VARCHAR(20) NOT NULL CHECK (role IN ('membre', 'moderateur', 'admin')),
	PRIMARY KEY (utilisateur_id, role)
);
