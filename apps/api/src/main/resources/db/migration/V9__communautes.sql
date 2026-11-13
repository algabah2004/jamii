-- Communautés et entraide (docs/modele-donnees.md, contracts/communautes.yaml). Le signalement arrive avec sa route.

-- Une antenne locale est une communauté qui a un pays (réponse à la question ouverte de modele-donnees.md).
CREATE TABLE communaute (
	id          UUID PRIMARY KEY,
	slug        VARCHAR(60) NOT NULL UNIQUE CHECK (slug ~ '^[a-z0-9]+(-[a-z0-9]+)*$'),
	nom         VARCHAR(80) NOT NULL,
	description VARCHAR(500) NOT NULL,
	theme       VARCHAR(40) NOT NULL,
	pays        CHAR(2) CHECK (pays ~ '^[A-Z]{2}$'),
	cree_le     TIMESTAMPTZ NOT NULL
);

-- Supprimer un compte supprime ses adhésions (donnée personnelle).
CREATE TABLE adhesion (
	communaute_id  UUID NOT NULL REFERENCES communaute (id) ON DELETE CASCADE,
	utilisateur_id UUID NOT NULL REFERENCES utilisateur (id) ON DELETE CASCADE,
	role           VARCHAR(12) NOT NULL DEFAULT 'membre' CHECK (role IN ('membre', 'moderateur')),
	rejoint_le     TIMESTAMPTZ NOT NULL,
	PRIMARY KEY (communaute_id, utilisateur_id)
);

CREATE INDEX adhesion_utilisateur ON adhesion (utilisateur_id);

-- Supprimer un compte garde ses publications, sans auteur (« compte supprimé ») : les réponses des autres restent lisibles.
CREATE TABLE publication (
	id                  UUID PRIMARY KEY,
	communaute_id       UUID NOT NULL REFERENCES communaute (id) ON DELETE CASCADE,
	auteur_id           UUID REFERENCES utilisateur (id) ON DELETE SET NULL,
	type                VARCHAR(20) NOT NULL CHECK (type IN ('question', 'discussion', 'montre_ton_projet')),
	titre               VARCHAR(150) NOT NULL,
	contenu             TEXT NOT NULL CHECK (length(contenu) <= 10000),
	-- Pas de clé étrangère : la table des projets appartient à un autre module (règle commune n° 4).
	projet_id           UUID,
	-- Pour une question seulement : où en est la réponse proposée par l'IA.
	proposition_ia      VARCHAR(12) CHECK (proposition_ia IN ('en_attente', 'prete', 'indisponible')),
	reponse_acceptee_id UUID,
	acceptee_le         TIMESTAMPTZ,
	cree_le             TIMESTAMPTZ NOT NULL,
	version             INTEGER NOT NULL DEFAULT 0,
	CHECK ((type = 'montre_ton_projet') = (projet_id IS NOT NULL)),
	CHECK ((type = 'question') = (proposition_ia IS NOT NULL))
);

-- Pagination par curseur (cree_le, id), et limite de publications par heure et par auteur.
CREATE INDEX publication_communaute_recentes ON publication (communaute_id, cree_le DESC, id DESC);
CREATE INDEX publication_auteur_recentes ON publication (auteur_id, cree_le DESC);

-- auteur_id vide : réponse de l'IA (proposee_par_ia), ou compte supprimé.
CREATE TABLE reponse (
	id             UUID PRIMARY KEY,
	publication_id UUID NOT NULL REFERENCES publication (id) ON DELETE CASCADE,
	auteur_id      UUID REFERENCES utilisateur (id) ON DELETE SET NULL,
	contenu        TEXT NOT NULL CHECK (length(contenu) <= 10000),
	proposee_par_ia BOOLEAN NOT NULL DEFAULT FALSE,
	validee_par_id UUID REFERENCES utilisateur (id) ON DELETE SET NULL,
	cree_le        TIMESTAMPTZ NOT NULL,
	CHECK (NOT proposee_par_ia OR auteur_id IS NULL)
);

CREATE INDEX reponse_publication ON reponse (publication_id, cree_le);

ALTER TABLE publication ADD CONSTRAINT publication_reponse_acceptee
	FOREIGN KEY (reponse_acceptee_id) REFERENCES reponse (id) ON DELETE SET NULL;

-- Les communautés de départ : des thèmes utiles partout sur le continent, et des antennes dans plusieurs régions.
INSERT INTO communaute (id, slug, nom, description, theme, pays, cree_le) VALUES
	(gen_random_uuid(), 'flutter', 'Flutter et Dart', 'Applications mobiles qui marchent aussi avec peu de réseau.', 'flutter', NULL, now()),
	(gen_random_uuid(), 'fintech', 'Fintech et paiement mobile', 'Mobile Money, paiements marchands, conformité.', 'fintech', NULL, now()),
	(gen_random_uuid(), 'ia', 'IA appliquée', 'Des modèles utiles, dans nos langues et sur nos données.', 'ia', NULL, now()),
	(gen_random_uuid(), 'web', 'Web et API', 'Sites rapides et API robustes, même sur une connexion lente.', 'web', NULL, now()),
	(gen_random_uuid(), 'cloud', 'Cloud et DevOps', 'Mettre en ligne, surveiller et tenir la charge.', 'cloud', NULL, now()),
	(gen_random_uuid(), 'antenne-dakar', 'Antenne Dakar', 'Les devs de Dakar : rencontres, entraide et projets locaux.', 'antenne', 'SN', now()),
	(gen_random_uuid(), 'antenne-abidjan', 'Antenne Abidjan', 'Les devs d''Abidjan : rencontres, entraide et projets locaux.', 'antenne', 'CI', now()),
	(gen_random_uuid(), 'antenne-lagos', 'Antenne Lagos', 'Les devs de Lagos : rencontres, entraide et projets locaux.', 'antenne', 'NG', now()),
	(gen_random_uuid(), 'antenne-nairobi', 'Antenne Nairobi', 'Les devs de Nairobi : rencontres, entraide et projets locaux.', 'antenne', 'KE', now());
