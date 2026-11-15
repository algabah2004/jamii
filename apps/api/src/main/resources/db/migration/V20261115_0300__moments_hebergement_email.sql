-- Ayo conseille aussi l'hébergement avec boutique en ligne et l'e-mail professionnel (catalogue vérifié le 15/11).
ALTER TABLE moment_systalink DROP CONSTRAINT moment_systalink_service_check;
ALTER TABLE moment_systalink ADD CONSTRAINT moment_systalink_service_check
	CHECK (service IN ('datacloud', 'orion', 'yokk', 'systatalk', 'systaforms', 'domaines', 'systaone', 'hebergement', 'email'));
