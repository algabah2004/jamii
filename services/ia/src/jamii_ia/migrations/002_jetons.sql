-- Jetons consommés par l'appel au fournisseur IA : sert au budget du jour (IA_BUDGET_JOUR).
ALTER TABLE ia.taches ADD COLUMN jetons integer NOT NULL DEFAULT 0;
-- Additionner les jetons du jour sans parcourir toute la file.
CREATE INDEX taches_termine_le ON ia.taches (termine_le) WHERE jetons > 0;
