package dev.jamii.api.auth;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * Ce que la session retient de l'utilisateur connecté : son identifiant Jamii et ses rôles, rien d'autre.
 * Les autres modules le récupèrent avec {@code @AuthenticationPrincipal UtilisateurConnecte}.
 * Sérialisable, car la session est stockée en base.
 */
public record UtilisateurConnecte(UUID id, Set<Role> roles) implements OAuth2User, Serializable {

	public UtilisateurConnecte {
		roles = Set.copyOf(roles);
	}

	@Override
	public String getName() {
		return id.toString();
	}

	@Override
	public Map<String, Object> getAttributes() {
		return Map.of();
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return roles.stream().map(r -> new SimpleGrantedAuthority(r.autorite())).toList();
	}

}
