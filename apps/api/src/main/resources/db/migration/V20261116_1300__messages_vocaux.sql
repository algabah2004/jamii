-- Les messages vocaux (#157) : une quatrième sorte de fichier joint, servie comme un son.
ALTER TABLE fichier DROP CONSTRAINT fichier_sorte_check;
ALTER TABLE fichier ADD CONSTRAINT fichier_sorte_check CHECK (sorte IN ('image', 'video', 'document', 'audio'));
