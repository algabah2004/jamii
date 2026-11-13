-- Carte de compétences proposée par l'IA (contracts/profils.yaml, contracts/ia.yaml analyse_profil, #20).
-- La carte proposée reste privée ; seules les compétences que la personne garde deviennent `ia`, avec niveau et preuves.
ALTER TABLE competence_profil DROP CONSTRAINT competence_profil_source_check;
ALTER TABLE competence_profil ADD CONSTRAINT competence_profil_source_check
	CHECK (source IN ('github', 'declaree', 'ia'));

ALTER TABLE competence_profil
	ADD COLUMN niveau  VARCHAR(15) CHECK (niveau IN ('debutant', 'intermediaire', 'confirme')),
	-- Noms de dépôts publics séparés par des virgules (un nom GitHub n'en contient jamais), 3 au plus.
	ADD COLUMN preuves VARCHAR(310);
ALTER TABLE competence_profil ADD CONSTRAINT competence_profil_ia_complete
	CHECK ((source = 'ia') = (niveau IS NOT NULL AND preuves IS NOT NULL));

-- La dernière carte proposée, en JSON. Remplacée à chaque génération.
ALTER TABLE profil ADD COLUMN competences_proposees TEXT CHECK (length(competences_proposees) <= 20000);
