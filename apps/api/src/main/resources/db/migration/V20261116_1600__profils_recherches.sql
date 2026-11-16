-- Les profils dont un cercle a besoin, choisis à sa création dans une liste fixe (5 au plus). Vide : rien de précisé.
ALTER TABLE cercle ADD COLUMN profils_recherches TEXT[] NOT NULL DEFAULT '{}'
  CHECK (cardinality(profils_recherches) <= 5
    AND profils_recherches <@ ARRAY['front', 'back', 'mobile', 'design', 'data', 'ia', 'devops', 'securite', 'produit', 'test']::TEXT[]);
