-- La discussion d'un hackathon (#252) : les annonces de l'organisateur, et la communauté où les participants échangent.

CREATE TABLE annonce_hackathon (
	id           UUID PRIMARY KEY,
	hackathon_id UUID NOT NULL REFERENCES hackathon (id) ON DELETE CASCADE,
	auteur_id    UUID REFERENCES utilisateur (id) ON DELETE SET NULL,
	texte        VARCHAR(2000) NOT NULL CHECK (length(btrim(texte)) > 0),
	publiee_le   TIMESTAMPTZ NOT NULL
);
CREATE INDEX annonce_hackathon_par_hackathon ON annonce_hackathon (hackathon_id, publiee_le DESC);

-- Le nom court d'une communauté (module communautés) : un lien, pas une clé étrangère, pour garder les modules séparés.
-- Si la communauté disparaît, la page du hackathon le dit au lieu de casser.
CREATE TABLE communaute_hackathon (
	hackathon_id    UUID PRIMARY KEY REFERENCES hackathon (id) ON DELETE CASCADE,
	communaute_slug VARCHAR(80) NOT NULL,
	liee_le         TIMESTAMPTZ NOT NULL
);
