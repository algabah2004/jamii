-- Module deploiement (#20). L'adresse en ligne d'un projet, déclarée par son équipe et vérifiée par Jamii
-- (contracts/deploiement.yaml). Aucune donnée personnelle : ni auteur, ni contenu de la page vérifiée.
CREATE TABLE deploiement (
	id               UUID PRIMARY KEY,
	-- Module projets : on garde l'identifiant, sans clé étrangère (docs/modele-donnees.md, §1).
	projet_id        UUID NOT NULL,
	url              VARCHAR(2048) NOT NULL CHECK (url LIKE 'https://%'),
	-- Déduit de l'adresse, jamais déclaré par l'équipe.
	hebergeur        VARCHAR(20) NOT NULL CHECK (hebergeur IN ('datacloud', 'autre')),
	statut           VARCHAR(20) NOT NULL CHECK (statut IN ('en_cours', 'en_ligne', 'echec')),
	-- Dernière fois que l'adresse a répondu.
	verifie_le       TIMESTAMPTZ,
	-- Premier passage en_ligne : ne change plus ensuite.
	mis_en_ligne_le  TIMESTAMPTZ,
	dernier_essai_le TIMESTAMPTZ,
	dernier_resultat VARCHAR(30) CHECK (dernier_resultat IN ('repond', 'erreur_serveur', 'delai_depasse',
	                 'connexion_refusee', 'certificat_invalide', 'adresse_refusee', 'nom_inconnu')),
	cree_le          TIMESTAMPTZ NOT NULL,
	UNIQUE (projet_id, url)
);

-- La vitrine et le tableau de bord lisent les déploiements en ligne vérifiés récemment.
CREATE INDEX deploiement_en_ligne ON deploiement (projet_id, verifie_le) WHERE statut = 'en_ligne';
-- La vérification horaire commence par les adresses vérifiées il y a le plus longtemps.
CREATE INDEX deploiement_dernier_essai ON deploiement (dernier_essai_le NULLS FIRST);
