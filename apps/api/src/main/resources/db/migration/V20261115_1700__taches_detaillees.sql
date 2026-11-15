-- Des tâches qu'on comprend sans demander (contracts/projets.yaml v0.6) : ce qu'il faut faire et quand c'est fini
-- (description), et par quoi commencer (priorité). Les tâches existantes passent en priorité normale.
ALTER TABLE tache
	ADD COLUMN description VARCHAR(2000),
	ADD COLUMN priorite    VARCHAR(8) NOT NULL DEFAULT 'normale' CHECK (priorite IN ('haute', 'normale', 'basse'));
