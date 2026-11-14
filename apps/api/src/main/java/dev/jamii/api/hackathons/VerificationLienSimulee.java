package dev.jamii.api.hackathons;

import java.net.URI;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Pour les tests et le développement (`VERIFICATION_LIEN=simulee`), sans réseau : un nom en
 * `.invalid` (réservé par la RFC 2606) ne répond pas, tous les autres répondent.
 */
@Component
@ConditionalOnProperty(name = "jamii.verification-lien.fournisseur", havingValue = "simulee")
class VerificationLienSimulee implements VerificationLien {

	@Override
	public Optional<Boolean> verifier(URI lien) {
		return Optional.of(!lien.getHost().endsWith(".invalid"));
	}

}
