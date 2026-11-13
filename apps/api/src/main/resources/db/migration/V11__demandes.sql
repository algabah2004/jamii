-- Les demandes de PME (contracts/demandes.yaml). Le plan n'est pas stocké : il vit dans le service IA, que l'API
-- relit à chaque lecture. On garde seulement l'identifiant de la tâche qui le produit.
CREATE TABLE demande (
	id             UUID PRIMARY KEY,
	-- Le texte d'une demande est une donnée personnelle de son auteur : il disparaît avec le compte.
	auteur_id      UUID NOT NULL REFERENCES utilisateur (id) ON DELETE CASCADE,
	titre          VARCHAR(120) NOT NULL,
	description    TEXT NOT NULL,
	duree_semaines SMALLINT CHECK (duree_semaines BETWEEN 1 AND 26),
	-- NULL tant que la tâche n'est pas déposée dans le service IA (service injoignable : on réessaie à la lecture).
	tache_ia_id    UUID,
	cree_le        TIMESTAMPTZ NOT NULL,
	version        INTEGER NOT NULL DEFAULT 0
);

-- La limite de 10 plans par heure et par utilisateur compte les demandes récentes d'un auteur.
CREATE INDEX demande_auteur_cree_le ON demande (auteur_id, cree_le DESC);
