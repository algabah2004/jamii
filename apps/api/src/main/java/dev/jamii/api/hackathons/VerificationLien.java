package dev.jamii.api.hackathons;

import java.net.URI;
import java.util.Optional;

/**
 * Le lien du produit d'un rendu répond-il ? Vide : on ne sait pas le vérifier ici (aucun adaptateur réel), et le rendu
 * l'affiche « pas encore vérifié » plutôt qu'un faux « il répond ». Une panne n'empêche jamais de rendre.
 */
interface VerificationLien {

	Optional<Boolean> verifier(URI lien);

}
