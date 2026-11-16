-- L'appel du cercle en WebRTC (#157) : au plus 6 membres, chacun relié directement à chacun (un maillage). L'API ne
-- garde que qui est là (signalé toutes les 1,5 s) et une boîte aux lettres des offres et réponses SDP d'une personne à
-- une autre. Le son et l'image ne passent jamais par l'API.

CREATE TABLE appel_cercle_presence (
	appel_id       UUID NOT NULL REFERENCES appel_cercle (id) ON DELETE CASCADE,
	utilisateur_id UUID NOT NULL REFERENCES utilisateur (id) ON DELETE CASCADE,
	-- Quand la personne est arrivée : décide qui envoie l'offre à qui (le dernier arrivé), et révèle un retour.
	arrive_le      TIMESTAMPTZ NOT NULL,
	vu_le          TIMESTAMPTZ NOT NULL,
	PRIMARY KEY (appel_id, utilisateur_id)
);

-- Les SDP contiennent les adresses réseau : lues une fois puis effacées, et jamais gardées plus d'une minute.
CREATE TABLE appel_cercle_signal (
	id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
	appel_id UUID NOT NULL REFERENCES appel_cercle (id) ON DELETE CASCADE,
	de       UUID NOT NULL REFERENCES utilisateur (id) ON DELETE CASCADE,
	a        UUID NOT NULL REFERENCES utilisateur (id) ON DELETE CASCADE,
	type     VARCHAR(8) NOT NULL CHECK (type IN ('offre', 'reponse')),
	sdp      TEXT NOT NULL,
	cree_le  TIMESTAMPTZ NOT NULL
);

CREATE INDEX appel_cercle_signal_boite ON appel_cercle_signal (appel_id, a);
