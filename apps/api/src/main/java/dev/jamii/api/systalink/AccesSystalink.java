package dev.jamii.api.systalink;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import dev.jamii.api.auth.Annuaire;
import dev.jamii.api.auth.Compte;
import dev.jamii.api.auth.Role;
import dev.jamii.api.auth.UtilisateurConnecte;

/**
 * Le rôle `systalink` (contracts/systalink.yaml) : un admin, ou un login GitHub ou un e-mail listé dans
 * `SYSTALINK_ACCES`, ou un juré qui a entré le code jury (`SYSTALINK_CODE_JURY`, table `acces_jury`). Tout l'espace
 * Systalink lui est réservé : tableau de bord, tendances et clients suivis. Liste vide et pas de code : seuls les
 * admins entrent.
 */
@Component
public class AccesSystalink {

	/** Essais de code permis par compte, tant que l'API tourne : de quoi corriger une faute de frappe, pas deviner. */
	static final int ESSAIS_MAX = 5;

	/** Un code plus court se devine : il est ignoré, comme s'il n'y en avait pas. */
	static final int LONGUEUR_MIN_CODE = 8;

	public enum ResultatCode {
		ACCORDE, FAUX, TROP_D_ESSAIS, FERME
	}

	private final Annuaire annuaire;

	private final JdbcClient base;

	private final Set<String> acces;

	private final byte[] codeJury;

	private final Map<UUID, Integer> essais = new ConcurrentHashMap<>();

	AccesSystalink(Annuaire annuaire, JdbcClient base, @Value("${jamii.clients-systalink.acces:}") String acces,
			@Value("${jamii.clients-systalink.code-jury:}") String codeJury) {
		this.annuaire = annuaire;
		this.base = base;
		this.acces = Arrays.stream(acces.split(","))
			.map(a -> a.strip().toLowerCase(Locale.ROOT))
			.filter(a -> !a.isEmpty())
			.collect(Collectors.toUnmodifiableSet());
		String code = codeJury.strip();
		this.codeJury = code.length() >= LONGUEUR_MIN_CODE ? code.getBytes(StandardCharsets.UTF_8) : null;
	}

	/** Le code jury est-il en service ? Sinon la page ne propose pas de l'entrer. */
	public boolean codeJuryOuvert() {
		return codeJury != null;
	}

	/**
	 * Un juré connecté entre le code donné avec la soumission CADEV. Comparaison en temps constant, et
	 * {@link #ESSAIS_MAX} essais par compte : après, le compte ne peut plus essayer jusqu'au redémarrage de l'API.
	 */
	public ResultatCode entrerCode(UtilisateurConnecte moi, String code) {
		if (codeJury == null) {
			return ResultatCode.FERME;
		}
		if (aAcces(moi)) {
			return ResultatCode.ACCORDE;
		}
		int dejaFaits = essais.merge(moi.id(), 1, Integer::sum);
		if (dejaFaits > ESSAIS_MAX) {
			return ResultatCode.TROP_D_ESSAIS;
		}
		byte[] donne = (code == null ? "" : code.strip()).getBytes(StandardCharsets.UTF_8);
		if (!MessageDigest.isEqual(codeJury, donne)) {
			return ResultatCode.FAUX;
		}
		base.sql("INSERT INTO acces_jury (utilisateur_id) VALUES (:id) ON CONFLICT DO NOTHING")
			.param("id", moi.id())
			.update();
		essais.remove(moi.id());
		return ResultatCode.ACCORDE;
	}

	public boolean aAcces(UtilisateurConnecte moi) {
		if (moi == null) {
			return false;
		}
		if (moi.roles().contains(Role.ADMIN)) {
			return true;
		}
		if (estJure(moi.id())) {
			return true;
		}
		if (acces.isEmpty()) {
			return false;
		}
		Optional<String> login = annuaire.lire(moi.id()).map(Compte::loginGithub);
		Optional<String> email = annuaire.emailDeContact(moi.id());
		return login.map(l -> acces.contains(l.toLowerCase(Locale.ROOT))).orElse(false)
				|| email.map(e -> acces.contains(e.toLowerCase(Locale.ROOT))).orElse(false);
	}

	/** Un juré garde l'accès tant que le code est en service : retirer le code ferme aussi la porte aux jurés. */
	private boolean estJure(UUID id) {
		return codeJury != null && base.sql("SELECT EXISTS (SELECT 1 FROM acces_jury WHERE utilisateur_id = :id)")
			.param("id", id)
			.query(Boolean.class)
			.single();
	}

}
