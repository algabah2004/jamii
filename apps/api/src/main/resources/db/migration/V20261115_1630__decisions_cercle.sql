-- Les décisions du cercle (contracts/cercles.yaml v0.3, #157) : ce que l'équipe a tranché (« on part sur Flutter »,
-- « la démo est vendredi »). Un message du salon d'un nouveau type, qui reste consultable à part.
ALTER TABLE message_cercle DROP CONSTRAINT message_cercle_type_check;
ALTER TABLE message_cercle ADD CONSTRAINT message_cercle_type_check
	CHECK (type IN ('texte', 'point', 'coup_de_main', 'evenement', 'appel', 'decision'));
ALTER TABLE message_cercle ADD CONSTRAINT message_cercle_decision_texte
	CHECK (type <> 'decision' OR length(btrim(texte)) > 0);

CREATE INDEX message_cercle_decisions ON message_cercle (cercle_id, numero) WHERE type = 'decision';
