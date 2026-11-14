-- Publier sa demande (contracts/demandes.yaml v0.3, #158) : l'auteur choisit le résumé que la communauté verra.
-- Seul ce résumé devient public : jamais le texte d'origine, jamais l'orientation complète.
ALTER TABLE demande
	ADD COLUMN publiee_le     TIMESTAMPTZ,
	ADD COLUMN publiee_resume VARCHAR(600);

-- Une demande publiée a toujours son résumé, et seulement elle.
ALTER TABLE demande ADD CONSTRAINT demande_publication_complete CHECK ((publiee_le IS NULL) = (publiee_resume IS NULL));
