-- La langue de l'écran au moment de la demande : l'IA y répond quand le texte ne dit pas clairement la sienne.
ALTER TABLE demande ADD COLUMN langue TEXT NOT NULL DEFAULT 'fr' CHECK (langue IN ('fr', 'en'));
