package dev.jamii.api.deploiement;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

import org.springframework.stereotype.Component;

/**
 * La vérification réelle, protégée contre la SSRF (contracts/deploiement.yaml) :
 * <ul>
 * <li>toutes les IP du nom doivent être publiques, sinon on n'appelle rien ;</li>
 * <li>on se connecte à l'IP vérifiée, pas au nom : un changement de DNS entre le contrôle et l'appel (DNS rebinding)
 * ne peut pas rediriger vers un réseau interne. Le certificat est quand même vérifié pour le nom ;</li>
 * <li>une seule requête GET, sans suivre les redirections, et on ne lit que la ligne de statut.</li>
 * </ul>
 * On écrit la requête HTTP à la main parce que le client HTTP de Java ne permet pas de choisir l'IP de connexion.
 */
@Component
class VerificateurHttps implements Verificateur {

	static final int DELAI_MS = 2_500;

	private static final int LIGNE_STATUT_MAX = 256;

	private final AdressesPubliques.Resolveur resolveur;

	private final SSLSocketFactory fabriqueTls;

	VerificateurHttps() {
		this(AdressesPubliques.Resolveur.SYSTEME, (SSLSocketFactory) SSLSocketFactory.getDefault());
	}

	VerificateurHttps(AdressesPubliques.Resolveur resolveur, SSLSocketFactory fabriqueTls) {
		this.resolveur = resolveur;
		this.fabriqueTls = fabriqueTls;
	}

	@Override
	public Resultat verifier(URI adresse) {
		String nom = adresse.getHost();
		InetAddress[] ips;
		try {
			ips = resolveur.resoudre(nom);
		}
		catch (UnknownHostException e) {
			return Resultat.NOM_INCONNU;
		}
		if (ips.length == 0 || !Arrays.stream(ips).allMatch(AdressesPubliques::estPublique)) {
			return Resultat.ADRESSE_REFUSEE;
		}
		try (Socket brut = new Socket()) {
			brut.connect(new InetSocketAddress(ips[0], 443), DELAI_MS);
			brut.setSoTimeout(DELAI_MS);
			try (SSLSocket tls = (SSLSocket) fabriqueTls.createSocket(brut, nom, 443, true)) {
				SSLParameters parametres = tls.getSSLParameters();
				parametres.setEndpointIdentificationAlgorithm("HTTPS");
				if (!estUneIp(nom)) {
					parametres.setServerNames(List.of(new SNIHostName(nom)));
				}
				tls.setSSLParameters(parametres);
				tls.startHandshake();
				envoyer(tls.getOutputStream(), adresse, nom);
				int code = codeHttp(tls.getInputStream());
				return code > 0 && code < 500 ? Resultat.REPOND : Resultat.ERREUR_SERVEUR;
			}
		}
		catch (SocketTimeoutException e) {
			return Resultat.DELAI_DEPASSE;
		}
		catch (SSLException e) {
			return Resultat.CERTIFICAT_INVALIDE;
		}
		catch (IOException e) {
			return Resultat.CONNEXION_REFUSEE;
		}
	}

	private static void envoyer(OutputStream sortie, URI adresse, String nom) throws IOException {
		String chemin = adresse.getRawPath() == null || adresse.getRawPath().isEmpty() ? "/" : adresse.getRawPath();
		if (adresse.getRawQuery() != null) {
			chemin += "?" + adresse.getRawQuery();
		}
		String requete = "GET " + chemin + " HTTP/1.1\r\nHost: " + nom
				+ "\r\nUser-Agent: Jamii-verification\r\nAccept: */*\r\nConnection: close\r\n\r\n";
		sortie.write(requete.getBytes(StandardCharsets.US_ASCII));
		sortie.flush();
	}

	/** Lit « HTTP/1.1 200 OK » et renvoie 200, ou -1 si la réponse n'est pas du HTTP. */
	static int codeHttp(InputStream entree) throws IOException {
		byte[] ligne = new byte[LIGNE_STATUT_MAX];
		int lus = 0;
		int octet;
		while (lus < ligne.length && (octet = entree.read()) != -1 && octet != '\n') {
			ligne[lus++] = (byte) octet;
		}
		String[] morceaux = new String(ligne, 0, lus, StandardCharsets.US_ASCII).trim().split(" ");
		if (morceaux.length < 2 || !morceaux[0].startsWith("HTTP/")) {
			return -1;
		}
		try {
			return Integer.parseInt(morceaux[1]);
		}
		catch (NumberFormatException e) {
			return -1;
		}
	}

	private static boolean estUneIp(String nom) {
		return nom.indexOf(':') >= 0 || nom.chars().allMatch(c -> c == '.' || Character.isDigit(c));
	}

}
