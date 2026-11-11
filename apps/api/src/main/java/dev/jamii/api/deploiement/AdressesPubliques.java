package dev.jamii.api.deploiement;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

/**
 * Une adresse IP est-elle sur l'Internet public ? Jamii appelle des adresses fournies par des utilisateurs : sans ce
 * contrôle, on pourrait lui faire appeler un réseau interne ou le service de métadonnées de l'hébergeur (SSRF).
 */
final class AdressesPubliques {

	private AdressesPubliques() {
	}

	static boolean estPublique(InetAddress adresse) {
		if (adresse.isAnyLocalAddress() || adresse.isLoopbackAddress() || adresse.isLinkLocalAddress()
				|| adresse.isSiteLocalAddress() || adresse.isMulticastAddress()) {
			return false;
		}
		byte[] octets = adresse.getAddress();
		return adresse instanceof Inet6Address ? ipv6Publique(octets) : ipv4Publique(octets);
	}

	private static boolean ipv4Publique(byte[] o) {
		int a = o[0] & 0xff;
		int b = o[1] & 0xff;
		int c = o[2] & 0xff;
		return a != 0 // 0.0.0.0/8
				&& !(a == 100 && (b & 0xc0) == 64) // 100.64.0.0/10, partage d'opérateur
				&& !(a == 192 && b == 0 && (c == 0 || c == 2)) // 192.0.0.0/24, 192.0.2.0/24
				&& !(a == 198 && (b == 18 || b == 19)) // 198.18.0.0/15, bancs de test
				&& !(a == 198 && b == 51 && c == 100) // 198.51.100.0/24, documentation
				&& !(a == 203 && b == 0 && c == 113) // 203.0.113.0/24, documentation
				&& a < 240; // 240.0.0.0/4 et diffusion
	}

	private static boolean ipv6Publique(byte[] o) {
		int a = o[0] & 0xff;
		int b = o[1] & 0xff;
		if ((a & 0xfe) == 0xfc) {
			return false; // fc00::/7, adresses locales uniques
		}
		if (a == 0x20 && b == 0x01 && (o[2] & 0xff) == 0x0d && (o[3] & 0xff) == 0xb8) {
			return false; // 2001:db8::/32, documentation
		}
		// Une adresse IPv4 cachée dans une IPv6 est jugée comme l'IPv4 qu'elle contient.
		if (estNul(o, 0, 10) && ((o[10] & 0xff) == 0xff && (o[11] & 0xff) == 0xff || estNul(o, 10, 12))) {
			return ipv4Integree(o, 12); // ::ffff:a.b.c.d et ::a.b.c.d
		}
		if (a == 0x00 && b == 0x64 && (o[2] & 0xff) == 0xff && (o[3] & 0xff) == 0x9b) {
			return ipv4Integree(o, 12); // 64:ff9b::/96, NAT64
		}
		if (a == 0x20 && b == 0x02) {
			return ipv4Integree(o, 2); // 2002::/16, 6to4
		}
		return true;
	}

	/** Juge l'IPv4 contenue dans une IPv6 avec le contrôle complet (boucle locale, réseaux privés compris). */
	private static boolean ipv4Integree(byte[] o, int debut) {
		try {
			return estPublique(InetAddress.getByAddress(Arrays.copyOfRange(o, debut, debut + 4)));
		}
		catch (UnknownHostException e) {
			return false;
		}
	}

	private static boolean estNul(byte[] o, int debut, int fin) {
		for (int i = debut; i < fin; i++) {
			if (o[i] != 0) {
				return false;
			}
		}
		return true;
	}

	/** Résolution DNS, remplaçable dans les tests. */
	@FunctionalInterface
	interface Resolveur {

		InetAddress[] resoudre(String nom) throws UnknownHostException;

		Resolveur SYSTEME = InetAddress::getAllByName;

	}

}
