-- Les appels en tête-à-tête dans une discussion privée (#157), en WebRTC : le son et l'image passent directement entre
-- les deux navigateurs (ou par notre relais TURN). L'API ne garde que la mise en relation et l'historique.
-- `offre` et `reponse` (les descriptions SDP) contiennent les adresses réseau des deux personnes : elles ne servent qu'à
-- établir l'appel, et sont effacées dès qu'il est terminé, refusé, manqué ou annulé.
CREATE TABLE appel_prive (
	id          UUID PRIMARY KEY,
	appelant_id UUID NOT NULL REFERENCES utilisateur (id) ON DELETE CASCADE,
	appele_id   UUID NOT NULL REFERENCES utilisateur (id) ON DELETE CASCADE,
	-- Le message de la conversation qui garde la trace de l'appel.
	message_id  UUID REFERENCES message (id) ON DELETE SET NULL,
	video       BOOLEAN NOT NULL,
	statut      VARCHAR(12) NOT NULL CHECK (statut IN ('sonne', 'en_cours', 'termine', 'refuse', 'manque', 'annule')),
	offre       TEXT,
	reponse     TEXT,
	cree_le     TIMESTAMPTZ NOT NULL,
	repondu_le  TIMESTAMPTZ,
	termine_le  TIMESTAMPTZ,
	CONSTRAINT appel_pas_a_soi_meme CHECK (appelant_id <> appele_id)
);

-- « Est-ce qu'on m'appelle ? », lu toutes les 2 secondes par chaque personne connectée.
CREATE INDEX appel_prive_qui_sonne ON appel_prive (appele_id, cree_le DESC) WHERE statut = 'sonne';
CREATE INDEX appel_prive_message ON appel_prive (message_id);
