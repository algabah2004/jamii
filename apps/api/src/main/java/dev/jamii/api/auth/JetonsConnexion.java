package dev.jamii.api.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Les jetons des liens de connexion (docs/modele-donnees.md, `jeton_connexion`) : 15 minutes, usage unique, hachés. */
@Service
class JetonsConnexion {

	static final Duration VALIDITE = Duration.ofMinutes(15);

	private static final Duration CONSERVATION = Duration.ofHours(24);

	private final SecureRandom hasard = new SecureRandom();

	private final JdbcClient base;

	JetonsConnexion(JdbcClient base) {
		this.base = base;
	}

	/** Crée un jeton pour cette adresse (déjà normalisée) et le renvoie en clair : c'est la seule fois qu'on le voit. */
	@Transactional
	String creer(String email, Instant maintenant) {
		byte[] octets = new byte[32];
		hasard.nextBytes(octets);
		String jeton = Base64.getUrlEncoder().withoutPadding().encodeToString(octets);
		base.sql("""
				INSERT INTO jeton_connexion (id, email, empreinte, expire_le, cree_le)
				VALUES (:id, :email, :empreinte, :expire, :cree)
				""")
			.param("id", UUID.randomUUID())
			.param("email", email)
			.param("empreinte", empreinte(jeton))
			.param("expire", Timestamp.from(maintenant.plus(VALIDITE)))
			.param("cree", Timestamp.from(maintenant))
			.update();
		return jeton;
	}

	/**
	 * Consomme le jeton et renvoie l'adresse qu'il prouve. Un seul UPDATE conditionnel : de deux confirmations
	 * simultanées, une seule réussit. Inconnu, déjà utilisé ou expiré : même résultat vide, on ne précise pas.
	 */
	@Transactional
	Optional<String> consommer(String jeton, Instant maintenant) {
		return base.sql("""
				UPDATE jeton_connexion SET utilise_le = :maintenant
				WHERE empreinte = :empreinte AND utilise_le IS NULL AND expire_le > :maintenant
				RETURNING email
				""")
			.param("empreinte", empreinte(jeton))
			.param("maintenant", Timestamp.from(maintenant))
			.query(String.class)
			.optional();
	}

	long demandesRecentes(String email, Instant depuis) {
		return base.sql("SELECT count(*) FROM jeton_connexion WHERE email = :email AND cree_le > :depuis")
			.param("email", email)
			.param("depuis", Timestamp.from(depuis))
			.query(Long.class)
			.single();
	}

	/** Les adresses sont des données personnelles : on ne les garde pas plus de 24 h. */
	@Scheduled(fixedDelayString = "PT1H", initialDelayString = "PT5M")
	void nettoyer() {
		supprimerAvant(Instant.now().minus(CONSERVATION));
	}

	int supprimerAvant(Instant limite) {
		return base.sql("DELETE FROM jeton_connexion WHERE cree_le < :limite").param("limite", Timestamp.from(limite)).update();
	}

	static String empreinte(String jeton) {
		try {
			byte[] hachage = MessageDigest.getInstance("SHA-256").digest(jeton.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(hachage);
		}
		catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

}
