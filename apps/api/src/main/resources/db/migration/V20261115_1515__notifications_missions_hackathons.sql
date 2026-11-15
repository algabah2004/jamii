-- Les notifications des missions et des hackathons (demande #201) : de nouveaux types, écrits par le module du réseau
-- à partir des événements publics des autres modules. Une décision de candidature = un type, pour que la phrase la dise.
ALTER TABLE notification DROP CONSTRAINT notification_type_check;
ALTER TABLE notification ADD CONSTRAINT notification_type_check CHECK (type IN (
	'message_recu', 'invitation_cercle', 'demande_contact', 'contact_accepte', 'membre_rejoint', 'tirage_effectue',
	'tour_livre', 'cercle_recommande',
	'proposition_recue', 'devis_recu', 'mission_attribuee',
	'candidature_recue', 'candidature_acceptee', 'candidature_refusee', 'candidature_attente', 'invitation_equipe'));
