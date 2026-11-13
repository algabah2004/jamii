package dev.jamii.api.demandes;

import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import tools.jackson.databind.json.JsonMapper;

/**
 * Le webhook signé vers l'outil de suivi de Systalink (contracts/systalink.yaml, `webhooks.clientSuivi`). Sans
 * `SYSTALINK_WEBHOOK_URL`, rien n'est envoyé. Une seule fois par demande, quand l'accord est donné ET l'orientation
 * terminée ; envoyé en arrière-plan, 3 essais au plus : un échec ne bloque jamais la personne.
 * <p>
 * Signature : `X-Jamii-Signature: sha256=` + HMAC-SHA256 hexadécimal de `<horodatage>.<corps>` avec
 * `SYSTALINK_WEBHOOK_SECRET`, et `X-Jamii-Horodatage` en secondes, pour que Systalink refuse un rejeu.
 */
@Component
class WebhookSystalink {

	private static final Logger journal = LoggerFactory.getLogger(WebhookSystalink.class);

	private final JdbcClient base;

	private final InfosSystalink infos;

	private final JsonMapper json;

	private final Clock horloge;

	private final String url;

	private final String secret;

	private final RestClient client;

	WebhookSystalink(JdbcClient base, InfosSystalink infos, JsonMapper json, ObjectProvider<Clock> horloge,
			@Value("${jamii.clients-systalink.webhook-url:}") String url, @Value("${jamii.clients-systalink.webhook-secret:}") String secret) {
		this.base = base;
		this.infos = infos;
		this.json = json;
		this.horloge = horloge.getIfAvailable(Clock::systemUTC);
		this.url = url == null ? "" : url.strip();
		this.secret = secret == null ? "" : secret;
		if (!this.url.isEmpty() && (!sure(this.url) || this.secret.length() < 16)) {
			throw new IllegalStateException(
					"SYSTALINK_WEBHOOK_URL doit être en https et SYSTALINK_WEBHOOK_SECRET faire au moins 16 caractères");
		}
		var transport = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
		transport.setReadTimeout(Duration.ofSeconds(5));
		this.client = RestClient.builder().requestFactory(transport).build();
	}

	/** https, ou une adresse locale pour les tests. */
	private static boolean sure(String adresse) {
		return adresse.startsWith("https://") || adresse.startsWith("http://127.0.0.1:") || adresse.startsWith("http://localhost:");
	}

	/** À appeler après tout changement d'accord ou d'orientation. Ne fait rien si ce n'est pas prêt ou déjà envoyé. */
	void signalerSiPret(UUID demandeId) {
		if (url.isEmpty()) {
			return;
		}
		// Réservation atomique : deux lectures simultanées n'envoient jamais deux fois.
		int reserve = base.sql("""
				UPDATE demande SET webhook_envoye_le = now()
				WHERE id = :id AND offres_systalink AND orientation IS NOT NULL AND webhook_envoye_le IS NULL
				""").param("id", demandeId).update();
		if (reserve == 1) {
			infos.client(demandeId).ifPresent(client -> Thread.ofVirtual().start(() -> envoyer(json.writeValueAsString(client))));
		}
	}

	void envoyer(String corps) {
		for (int essai = 1; essai <= 3; essai++) {
			String horodatage = String.valueOf(horloge.instant().getEpochSecond());
			try {
				client.post().uri(url).contentType(MediaType.APPLICATION_JSON)
					.header("X-Jamii-Horodatage", horodatage)
					.header("X-Jamii-Signature", "sha256=" + signer(horodatage + "." + corps))
					.body(corps).retrieve().toBodilessEntity();
				return;
			}
			catch (RestClientException e) {
				// Jamais le contenu (il contient un contact) : seulement le type de l'erreur.
				journal.warn("Webhook Systalink, essai {} : {}", essai, e.getClass().getSimpleName());
				try {
					Thread.sleep(Duration.ofSeconds(2L * essai));
				}
				catch (InterruptedException interrompu) {
					Thread.currentThread().interrupt();
					return;
				}
			}
		}
	}

	String signer(String texte) {
		try {
			Mac mac = Mac.getInstance("HmacSHA256");
			mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
			return HexFormat.of().formatHex(mac.doFinal(texte.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException | InvalidKeyException e) {
			throw new IllegalStateException(e);
		}
	}

}
