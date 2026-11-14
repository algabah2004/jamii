-- Les salons d'une communauté et leurs messages (contracts/communautes.yaml v0.2, #155).

-- `salle_appel` : le nom de la salle d'appel du salon, avec un suffixe aléatoire pour qu'on ne la devine pas.
CREATE TABLE salon (
	id            UUID PRIMARY KEY,
	communaute_id UUID NOT NULL REFERENCES communaute (id) ON DELETE CASCADE,
	slug          VARCHAR(40) NOT NULL CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
	nom           VARCHAR(60) NOT NULL,
	sujet         VARCHAR(200) NOT NULL,
	ordre         SMALLINT NOT NULL,
	salle_appel   VARCHAR(120) NOT NULL UNIQUE,
	UNIQUE (communaute_id, slug)
);

-- `numero` : croissant, sert de curseur au front qui relit les nouveaux messages toutes les 3 secondes.
-- Supprimer un compte supprime ses messages (donnée personnelle, sans valeur de référence comme une réponse).
CREATE TABLE message_salon (
	id        UUID PRIMARY KEY,
	numero    BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE,
	salon_id  UUID NOT NULL REFERENCES salon (id) ON DELETE CASCADE,
	auteur_id UUID NOT NULL REFERENCES utilisateur (id) ON DELETE CASCADE,
	texte     VARCHAR(2000) NOT NULL CHECK (length(btrim(texte)) > 0),
	envoye_le TIMESTAMPTZ NOT NULL
);

CREATE INDEX message_salon_lecture ON message_salon (salon_id, numero);
CREATE INDEX message_salon_auteur ON message_salon (auteur_id, envoye_le);

-- Trois salons dans chaque communauté.
INSERT INTO salon (id, communaute_id, slug, nom, sujet, ordre, salle_appel)
SELECT gen_random_uuid(), c.id, s.slug, s.nom, s.sujet, s.ordre,
	'jamii-' || c.slug || '-' || s.slug || '-' || substr(replace(gen_random_uuid()::text, '-', ''), 1, 12)
FROM communaute c
CROSS JOIN (VALUES
	('general', 'général', 'Se présenter et parler de tout.', 1),
	('entraide', 'entraide', 'Une question rapide, un blocage : quelqu''un t''aide.', 2),
	('projets', 'projets', 'Montrer ce qu''on construit et trouver des coéquipiers.', 3)
) AS s (slug, nom, sujet, ordre);
