-- Profil professionnel (#159, contracts/profils.yaml) : ce que la personne déclare d'elle-même, en plus de GitHub.
ALTER TABLE profil
	ADD COLUMN titre              VARCHAR(80),
	-- Liens http(s) vérifiés par l'API (ModificationProfil) : jamais de javascript: ni de data:.
	ADD COLUMN linkedin           VARCHAR(300),
	ADD COLUMN site_web           VARCHAR(300),
	-- Codes ISO 639-1 séparés par des virgules (fr,wo,en), 8 au plus.
	ADD COLUMN langues            VARCHAR(30) CHECK (langues ~ '^[a-z]{2}(,[a-z]{2}){0,7}$'),
	-- Compétences mises en avant, séparées par des virgules, 3 au plus (forme de Competences.java).
	ADD COLUMN competences_phares VARCHAR(160);

-- Réalisations déclarées hors de Jamii : affichées « Déclaré », jamais comptées comme preuve.
CREATE TABLE profil_realisation (
	utilisateur_id UUID NOT NULL REFERENCES profil (utilisateur_id) ON DELETE CASCADE,
	rang           INTEGER NOT NULL CHECK (rang BETWEEN 0 AND 5),
	titre          VARCHAR(80) NOT NULL,
	role           VARCHAR(60),
	description    VARCHAR(300),
	-- Compétences séparées par des virgules, 8 au plus.
	technologies   VARCHAR(420),
	url            VARCHAR(300),
	annee          INTEGER CHECK (annee BETWEEN 1990 AND 2100),
	PRIMARY KEY (utilisateur_id, rang)
);
