package dev.jamii.api.fichiers;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Les fichiers joints (contracts/fichiers.yaml) : on les reçoit, on vérifie leur vrai type d'après leurs premiers
 * octets (jamais d'après le nom ni le type annoncé par le navigateur), et on les garde. Les autres modules (salons,
 * publications) n'en gardent que l'identifiant, et appellent {@link #verifierPieces} pour vérifier et décrire leurs pièces jointes.
 * <p>
 * Refusés : tout ce qui pourrait s'exécuter dans le navigateur (HTML, SVG, JavaScript) ou ailleurs (exécutables).
 */
@Service
public class Fichiers {

	/** Ce qu'un message ou une publication montre d'une pièce jointe. `url` : à lire avec la session. */
	public record Piece(UUID id, String nom, String type, String sorte, long taille, String url) {
	}

	public static final int PIECES_MAX = 6;

	static final long IMAGE_MAX = 10L * 1024 * 1024;

	static final long DOCUMENT_MAX = 10L * 1024 * 1024;

	static final long VIDEO_MAX = 25L * 1024 * 1024;

	/** Un message vocal : environ 5 minutes en Opus à 128 kbit/s, bien plus que ce que l'enregistreur permet (3 min). */
	static final long AUDIO_MAX = 5L * 1024 * 1024;

	static final int ENVOIS_PAR_HEURE = 60;

	record Contenu(Piece piece, byte[] octets, boolean prive) {
	}

	/** Un type reconnu : son type MIME et sa sorte. */
	record Type(String mime, String sorte) {
	}

	private final JdbcClient base;

	private final StockageFichiers stockage;

	Fichiers(JdbcClient base, StockageFichiers stockage) {
		this.base = base;
		this.stockage = stockage;
	}

	@Transactional
	Piece enregistrer(UUID proprietaire, String nomEnvoye, byte[] octets, Instant maintenant) {
		return enregistrer(proprietaire, nomEnvoye, octets, maintenant, null);
	}

	@Transactional
	Piece enregistrer(UUID proprietaire, String nomEnvoye, byte[] octets, Instant maintenant, UUID destinataire) {
		return enregistrer(proprietaire, nomEnvoye, octets, maintenant, destinataire, null);
	}

	@Transactional
	Piece enregistrer(UUID proprietaire, String nomEnvoye, byte[] octets, Instant maintenant, UUID destinataire, UUID cercle) {
		return enregistrer(proprietaire, nomEnvoye, octets, maintenant, destinataire, cercle, false);
	}

	/**
	 * Avec {@code vocal}, le fichier est un message vocal enregistré dans le navigateur : WebM ou Ogg (Chrome, Firefox,
	 * Android) ou MP4 (Safari, iPhone), servi comme un son. Sans lui, un WebM ou un MP4 reste une vidéo.
	 */
	@Transactional
	Piece enregistrer(UUID proprietaire, String nomEnvoye, byte[] octets, Instant maintenant, UUID destinataire, UUID cercle,
			boolean vocal) {
		if (cercle != null) {
			if (destinataire != null) throw new ErreurFichier(400, "destination_invalide", "Une seule destination est possible");
			if (!base.sql("SELECT EXISTS (SELECT 1 FROM cercle_membre WHERE cercle_id = :c AND utilisateur_id = :u)")
				.param("c", cercle).param("u", proprietaire).query(Boolean.class).single()) {
				throw new ErreurFichier(403, "interdit", "Réservé aux membres du cercle");
			}
		}
		if (destinataire != null && (destinataire.equals(proprietaire) || !base.sql("SELECT EXISTS (SELECT 1 FROM utilisateur WHERE id = :id)")
				.param("id", destinataire).query(Boolean.class).single())) {
			throw new ErreurFichier(404, "introuvable", "Destinataire introuvable");
		}
		Type type = (vocal ? reconnaitreVocal(octets) : reconnaitre(octets, nomEnvoye)).orElseThrow(() -> new ErreurFichier(415, "type_refuse",
				vocal ? "Ce message vocal n'est pas un son WebM, Ogg ou MP4"
						: "Ce type de fichier n'est pas accepté : images, vidéos, PDF, documents texte, archives ZIP"));
		long max = switch (type.sorte()) {
			case "video" -> VIDEO_MAX;
			case "image" -> IMAGE_MAX;
			case "audio" -> AUDIO_MAX;
			default -> DOCUMENT_MAX;
		};
		if (octets.length == 0 || octets.length > max) {
			throw new ErreurFichier(413, "trop_gros", "Ce fichier dépasse " + (max / 1024 / 1024) + " Mo");
		}
		UUID id = UUID.randomUUID();
		String nom = nomPropre(nomEnvoye);
		base.sql("""
				INSERT INTO fichier (id, proprietaire_id, nom, type_mime, sorte, taille, cree_le, conversation_avec, cercle_id)
				VALUES (:id, :p, :nom, :mime, :sorte, :taille, :t, :destinataire, :cercle)
				""")
			.param("id", id).param("p", proprietaire).param("nom", nom).param("mime", type.mime()).param("sorte", type.sorte())
			.param("taille", (long) octets.length).param("t", Timestamp.from(maintenant))
			.param("destinataire", destinataire, java.sql.Types.OTHER).param("cercle", cercle, java.sql.Types.OTHER).update();
		stockage.ecrire(id, octets);
		return new Piece(id, nom, type.mime(), type.sorte(), octets.length, url(id));
	}

	Optional<Contenu> lire(UUID id, UUID moi) {
		var acces = base.sql("""
				SELECT (conversation_avec IS NOT NULL OR cercle_id IS NOT NULL) AS prive FROM fichier f
				WHERE f.id = :id AND ((f.conversation_avec IS NULL AND f.cercle_id IS NULL) OR f.proprietaire_id = :moi OR
				  (f.conversation_avec = :moi AND EXISTS (SELECT 1 FROM message m WHERE f.id = ANY(m.pieces)
				    AND m.expediteur_id = f.proprietaire_id AND m.destinataire_id = f.conversation_avec)) OR
				  (f.cercle_id IS NOT NULL AND EXISTS (SELECT 1 FROM cercle_membre m WHERE m.cercle_id = f.cercle_id AND m.utilisateur_id = :moi)
				    AND EXISTS (SELECT 1 FROM cercle_membre m WHERE m.cercle_id = f.cercle_id AND m.cahier_charges_id = f.id)))
				""").param("id", id).param("moi", moi).query((rs, n) -> rs.getBoolean("prive")).optional();
		if (acces.isEmpty()) return Optional.empty();
		return decrire(List.of(id)).stream().findFirst().flatMap(p -> stockage.lire(id).map(o -> new Contenu(p, o, acces.get())));
	}

	long attenteAvantEnvoi(UUID proprietaire, Instant maintenant) {
		List<Instant> recents = base.sql("SELECT cree_le FROM fichier WHERE proprietaire_id = :p AND cree_le > :d ORDER BY cree_le")
			.param("p", proprietaire).param("d", Timestamp.from(maintenant.minus(Duration.ofHours(1))))
			.query((rs, n) -> rs.getTimestamp("cree_le").toInstant()).list();
		if (recents.size() < ENVOIS_PAR_HEURE) {
			return 0;
		}
		return Math.max(1, Duration.between(maintenant, recents.get(recents.size() - ENVOIS_PAR_HEURE).plus(Duration.ofHours(1))).toSeconds());
	}

	/**
	 * Vérifie les pièces jointes d'un message qu'on écrit : 6 au plus, sans doublon, et toutes envoyées par son auteur
	 * (on ne joint pas le fichier de quelqu'un d'autre). Renvoie les identifiants dans l'ordre, ou lève une erreur 400.
	 */
	public List<UUID> verifierPieces(Collection<UUID> ids, UUID auteur) {
		return verifierPieces(ids, auteur, null, null);
	}

	/** Un fichier privé ne peut ni changer de destinataire, ni être joint à un salon public. */
	public List<UUID> verifierPiecesPrivees(Collection<UUID> ids, UUID auteur, UUID destinataire) {
		return verifierPieces(ids, auteur, destinataire, null);
	}

	public List<UUID> verifierPiecesCercle(Collection<UUID> ids, UUID auteur, UUID cercle) {
		return verifierPieces(ids, auteur, null, cercle);
	}

	private List<UUID> verifierPieces(Collection<UUID> ids, UUID auteur, UUID destinataire, UUID cercle) {
		if (ids == null || ids.isEmpty()) {
			return List.of();
		}
		List<UUID> uniques = new ArrayList<>(new java.util.LinkedHashSet<>(ids));
		if (uniques.size() > PIECES_MAX) {
			throw new ErreurFichier(400, "trop_de_pieces", "Au plus " + PIECES_MAX + " pièces jointes");
		}
		long miennes = base.sql("SELECT count(*) FROM fichier WHERE id IN (:ids) AND proprietaire_id = :a AND conversation_avec IS NOT DISTINCT FROM :destinataire AND cercle_id IS NOT DISTINCT FROM :cercle")
			.param("ids", uniques).param("a", auteur).param("destinataire", destinataire, java.sql.Types.OTHER).param("cercle", cercle, java.sql.Types.OTHER).query(Long.class).single();
		if (miennes != uniques.size()) {
			throw new ErreurFichier(400, "piece_inconnue", "Une pièce jointe est introuvable");
		}
		return uniques;
	}

	/** Décrit des pièces jointes, dans l'ordre demandé (celles qui n'existent plus sont omises). */
	public List<Piece> decrire(Collection<UUID> ids) {
		if (ids == null || ids.isEmpty()) {
			return List.of();
		}
		Map<UUID, Piece> trouvees = new LinkedHashMap<>();
		base.sql("SELECT id, nom, type_mime, sorte, taille FROM fichier WHERE id IN (:ids)").param("ids", ids)
			.query((rs, n) -> {
				UUID id = rs.getObject("id", UUID.class);
				return new Piece(id, rs.getString("nom"), rs.getString("type_mime"), rs.getString("sorte"), rs.getLong("taille"), url(id));
			}).list().forEach(p -> trouvees.put(p.id(), p));
		return ids.stream().map(trouvees::get).filter(p -> p != null).toList();
	}

	/** Un tableau PostgreSQL de UUID écrit en texte (`{a,b}`), à passer avec `CAST(:x AS UUID[])`. */
	public static String tableau(List<UUID> ids) {
		return "{" + String.join(",", ids.stream().map(UUID::toString).toList()) + "}";
	}

	/** Lit une colonne `UUID[]`. */
	public static List<UUID> lireTableau(java.sql.ResultSet rs, String colonne) throws java.sql.SQLException {
		java.sql.Array a = rs.getArray(colonne);
		if (a == null) {
			return List.of();
		}
		return java.util.Arrays.stream((Object[]) a.getArray()).map(v -> v instanceof UUID u ? u : UUID.fromString(v.toString())).toList();
	}

	/** Où lire un fichier, pour un autre module (la couverture d'une communauté). */
	public static String adresse(UUID id) {
		return url(id);
	}

	/**
	 * Une image qu'on peut montrer à toute personne connectée : envoyée par `auteur`, de sorte `image`, ni réservée à
	 * une conversation, ni à un cercle. Pour une couverture de communauté.
	 */
	public boolean estImagePublique(UUID id, UUID auteur) {
		return base.sql("""
				SELECT EXISTS (SELECT 1 FROM fichier WHERE id = :id AND proprietaire_id = :a AND sorte = 'image'
				  AND conversation_avec IS NULL AND cercle_id IS NULL)
				""").param("id", id).param("a", auteur).query(Boolean.class).single();
	}

	static String url(UUID id) {
		return "/api/fichiers/" + id;
	}

	/** Le nom tel qu'on le montre : sans chemin ni caractère de contrôle, 200 caractères au plus. */
	static String nomPropre(String nom) {
		String n = nom == null ? "" : nom.replace('\\', '/');
		n = n.substring(n.lastIndexOf('/') + 1).replaceAll("[\\p{Cntrl}\"<>]", "").strip();
		if (n.isEmpty()) {
			n = "fichier";
		}
		return n.length() > 200 ? n.substring(n.length() - 200) : n;
	}

	// ---------------------------------------------------------------- le vrai type, d'après les octets

	/** Un message vocal, d'après ses octets : ce que MediaRecorder produit selon le navigateur. */
	static Optional<Type> reconnaitreVocal(byte[] o) {
		if (commence(o, 0, 0x1A, 0x45, 0xDF, 0xA3)) return Optional.of(new Type("audio/webm", "audio"));
		if (commence(o, 0, 'O', 'g', 'g', 'S')) return Optional.of(new Type("audio/ogg", "audio"));
		if (commence(o, 4, 'f', 't', 'y', 'p')) {
			String marque = o.length >= 12 ? new String(o, 8, 4, StandardCharsets.US_ASCII) : "";
			if (marque.startsWith("qt") || marque.startsWith("hei") || marque.startsWith("mif1") || marque.startsWith("avif")) return Optional.empty();
			return Optional.of(new Type("audio/mp4", "audio"));
		}
		return Optional.empty();
	}

	static Optional<Type> reconnaitre(byte[] o, String nom) {
		if (commence(o, 0, 0x89, 'P', 'N', 'G')) return Optional.of(new Type("image/png", "image"));
		if (commence(o, 0, 0xFF, 0xD8, 0xFF)) return Optional.of(new Type("image/jpeg", "image"));
		if (commence(o, 0, 'G', 'I', 'F', '8')) return Optional.of(new Type("image/gif", "image"));
		if (commence(o, 0, 'R', 'I', 'F', 'F') && commence(o, 8, 'W', 'E', 'B', 'P')) return Optional.of(new Type("image/webp", "image"));
		if (commence(o, 4, 'f', 't', 'y', 'p')) {
			String marque = o.length >= 12 ? new String(o, 8, 4, StandardCharsets.US_ASCII) : "";
			if (marque.startsWith("qt")) return Optional.of(new Type("video/quicktime", "video"));
			if (marque.startsWith("hei") || marque.startsWith("mif1") || marque.startsWith("avif")) return Optional.empty();
			return Optional.of(new Type("video/mp4", "video"));
		}
		if (commence(o, 0, 0x1A, 0x45, 0xDF, 0xA3)) return Optional.of(new Type("video/webm", "video"));
		if (commence(o, 0, '%', 'P', 'D', 'F', '-')) return Optional.of(new Type("application/pdf", "document"));
		if (commence(o, 0, 'P', 'K', 0x03, 0x04)) {
			String n = nom == null ? "" : nom.toLowerCase(java.util.Locale.ROOT);
			if (n.endsWith(".docx")) return Optional.of(new Type("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "document"));
			if (n.endsWith(".xlsx")) return Optional.of(new Type("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "document"));
			if (n.endsWith(".pptx")) return Optional.of(new Type("application/vnd.openxmlformats-officedocument.presentationml.presentation", "document"));
			return Optional.of(new Type("application/zip", "document"));
		}
		if (texteSimple(o, nom)) return Optional.of(new Type("text/plain;charset=utf-8", "document"));
		return Optional.empty();
	}

	/** Un fichier texte (.txt, .md, .csv, .json, code) : de l'UTF-8 lisible, sans balise HTML. Servi en text/plain. */
	private static boolean texteSimple(byte[] o, String nom) {
		String n = nom == null ? "" : nom.toLowerCase(java.util.Locale.ROOT);
		if (!n.matches(".*\\.(txt|md|csv|json|log|ya?ml|java|kt|py|js|ts|vue|dart|go|rs|sql|sh)$")) {
			return false;
		}
		var decodeur = StandardCharsets.UTF_8.newDecoder();
		try {
			String texte = decodeur.decode(java.nio.ByteBuffer.wrap(o, 0, Math.min(o.length, 64 * 1024))).toString();
			return texte.chars().noneMatch(c -> c == 0);
		}
		catch (java.nio.charset.CharacterCodingException e) {
			// Coupé au milieu d'un caractère : on regarde un peu moins loin.
			return o.length > 64 * 1024;
		}
	}

	private static boolean commence(byte[] o, int decalage, int... attendu) {
		if (o.length < decalage + attendu.length) {
			return false;
		}
		for (int i = 0; i < attendu.length; i++) {
			if ((o[decalage + i] & 0xFF) != attendu[i]) {
				return false;
			}
		}
		return true;
	}

}
