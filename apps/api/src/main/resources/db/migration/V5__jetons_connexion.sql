-- Les liens de connexion par e-mail (sans mot de passe). Le jeton lui-même n'est jamais stocké :
-- seulement son empreinte SHA-256. Une fuite de la base ne permet donc pas de se connecter.
CREATE TABLE jeton_connexion (
	id         UUID PRIMARY KEY,
	email      VARCHAR(254) NOT NULL,
	empreinte  CHAR(64) NOT NULL UNIQUE,
	expire_le  TIMESTAMPTZ NOT NULL,
	utilise_le TIMESTAMPTZ,
	cree_le    TIMESTAMPTZ NOT NULL
);

-- Compter les demandes récentes d'une adresse (limite de 5 par heure) et supprimer les vieux jetons.
CREATE INDEX jeton_connexion_email_cree_le ON jeton_connexion (email, cree_le);
CREATE INDEX jeton_connexion_cree_le ON jeton_connexion (cree_le);
