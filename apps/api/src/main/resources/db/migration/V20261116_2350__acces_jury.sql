-- Les jurés de la CADEV qui ont entré le code d'accès jury (SYSTALINK_CODE_JURY) : ils voient l'espace Systalink
-- comme son équipe. Supprimer le code de la configuration ferme la porte à de nouveaux jurés ; vider cette table
-- retire l'accès à ceux qui l'ont déjà.
CREATE TABLE acces_jury (
    utilisateur_id UUID PRIMARY KEY REFERENCES utilisateur (id) ON DELETE CASCADE,
    accorde_le TIMESTAMPTZ NOT NULL DEFAULT now()
);
