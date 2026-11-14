-- Les avis d'une mission, et ce que devient une mission quand un compte est supprimé (contracts/missions.yaml, #180).

-- Un avis par partie et par mission, une fois la mission terminée. `auteur` dit qui l'a donné ; `auteur_id` est vidé
-- à la suppression du compte : l'avis reste, anonyme, pour l'autre partie.
CREATE TABLE avis_mission (
	mission_id UUID NOT NULL REFERENCES mission (id) ON DELETE CASCADE,
	auteur     VARCHAR(12) NOT NULL CHECK (auteur IN ('client', 'developpeur')),
	auteur_id  UUID REFERENCES utilisateur (id) ON DELETE SET NULL,
	note       SMALLINT NOT NULL CHECK (note BETWEEN 1 AND 5),
	texte      VARCHAR(1000),
	donne_le   TIMESTAMPTZ NOT NULL,
	-- Départage deux envois simultanés (`avis_deja_donne`).
	PRIMARY KEY (mission_id, auteur)
);

-- Supprimer un compte ne doit pas effacer le travail de l'autre partie (sa mission terminée, ses avis, son devis).
-- Ces identifiants n'ont donc plus de clé étrangère : le compte supprimé, ils ne renvoient plus à personne et la vue
-- affiche « Compte supprimé ». DonneesMissions ferme ou annule ce qui était en cours.
ALTER TABLE mission DROP CONSTRAINT mission_client_id_fkey;
ALTER TABLE mission DROP CONSTRAINT mission_developpeur_id_fkey;
ALTER TABLE proposition DROP CONSTRAINT proposition_developpeur_id_fkey;
