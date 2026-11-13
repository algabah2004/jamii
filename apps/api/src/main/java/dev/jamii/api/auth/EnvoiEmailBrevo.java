package dev.jamii.api.auth;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Envoi par l'API HTTP de Brevo (`EMAIL_FOURNISSEUR=brevo`) : 300 e-mails par jour sans frais, sans carte bancaire, et
 * une adresse d'expéditeur vérifiée suffit (pas besoin de nom de domaine). Pas de SMTP : aucune dépendance de plus, donc
 * rien à vérifier côté licences (règlement CADEV, article 6).
 * <p>
 * La clé (`BREVO_API_KEY`) ne sort jamais de l'API. En cas d'échec, l'exception remonte à {@link LiensEmail}, qui la
 * journalise sans l'adresse ni le lien.
 */
@Component
@ConditionalOnProperty(name = "jamii.email.fournisseur", havingValue = "brevo")
class EnvoiEmailBrevo implements EnvoiEmail {

	private static final Pattern ADRESSE = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

	private final RestClient client;

	private final String expediteur;

	private final String nomExpediteur;

	EnvoiEmailBrevo(@Value("${jamii.email.brevo.url:https://api.brevo.com/v3}") String url,
			@Value("${jamii.email.brevo.cle:}") String cle, @Value("${jamii.email.expediteur:}") String expediteur,
			@Value("${jamii.email.nom-expediteur:Jamii}") String nomExpediteur) {
		if (cle == null || cle.isBlank() || expediteur == null || !ADRESSE.matcher(expediteur).matches()) {
			throw new IllegalStateException("EMAIL_FOURNISSEUR=brevo demande BREVO_API_KEY et EMAIL_EXPEDITEUR");
		}
		Duration delai = Duration.ofSeconds(5);
		var transport = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(delai).build());
		transport.setReadTimeout(delai);
		this.client = RestClient.builder()
			.baseUrl(url)
			.requestFactory(transport)
			.defaultHeader("api-key", cle)
			.defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
			.build();
		this.expediteur = expediteur;
		this.nomExpediteur = nomExpediteur == null || nomExpediteur.isBlank() ? "Jamii" : nomExpediteur;
	}

	@Override
	public void envoyer(String destinataire, String objet, String texte) {
		client.post()
			.uri("/smtp/email")
			.contentType(MediaType.APPLICATION_JSON)
			.body(Map.of("sender", Map.of("name", nomExpediteur, "email", expediteur), "to",
					List.of(Map.of("email", destinataire)), "subject", objet, "textContent", texte))
			.retrieve()
			.toBodilessEntity();
	}

}
