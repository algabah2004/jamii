-- Le jury d'un hackathon et ses notes (contracts/hackathons.yaml, tag « jury », #173).

CREATE TABLE jure (
	hackathon_id   UUID NOT NULL REFERENCES hackathon (id) ON DELETE CASCADE,
	utilisateur_id UUID NOT NULL REFERENCES utilisateur (id) ON DELETE CASCADE,
	-- Pour répartir les projets entre jurés ; NULL : il voit tout.
	groupe         VARCHAR(40),
	cree_le        TIMESTAMPTZ NOT NULL,
	PRIMARY KEY (hackathon_id, utilisateur_id)
);

-- Une valeur de 1 à 5 par critère, par juré et par rendu. Les notes d'un juré sur un rendu se remplacent ensemble.
-- `utilisateur_id` n'a volontairement pas de clé étrangère : à la suppression de son compte, les notes d'un juré sont
-- anonymisées (un identifiant tiré au hasard, le même pour toutes ses notes) au lieu d'être effacées, pour que le
-- classement déjà calculé ne change pas (DonneesHackathons).
CREATE TABLE note (
	rendu_id       UUID NOT NULL REFERENCES rendu (id) ON DELETE CASCADE,
	utilisateur_id UUID NOT NULL,
	critere_id     UUID NOT NULL REFERENCES critere (id) ON DELETE CASCADE,
	valeur         SMALLINT NOT NULL CHECK (valeur BETWEEN 1 AND 5),
	PRIMARY KEY (rendu_id, utilisateur_id, critere_id)
);

-- Le commentaire d'un juré sur un rendu : le « retour du jury », montré sans son nom une fois les résultats publiés.
CREATE TABLE appreciation (
	rendu_id       UUID NOT NULL REFERENCES rendu (id) ON DELETE CASCADE,
	utilisateur_id UUID NOT NULL, -- anonymisé comme les notes
	commentaire    VARCHAR(2000),
	modifie_le     TIMESTAMPTZ NOT NULL,
	PRIMARY KEY (rendu_id, utilisateur_id)
);

CREATE INDEX note_utilisateur ON note (utilisateur_id);
