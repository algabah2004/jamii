-- Les abonnés (#157) : suivre quelqu'un, sans son accord, comme sur LinkedIn. Distinct des contacts (réciproques).
-- Effacé avec l'un ou l'autre compte.
CREATE TABLE abonnement (
	abonne_id UUID        NOT NULL REFERENCES utilisateur (id) ON DELETE CASCADE,
	suivi_id  UUID        NOT NULL REFERENCES utilisateur (id) ON DELETE CASCADE,
	depuis    TIMESTAMPTZ NOT NULL,
	PRIMARY KEY (abonne_id, suivi_id),
	CHECK (abonne_id <> suivi_id)
);
CREATE INDEX abonnement_suivi ON abonnement (suivi_id, depuis DESC);
CREATE INDEX abonnement_abonne ON abonnement (abonne_id, depuis DESC);

-- De nouvelles notifications : quelqu'un me suit ; une personne que je suis ouvre un cercle ou publie dans une communauté.
ALTER TABLE notification DROP CONSTRAINT notification_type_check;
ALTER TABLE notification ADD CONSTRAINT notification_type_check CHECK (type IN (
	'message_recu', 'invitation_cercle', 'demande_contact', 'contact_accepte', 'membre_rejoint', 'tirage_effectue',
	'tour_livre', 'cercle_recommande',
	'proposition_recue', 'devis_recu', 'mission_attribuee',
	'candidature_recue', 'candidature_acceptee', 'candidature_refusee', 'candidature_attente', 'invitation_equipe',
	'nouvel_abonne', 'suivi_cercle', 'suivi_publication'));
