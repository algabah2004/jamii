-- N'importe quel membre d'un cercle peut lancer le tirage (#157) : on garde qui l'a lancé (`tirage.parId`).
-- Les cercles déjà tirés l'ont été par leur créateur, la seule personne qui le pouvait jusqu'ici.
ALTER TABLE cercle ADD COLUMN tire_par UUID REFERENCES utilisateur (id) ON DELETE SET NULL;
UPDATE cercle SET tire_par = createur_id WHERE tirage_le IS NOT NULL;
