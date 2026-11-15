-- Lancer un appel dans un salon (contracts/communautes.yaml v0.7, #155) : un appel n'existe que quand quelqu'un le
-- lance. Une salle neuve à chaque appel. « En cours » = lancé il y a moins d'une heure et pas terminé.
CREATE TABLE appel_salon (
	id         UUID PRIMARY KEY,
	salon_id   UUID NOT NULL REFERENCES salon (id) ON DELETE CASCADE,
	lance_par  UUID REFERENCES utilisateur (id) ON DELETE SET NULL,
	salle      VARCHAR(140) NOT NULL UNIQUE,
	lance_le   TIMESTAMPTZ NOT NULL,
	termine_le TIMESTAMPTZ
);

-- Un seul appel non terminé par salon.
CREATE UNIQUE INDEX appel_salon_un_seul ON appel_salon (salon_id) WHERE termine_le IS NULL;
CREATE INDEX appel_salon_lanceur ON appel_salon (lance_par, lance_le);

-- Le message qui annonce un appel dans le salon.
ALTER TABLE message_salon ADD COLUMN appel_id UUID REFERENCES appel_salon (id) ON DELETE SET NULL;
