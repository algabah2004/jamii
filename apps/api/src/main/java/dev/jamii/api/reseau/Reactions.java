package dev.jamii.api.reseau;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Réagir à un message avec un emoji (#157) : 👍 3, ❤️ 1… Une réaction par emoji et par personne, 10 emojis différents
 * au plus par personne et par message. Seul un emoji est accepté, jamais du texte.
 */
@Service
class Reactions {

	static final int EMOJIS_PAR_PERSONNE = 10;

	/** Les noms montrés au survol d'une pastille, au plus. */
	static final int NOMS_MAX = 10;

	/**
	 * Un emoji, avec ses éventuels modificateurs (couleur de peau, ZWJ, sélecteur de variante, drapeaux) : rien d'autre.
	 * 8 points de code au plus suffisent pour 👩🏾‍💻 ou 🏳️‍🌈.
	 */
	private static final Pattern EMOJI = Pattern.compile(
			"^(?:[\\p{So}\\p{Sk}\\x{1F1E6}-\\x{1F1FF}\\x{1F3FB}-\\x{1F3FF}]|\\x{200D}|\\x{FE0F}|\\x{20E3}|[\\x{E0020}-\\x{E007F}])+$");

	record Reaction(String emoji, int nombre, boolean deMoi, List<UUID> personnes) {
	}

	private final JdbcClient base;

	Reactions(JdbcClient base) {
		this.base = base;
	}

	static boolean estUnEmoji(String texte) {
		return texte != null && !texte.isBlank() && texte.length() <= 32 && texte.codePointCount(0, texte.length()) <= 8
				&& EMOJI.matcher(texte).matches() && texte.codePoints().anyMatch(c -> c > 0x2000);
	}

	@Transactional
	void changer(UUID messageId, UUID moi, String emoji, boolean active, Instant maintenant) {
		if (!estUnEmoji(emoji)) {
			throw ErreurReseau.validation("emoji", "format_invalide");
		}
		if (!active) {
			base.sql("DELETE FROM reaction_message WHERE message_id = :m AND utilisateur_id = :moi AND emoji = :e")
				.param("m", messageId).param("moi", moi).param("e", emoji).update();
			return;
		}
		long miennes = base.sql("SELECT count(*) FROM reaction_message WHERE message_id = :m AND utilisateur_id = :moi AND emoji <> :e")
			.param("m", messageId).param("moi", moi).param("e", emoji).query(Long.class).single();
		if (miennes >= EMOJIS_PAR_PERSONNE) {
			throw ErreurReseau.conflit("trop_de_reactions", "Dix réactions au plus par message");
		}
		base.sql("""
				INSERT INTO reaction_message (message_id, utilisateur_id, emoji, cree_le) VALUES (:m, :moi, :e, :le)
				ON CONFLICT DO NOTHING
				""").param("m", messageId).param("moi", moi).param("e", emoji).param("le", Timestamp.from(maintenant)).update();
	}

	/** Un message supprimé pour tout le monde perd ses réactions. */
	void effacer(UUID messageId) {
		base.sql("DELETE FROM reaction_message WHERE message_id = :m").param("m", messageId).update();
	}

	/** Les réactions de ces messages, dans l'ordre de la première réaction de chaque emoji. */
	Map<UUID, List<Reaction>> pour(Collection<UUID> messages, UUID moi) {
		Map<UUID, Map<String, List<UUID>>> parMessage = new LinkedHashMap<>();
		if (messages.isEmpty()) {
			return Map.of();
		}
		base.sql("SELECT message_id, emoji, utilisateur_id FROM reaction_message WHERE message_id IN (:ids) ORDER BY cree_le, utilisateur_id")
			.param("ids", messages)
			.query((rs, n) -> {
				parMessage.computeIfAbsent(rs.getObject("message_id", UUID.class), k -> new LinkedHashMap<>())
					.computeIfAbsent(rs.getString("emoji"), k -> new ArrayList<>())
					.add(rs.getObject("utilisateur_id", UUID.class));
				return 0;
			}).list();
		Map<UUID, List<Reaction>> resultat = new LinkedHashMap<>();
		parMessage.forEach((id, emojis) -> resultat.put(id, emojis.entrySet().stream()
			.map(e -> new Reaction(e.getKey(), e.getValue().size(), e.getValue().contains(moi),
					e.getValue().subList(0, Math.min(NOMS_MAX, e.getValue().size()))))
			.toList()));
		return resultat;
	}

}
