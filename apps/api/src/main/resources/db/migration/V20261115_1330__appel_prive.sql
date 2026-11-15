-- Un appel vocal et vidéo entre deux personnes (#157) : le message qui l'annonce porte la salle, neuve à chaque appel.
ALTER TABLE message ADD COLUMN salle_appel text;
