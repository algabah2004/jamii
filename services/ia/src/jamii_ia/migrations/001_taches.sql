-- La file de tâches du service IA. Elle vit dans le schéma « ia » : l'API de Jamii ne lit
-- jamais ces tables, elle passe par contracts/ia.yaml.
CREATE TABLE ia.taches (
    id               uuid PRIMARY KEY,
    -- Un nouvel essai réseau de l'appelant ne crée pas une deuxième tâche.
    cle_idempotence  uuid NOT NULL UNIQUE,
    type             text NOT NULL,
    entree           jsonb NOT NULL,
    -- SHA-256 de (type, entrée) : détecte une clé réutilisée, et servira au cache.
    empreinte        text NOT NULL,
    statut           text NOT NULL DEFAULT 'en_attente'
                     CHECK (statut IN ('en_attente', 'en_cours', 'terminee', 'echouee')),
    origine          text CHECK (origine IN ('ia', 'cache', 'repli')),
    resultat         jsonb,
    erreur_code      text,
    essais           integer NOT NULL DEFAULT 0,
    -- Pas avant cette date : sert au délai entre deux essais.
    disponible_le    timestamptz NOT NULL DEFAULT now(),
    cree_le          timestamptz NOT NULL DEFAULT now(),
    termine_le       timestamptz
);

-- Le travailleur ne parcourt que les tâches à traiter, dans l'ordre.
CREATE INDEX taches_a_traiter ON ia.taches (disponible_le) WHERE statut = 'en_attente';
-- Limite globale : compter les tâches de la dernière heure.
CREATE INDEX taches_cree_le ON ia.taches (cree_le);
