-- Le webhook vers l'outil de suivi de Systalink (contracts/systalink.yaml, webhooks.clientSuivi) : envoyé une seule
-- fois par demande, quand l'accord est donné ET que l'orientation est terminée.
ALTER TABLE demande ADD COLUMN webhook_envoye_le TIMESTAMPTZ;
