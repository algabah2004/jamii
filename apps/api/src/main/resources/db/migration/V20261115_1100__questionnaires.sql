-- Les questionnaires personnalisés (contracts/socle.yaml, Question et Reponse ; #257).
-- Un questionnaire et ses réponses se lisent toujours en entier, avec leur hackathon, leur mission, leur inscription ou
-- leur proposition : du JSONB plutôt que des tables, validé par l'API (Questionnaire.java) avant d'être écrit.

-- Posé à l'inscription à un hackathon.
ALTER TABLE hackathon ADD COLUMN questionnaire JSONB NOT NULL DEFAULT '[]';
ALTER TABLE inscription ADD COLUMN reponses JSONB NOT NULL DEFAULT '[]';

-- Posé dans « Proposer mes services ». `proposition.reponses` (texte[], jamais rempli par l'interface) reste en place
-- pour ne rien perdre ; les réponses typées vont dans une colonne à part.
ALTER TABLE mission ADD COLUMN questionnaire JSONB NOT NULL DEFAULT '[]';
ALTER TABLE proposition ADD COLUMN reponses_questionnaire JSONB NOT NULL DEFAULT '[]';
