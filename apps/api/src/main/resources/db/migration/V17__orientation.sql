-- L'orientation d'un besoin (contracts/demandes.yaml v0.2, #20).
ALTER TABLE demande
	ADD COLUMN secteur VARCHAR(60),
	ADD COLUMN ville VARCHAR(80),
	ADD COLUMN pays CHAR(2) CHECK (pays ~ '^[A-Z]{2}$'),
	-- « Recevoir les offres Systalink adaptées à mon projet » : jamais vrai par défaut, retirable à tout moment.
	ADD COLUMN offres_systalink BOOLEAN NOT NULL DEFAULT FALSE,
	ADD COLUMN offres_accord_le TIMESTAMPTZ,
	-- Où en est Systalink avec cette personne (clients suivis, contracts/systalink.yaml).
	ADD COLUMN suivi_statut VARCHAR(12) NOT NULL DEFAULT 'nouveau'
		CHECK (suivi_statut IN ('nouveau', 'contacte', 'client', 'sans_suite')),
	-- Le résumé de l'orientation, recopié une fois terminée : résumé, secteur, identifiants des services, compétences,
	-- développeurs nécessaires. Sert aux tendances et aux clients suivis. Le plan, lui, reste dans le service IA.
	ADD COLUMN orientation JSONB;

-- Les clients suivis : seulement les demandes avec accord, les plus récentes d'abord.
CREATE INDEX demande_offres_systalink ON demande (cree_le DESC) WHERE offres_systalink;
