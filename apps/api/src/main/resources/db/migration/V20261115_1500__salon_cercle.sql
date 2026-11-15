-- Le salon d'un cercle (contracts/cercles.yaml v0.2, #157) : un fil privé aux membres, pensé pour la tontine.
-- Cinq sortes de messages :
--   texte        : un message libre ;
--   point        : le point du jour d'un membre (fait, prévu, et ce qui le bloque s'il y a lieu) ;
--   coup_de_main : « je bloque, qui peut m'aider ? », qui reste en haut du salon tant qu'il n'est pas résolu ;
--   evenement    : écrit par Jamii quand le cercle avance (arrivée d'un membre, tirage, tour ouvert ou livré…) ;
--   appel        : l'annonce d'un appel lancé dans le salon.
-- `numero` : croissant, c'est le curseur du front qui relit les nouveaux messages toutes les quelques secondes.
CREATE TABLE message_cercle (
	id                 UUID PRIMARY KEY,
	numero             BIGINT GENERATED ALWAYS AS IDENTITY UNIQUE,
	cercle_id          UUID NOT NULL REFERENCES cercle (id) ON DELETE CASCADE,
	-- NULL pour un événement (écrit par Jamii). Supprimer un compte supprime ses messages.
	auteur_id          UUID REFERENCES utilisateur (id) ON DELETE CASCADE,
	type               VARCHAR(15) NOT NULL CHECK (type IN ('texte', 'point', 'coup_de_main', 'evenement', 'appel')),
	texte              VARCHAR(2000),
	point_fait         VARCHAR(500),
	point_prevu        VARCHAR(500),
	point_bloque       VARCHAR(500),
	evenement          VARCHAR(20) CHECK (evenement IN ('cercle_cree', 'membre_rejoint', 'tirage', 'tour_ouvert', 'tour_livre', 'cercle_termine')),
	evenement_tour     INTEGER,
	-- La personne dont parle l'événement (qui arrive, à qui est le tour).
	evenement_personne UUID REFERENCES utilisateur (id) ON DELETE SET NULL,
	resolu_le          TIMESTAMPTZ,
	resolu_par         UUID REFERENCES utilisateur (id) ON DELETE SET NULL,
	envoye_le          TIMESTAMPTZ NOT NULL,
	CHECK ((type = 'evenement') = (evenement IS NOT NULL AND auteur_id IS NULL)),
	CHECK (type <> 'texte' OR length(btrim(texte)) > 0),
	CHECK (type <> 'coup_de_main' OR length(btrim(texte)) > 0),
	CHECK (type <> 'point' OR (length(btrim(point_fait)) > 0 AND length(btrim(point_prevu)) > 0)),
	CHECK (type = 'coup_de_main' OR resolu_le IS NULL)
);

CREATE INDEX message_cercle_lecture ON message_cercle (cercle_id, numero);
CREATE INDEX message_cercle_auteur ON message_cercle (auteur_id, envoye_le);
-- Les coups de main encore ouverts, montrés en haut du salon.
CREATE INDEX message_cercle_coups_de_main ON message_cercle (cercle_id, numero) WHERE type = 'coup_de_main' AND resolu_le IS NULL;

-- Les appels du salon : une salle neuve à chaque appel, « en cours » une heure au plus (comme les salons).
CREATE TABLE appel_cercle (
	id         UUID PRIMARY KEY,
	cercle_id  UUID NOT NULL REFERENCES cercle (id) ON DELETE CASCADE,
	lance_par  UUID REFERENCES utilisateur (id) ON DELETE SET NULL,
	salle      VARCHAR(140) NOT NULL UNIQUE,
	lance_le   TIMESTAMPTZ NOT NULL,
	termine_le TIMESTAMPTZ
);

CREATE UNIQUE INDEX appel_cercle_un_seul ON appel_cercle (cercle_id) WHERE termine_le IS NULL;
CREATE INDEX appel_cercle_lanceur ON appel_cercle (lance_par, lance_le);

ALTER TABLE message_cercle ADD COLUMN appel_id UUID REFERENCES appel_cercle (id) ON DELETE SET NULL;

-- Les cercles qui existent déjà : leur histoire, telle qu'on la connaît, ouvre le salon (dans l'ordre du temps).
INSERT INTO message_cercle (id, cercle_id, type, evenement, evenement_tour, evenement_personne, envoye_le)
SELECT gen_random_uuid(), h.cercle_id, 'evenement', h.evenement, h.tour, h.personne, h.le
FROM (
	SELECT c.id AS cercle_id, 'cercle_cree' AS evenement, NULL::INTEGER AS tour, c.createur_id AS personne, c.cree_le AS le, 0 AS rang
	FROM cercle c
	UNION ALL
	SELECT m.cercle_id, 'membre_rejoint', NULL, m.utilisateur_id, m.rejoint_le, 1
	FROM cercle_membre m JOIN cercle c ON c.id = m.cercle_id WHERE m.utilisateur_id <> c.createur_id
	UNION ALL
	SELECT c.id, 'tirage', NULL, c.tire_par, c.tirage_le, 2 FROM cercle c WHERE c.tirage_le IS NOT NULL
	UNION ALL
	SELECT m.cercle_id, 'tour_livre', m.rang, m.utilisateur_id, m.livre_le, 3 FROM cercle_membre m WHERE m.livre_le IS NOT NULL
) h
ORDER BY h.le, h.rang;
