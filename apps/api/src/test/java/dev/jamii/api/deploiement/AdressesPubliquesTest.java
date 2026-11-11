package dev.jamii.api.deploiement;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Le contrôle anti-SSRF : aucune adresse interne ne doit passer. Sans réseau ni base. */
class AdressesPubliquesTest {

	@ParameterizedTest
	@ValueSource(strings = { "127.0.0.1", "10.1.2.3", "172.16.0.1", "192.168.1.10", "169.254.169.254", "0.0.0.0",
			"100.64.0.1", "192.0.0.8", "198.18.0.1", "224.0.0.1", "255.255.255.255", "::1", "::", "fe80::1",
			"fc00::1", "fd12:3456::1", "::ffff:127.0.0.1", "::ffff:169.254.169.254", "64:ff9b::a9fe:a9fe",
			"2002:7f00:0001::1", "2001:db8::1" })
	void une_adresse_interne_est_refusee(String ip) throws UnknownHostException {
		assertThat(AdressesPubliques.estPublique(InetAddress.getByName(ip))).as(ip).isFalse();
	}

	@ParameterizedTest
	@ValueSource(strings = { "41.82.10.5", "8.8.8.8", "196.207.0.1", "2c0f:f698::1", "2606:4700::1111" })
	void une_adresse_publique_est_acceptee(String ip) throws UnknownHostException {
		assertThat(AdressesPubliques.estPublique(InetAddress.getByName(ip))).as(ip).isTrue();
	}

}
