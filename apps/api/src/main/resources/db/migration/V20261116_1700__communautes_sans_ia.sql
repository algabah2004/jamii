-- Les communautés sont entre humains : l'IA n'y propose plus de réponse (décision du 16/11/2026).
-- Les réponses écrites par l'IA disparaissent. Une question qui en avait accepté une redevient ouverte.
UPDATE publication SET reponse_acceptee_id = NULL, acceptee_le = NULL
WHERE reponse_acceptee_id IN (SELECT id FROM reponse WHERE proposee_par_ia);

DELETE FROM reponse WHERE proposee_par_ia;

-- Supprimer une colonne supprime aussi les contraintes CHECK qui la lisent.
ALTER TABLE publication DROP COLUMN proposition_ia;
ALTER TABLE reponse DROP COLUMN proposee_par_ia, DROP COLUMN validee_par_id;
