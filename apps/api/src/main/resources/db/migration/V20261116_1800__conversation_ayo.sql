-- Les demandes deviennent une vraie conversation avec Ayo (16/11/2026) : chaque réponse de la personne garde ce
-- qu'Ayo venait de lui dire, pour relire le fil et pour qu'Ayo lise toute la conversation.

-- La dernière réponse d'Ayo, en texte : son message, et sa question s'il en pose une (`ayo_question`).
ALTER TABLE demande
	ADD COLUMN ayo_dernier  VARCHAR(1200),
	ADD COLUMN ayo_question BOOLEAN NOT NULL DEFAULT FALSE;

-- Ce qu'Ayo avait dit juste avant cette réponse de la personne. Vide pour les précisions d'avant la conversation.
ALTER TABLE demande_precision
	ADD COLUMN ayo          VARCHAR(1200),
	ADD COLUMN ayo_question BOOLEAN NOT NULL DEFAULT FALSE;

-- Une conversation dure plus que 5 précisions de 500 caractères.
ALTER TABLE demande_precision ALTER COLUMN texte TYPE VARCHAR(1000);
ALTER TABLE demande_precision DROP CONSTRAINT demande_precision_rang_check;
ALTER TABLE demande_precision ADD CONSTRAINT demande_precision_rang_check CHECK (rang BETWEEN 1 AND 12);
