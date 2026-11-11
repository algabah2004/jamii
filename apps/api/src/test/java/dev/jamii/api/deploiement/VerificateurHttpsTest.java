package dev.jamii.api.deploiement;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;

import javax.net.ssl.SSLSocketFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Le vérificateur n'appelle jamais une adresse interne, même si le DNS l'y envoie. Sans réseau. */
class VerificateurHttpsTest {

	private static VerificateurHttps avecDns(AdressesPubliques.Resolveur resolveur) {
		return new VerificateurHttps(resolveur, (SSLSocketFactory) SSLSocketFactory.getDefault());
	}

	@Test
	void un_nom_qui_pointe_vers_le_reseau_interne_n_est_pas_appele() {
		var verificateur = avecDns(nom -> new InetAddress[] { InetAddress.getByName("169.254.169.254") });

		assertThat(verificateur.verifier(URI.create("https://piege.example.com/")))
			.isEqualTo(Verificateur.Resultat.ADRESSE_REFUSEE);
	}

	@Test
	void une_seule_ip_interne_parmi_plusieurs_suffit_a_refuser() {
		var verificateur = avecDns(nom -> new InetAddress[] { InetAddress.getByName("41.82.10.5"),
				InetAddress.getByName("10.0.0.5") });

		assertThat(verificateur.verifier(URI.create("https://mixte.example.com/")))
			.isEqualTo(Verificateur.Resultat.ADRESSE_REFUSEE);
	}

	@Test
	void un_nom_inconnu() {
		var verificateur = avecDns(nom -> {
			throw new UnknownHostException(nom);
		});

		assertThat(verificateur.verifier(URI.create("https://nexiste-pas.example.com/")))
			.isEqualTo(Verificateur.Resultat.NOM_INCONNU);
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { "HTTP/1.1 200 OK|200", "HTTP/1.1 301 Moved Permanently|301",
			"HTTP/2 503|503", "SSH-2.0-OpenSSH|-1", "|-1", "HTTP/1.1 abc|-1" })
	void lit_le_code_de_la_ligne_de_statut(String ligne, int attendu) throws IOException {
		var entree = new ByteArrayInputStream(((ligne == null ? "" : ligne) + "\r\nServer: x\r\n")
			.getBytes(StandardCharsets.US_ASCII));

		assertThat(VerificateurHttps.codeHttp(entree)).isEqualTo(attendu);
	}

}
