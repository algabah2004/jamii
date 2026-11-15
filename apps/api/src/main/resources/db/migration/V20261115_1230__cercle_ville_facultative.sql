-- Un cercle travaille en ligne : sa ville n'est plus demandée à la création (#157). Les cercles déjà créés la gardent.
ALTER TABLE cercle ALTER COLUMN ville DROP NOT NULL;
