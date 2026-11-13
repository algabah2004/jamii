-- Un projet né d'un tour de cercle a sa propre origine : la vitrine le montre avec le label « Tech Tontine »
-- (#109). Seul le tirage d'un cercle crée ces projets ; POST /api/projets n'accepte toujours que idee et demande.
ALTER TABLE projet DROP CONSTRAINT projet_origine_check;
ALTER TABLE projet ADD CONSTRAINT projet_origine_check CHECK (origine IN ('idee', 'demande', 'cercle'));

-- Les tours déjà tirés avant cette migration.
UPDATE projet SET origine = 'cercle'
WHERE id IN (SELECT projet_id FROM cercle_membre WHERE projet_id IS NOT NULL);
