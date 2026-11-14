package dev.jamii.api.hackathons;

import java.net.URI;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Par défaut, aucun appel réseau : le lien reste « pas encore vérifié ». L'adaptateur réel reprendra la vérification
 * protégée contre la SSRF du module de mise en ligne, quand elle sera ouverte aux autres modules.
 */
@Component
@ConditionalOnProperty(name = "jamii.verification-lien.fournisseur", havingValue = "aucune", matchIfMissing = true)
class VerificationLienAbsente implements VerificationLien {

	@Override
	public Optional<Boolean> verifier(URI lien) {
		return Optional.empty();
	}

}
