-- « En ligne » et « Vu il y a … » dans les messages (#157). Une ligne par personne : sa dernière activité (notée au plus
-- une fois par minute) et son choix de la montrer. Effacée avec le compte.
CREATE TABLE presence (
	utilisateur_id UUID PRIMARY KEY REFERENCES utilisateur (id) ON DELETE CASCADE,
	vu_le          TIMESTAMPTZ,
	visible        BOOLEAN NOT NULL DEFAULT true
);
