-- Une image de couverture, facultative : sans elle, la communauté garde son motif. Le fichier est une image publique
-- (ni message privé, ni cercle), envoyée par la personne qui la choisit. Supprimer le fichier rend le motif.
ALTER TABLE communaute ADD COLUMN couverture_id UUID REFERENCES fichier (id) ON DELETE SET NULL;
