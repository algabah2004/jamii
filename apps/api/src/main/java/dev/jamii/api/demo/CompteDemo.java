package dev.jamii.api.demo;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Le compte démo (#157) : un compte sans GitHub ni Google, que tout visiteur ouvre d'un clic depuis la page de connexion
 * ({@link ConnexionDemoController}) pour explorer Jamii déjà rempli : un cercle dont c'est le tour, des communautés,
 * des contacts, un hackathon et une mission ({@link DemoMonCompte#pourCompteDemo}).
 * <p>
 * Le compte est partagé : il est donc <b>remis à zéro à chaque démarrage</b> de l'API. Ce qu'un visiteur y a fait
 * disparaît, et le parcours de départ revient. Son adresse est dans le domaine réservé `.test` : il ne peut écrire
 * qu'aux personnes de la démo, ni supprimer le compte (auth/Annuaire#estDeDemonstration).
 */
final class CompteDemo {

	static final DonneesDemo.Personne PERSONNE = new DonneesDemo.Personne("compte-demo", "Compte démo");

	static final UUID ID = PERSONNE.id();

	private final JdbcClient base;

	private final Instant maintenant;

	CompteDemo(JdbcClient base, Instant maintenant) {
		this.base = base;
		this.maintenant = maintenant;
	}

	/**
	 * Efface le compte et tout ce qu'il a créé. Les tables qui le référencent partent en cascade, sauf trois qu'on
	 * vide d'abord : les cercles qu'il a créés, les projets qu'il porte (et ceux des tours de son cercle) et les
	 * inscriptions de son équipe au hackathon (qui, sinon, resteraient sans équipe).
	 */
	void reinitialiser() {
		List<UUID> projets = base.sql("""
				SELECT projet_id FROM cercle_membre
				WHERE projet_id IS NOT NULL AND cercle_id IN (SELECT id FROM cercle WHERE id = :cercle OR createur_id = :id)
				UNION SELECT id FROM projet WHERE porteur_id = :id
				""").param("cercle", DemoMonCompte.CERCLE_DEMO).param("id", ID).query(UUID.class).list();
		base.sql("DELETE FROM inscription WHERE equipe_id IN (SELECT id FROM equipe WHERE chef_id = :id)").param("id", ID).update();
		base.sql("DELETE FROM cercle WHERE id = :cercle OR createur_id = :id").param("cercle", DemoMonCompte.CERCLE_DEMO).param("id", ID).update();
		if (!projets.isEmpty()) {
			base.sql("UPDATE projet SET projet_repris_id = NULL WHERE projet_repris_id IN (:p)").param("p", projets).update();
			base.sql("DELETE FROM projet WHERE id IN (:p)").param("p", projets).update();
		}
		base.sql("DELETE FROM SPRING_SESSION WHERE PRINCIPAL_NAME = :nom").param("nom", ID.toString()).update();
		base.sql("DELETE FROM utilisateur WHERE id = :id").param("id", ID).update();
	}

	/** Le compte, son rôle et un profil publié, comme les autres personnes de la démo. */
	void creer() {
		Timestamp t = Timestamp.from(maintenant.minus(Duration.ofDays(30)));
		base.sql("INSERT INTO utilisateur (id, nom, email, cree_le, mis_a_jour_le) VALUES (:id, :nom, :email, :t, :t)")
			.param("id", ID).param("nom", PERSONNE.nom()).param("email", PERSONNE.email()).param("t", t).update();
		base.sql("INSERT INTO utilisateur_role (utilisateur_id, role) VALUES (:id, 'membre')").param("id", ID).update();
	}

}
