-- Les fichiers joints (images, vidéos, documents) des salons et des publications (contracts/fichiers.yaml, #155 #157).
-- Les métadonnées d'un côté, les octets de l'autre : on liste et on vérifie sans charger le contenu. Le contenu est
-- derrière une interface (StockageFichiers) : il pourra passer au stockage objet de DataCloud sans toucher au reste.
CREATE TABLE fichier (
	id              UUID PRIMARY KEY,
	proprietaire_id UUID REFERENCES utilisateur (id) ON DELETE SET NULL,
	nom             VARCHAR(200) NOT NULL,
	type_mime       VARCHAR(100) NOT NULL,
	sorte           VARCHAR(10) NOT NULL CHECK (sorte IN ('image', 'video', 'document')),
	taille          BIGINT NOT NULL CHECK (taille > 0 AND taille <= 26214400),
	cree_le         TIMESTAMPTZ NOT NULL
);

CREATE INDEX fichier_proprietaire ON fichier (proprietaire_id, cree_le);

CREATE TABLE fichier_contenu (
	fichier_id UUID PRIMARY KEY REFERENCES fichier (id) ON DELETE CASCADE,
	octets     BYTEA NOT NULL
);

-- Les pièces jointes d'un message ou d'une publication, dans l'ordre où on les a jointes (6 au plus, vérifié par l'API).
ALTER TABLE message_cercle ADD COLUMN pieces UUID[] NOT NULL DEFAULT '{}';
ALTER TABLE message_salon ADD COLUMN pieces UUID[] NOT NULL DEFAULT '{}';
ALTER TABLE publication ADD COLUMN pieces UUID[] NOT NULL DEFAULT '{}';

-- Un message peut n'être qu'une image ou un fichier : le texte devient facultatif quand il y a des pièces jointes.
ALTER TABLE message_cercle DROP CONSTRAINT message_cercle_check1;
ALTER TABLE message_cercle ADD CONSTRAINT message_cercle_texte_ou_pieces
	CHECK (type <> 'texte' OR length(btrim(coalesce(texte, ''))) > 0 OR cardinality(pieces) > 0);
ALTER TABLE message_salon DROP CONSTRAINT message_salon_texte_check;
ALTER TABLE message_salon ADD CONSTRAINT message_salon_texte_ou_pieces
	CHECK (length(btrim(texte)) > 0 OR cardinality(pieces) > 0);
