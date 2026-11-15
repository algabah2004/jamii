-- Créer une communauté (contracts/communautes.yaml v0.4, #155) : on garde qui l'a créée, pour la limite par jour.
-- Supprimer le compte garde la communauté, sans créateur : ses membres et ses échanges restent.
ALTER TABLE communaute ADD COLUMN createur_id UUID REFERENCES utilisateur (id) ON DELETE SET NULL;

CREATE INDEX communaute_createur ON communaute (createur_id, cree_le);
