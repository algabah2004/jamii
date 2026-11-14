-- Les services proposés par l'orientation d'un besoin (#65) : SystaONE s'ajoute, et une demande ne compte qu'une
-- proposition par service, même relue ou précisée (taux impossible à gonfler en rechargeant la page).
ALTER TABLE moment_systalink DROP CONSTRAINT moment_systalink_service_check;
ALTER TABLE moment_systalink ADD CONSTRAINT moment_systalink_service_check
	CHECK (service IN ('datacloud', 'orion', 'yokk', 'systatalk', 'systaforms', 'domaines', 'systaone'));

CREATE UNIQUE INDEX moment_systalink_une_fois_par_demande
	ON moment_systalink (service, contexte, contexte_id) WHERE contexte = 'demande';
