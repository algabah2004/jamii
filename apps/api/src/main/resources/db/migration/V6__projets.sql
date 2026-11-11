-- Les projets d'équipe et leurs membres (docs/modele-donnees.md §4). Les rôles recherchés, candidatures, jalons et
-- tâches arrivent avec leurs routes, dans des migrations suivantes.
CREATE TABLE projet (
	id                   UUID PRIMARY KEY,
	titre                VARCHAR(120) NOT NULL,
	description          TEXT NOT NULL DEFAULT '',
	origine              VARCHAR(10) NOT NULL CHECK (origine IN ('idee', 'demande')),
	-- Pas de clé étrangère : la table des demandes appartient à un autre module (règle commune n° 4).
	demande_id           UUID,
	-- Pas de ON DELETE : supprimer un compte exige d'abord de transmettre ses projets (DELETE /api/me).
	porteur_id           UUID NOT NULL REFERENCES utilisateur (id),
	projet_repris_id     UUID REFERENCES projet (id),
	statut               VARCHAR(20) NOT NULL DEFAULT 'recrutement'
		CHECK (statut IN ('recrutement', 'en_cours', 'en_pause', 'livre', 'abandonne')),
	derniere_activite_le TIMESTAMPTZ NOT NULL,
	cree_le              TIMESTAMPTZ NOT NULL,
	mis_a_jour_le        TIMESTAMPTZ NOT NULL,
	version              INTEGER NOT NULL DEFAULT 0
);

-- Une demande de PME n'a qu'un seul projet actif à la fois.
CREATE UNIQUE INDEX projet_demande_active ON projet (demande_id)
	WHERE demande_id IS NOT NULL AND statut NOT IN ('abandonne', 'livre');

-- Détection des projets inactifs (alerte de continuité) et liste « mes projets ».
CREATE INDEX projet_statut_activite ON projet (statut, derniere_activite_le);
CREATE INDEX projet_porteur ON projet (porteur_id);
CREATE INDEX projet_cree_le ON projet (cree_le DESC);

-- L'équipe, c'est l'ensemble des membres actifs (parti_le IS NULL). Un membre qui part reste dans l'historique :
-- sa contribution est prouvée. Supprimer un compte supprime son appartenance (donnée personnelle).
CREATE TABLE membre_equipe (
	projet_id      UUID NOT NULL REFERENCES projet (id) ON DELETE CASCADE,
	utilisateur_id UUID NOT NULL REFERENCES utilisateur (id) ON DELETE CASCADE,
	rejoint_le     TIMESTAMPTZ NOT NULL,
	parti_le       TIMESTAMPTZ,
	PRIMARY KEY (projet_id, utilisateur_id)
);

CREATE INDEX membre_equipe_utilisateur_actif ON membre_equipe (utilisateur_id) WHERE parti_le IS NULL;
