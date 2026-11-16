package dev.jamii.api.reseau;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Les serveurs ICE donnés au navigateur pour un appel (#157) : un STUN pour trouver son adresse publique, et notre
 * relais TURN (coturn) quand les deux personnes ne peuvent pas se joindre directement (NAT, réseau d'entreprise, 4G).
 *
 * <p>Jamais de mot de passe TURN fixe envoyé au navigateur : des identifiants temporaires, selon le mécanisme « TURN
 * REST API » que coturn comprend avec {@code use-auth-secret}. Le nom est {@code <expiration>:<utilisateur>}, le mot de
 * passe est {@code base64(HMAC-SHA1(secret, nom))}. coturn refuse le nom une fois l'expiration passée, et ne peut
 * pas être utilisé par quelqu'un qui n'a pas de session Jamii. Sans secret configuré : STUN seulement (les appels
 * passent sur la plupart des réseaux, pas tous).
 */
@Component
class ServeursIce {

	/** La durée de vie d'un identifiant TURN : assez pour un long appel, pas assez pour servir de relais gratuit. */
	static final Duration DUREE_IDENTIFIANT = Duration.ofHours(4);

	record ServeurIce(List<String> urls, String username, String credential) {
	}

	private final List<String> stun;

	private final List<String> turn;

	private final String secret;

	private final Clock horloge;

	@org.springframework.beans.factory.annotation.Autowired
	ServeursIce(@Value("${jamii.appels.stun:stun:stun.l.google.com:19302}") String stun,
			@Value("${jamii.appels.turn.urls:}") String turn, @Value("${jamii.appels.turn.secret:}") String secret) {
		this(liste(stun), liste(turn), secret, Clock.systemUTC());
	}

	ServeursIce(List<String> stun, List<String> turn, String secret, Clock horloge) {
		this.stun = stun;
		this.turn = turn;
		this.secret = secret == null ? "" : secret.strip();
		this.horloge = horloge;
	}

	boolean relaisConfigure() {
		return !turn.isEmpty() && !secret.isEmpty();
	}

	List<ServeurIce> pour(UUID utilisateur) {
		List<ServeurIce> serveurs = new ArrayList<>();
		if (!stun.isEmpty()) {
			serveurs.add(new ServeurIce(stun, null, null));
		}
		if (relaisConfigure()) {
			long expiration = horloge.instant().plus(DUREE_IDENTIFIANT).getEpochSecond();
			String nom = expiration + ":" + utilisateur;
			serveurs.add(new ServeurIce(turn, nom, signer(nom)));
		}
		return serveurs;
	}

	String signer(String nom) {
		try {
			Mac mac = Mac.getInstance("HmacSHA1");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
			return Base64.getEncoder().encodeToString(mac.doFinal(nom.getBytes(StandardCharsets.UTF_8)));
		}
		catch (GeneralSecurityException e) {
			throw new IllegalStateException("HMAC-SHA1 indisponible", e);
		}
	}

	private static List<String> liste(String valeur) {
		if (valeur == null || valeur.isBlank()) {
			return List.of();
		}
		return Arrays.stream(valeur.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
	}

}
