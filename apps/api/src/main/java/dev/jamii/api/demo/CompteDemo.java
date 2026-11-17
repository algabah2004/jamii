package dev.jamii.api.demo;

import static dev.jamii.api.demo.DonneesDemo.id;

import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * Effacer un compte de la démo et tout ce qu'il a créé : un visiteur dont le temps est écoulé ({@link VisiteursDemo}),
 * ou l'ancien compte démo partagé par tous les visiteurs, qu'on retire une fois pour toutes ({@link #retirerAncien}).
 */
final class CompteDemo {

	/** L'ancien compte partagé (#341), remplacé par un compte par visiteur. */
	static final DonneesDemo.Personne ANCIEN = new DonneesDemo.Personne("compte-demo", "Compte démo");

	private final JdbcClient base;

	CompteDemo(JdbcClient base) {
		this.base = base;
	}

	/**
	 * Les tables qui référencent le compte partent en cascade, sauf ce qu'on vide d'abord : ses cercles et les projets de
	 * leurs tours (le créateur et le porteur sont obligatoires), ses missions (sans clé étrangère, pour qu'une vraie
	 * mission survive à la suppression d'un compte), et ce qu'il a écrit dans les communautés, qui sinon y resterait
	 * sans auteur.
	 */
	void effacer(UUID compte) {
		List<UUID> projets = base.sql("""
				SELECT projet_id FROM cercle_membre
				WHERE projet_id IS NOT NULL AND cercle_id IN (SELECT id FROM cercle WHERE createur_id = :id)
				UNION SELECT id FROM projet WHERE porteur_id = :id
				""").param("id", compte).query(UUID.class).list();
		base.sql("DELETE FROM inscription WHERE equipe_id IN (SELECT id FROM equipe WHERE chef_id = :id)").param("id", compte).update();
		base.sql("DELETE FROM cercle WHERE createur_id = :id").param("id", compte).update();
		if (!projets.isEmpty()) {
			base.sql("UPDATE projet SET projet_repris_id = NULL WHERE projet_repris_id IN (:p)").param("p", projets).update();
			base.sql("DELETE FROM projet WHERE id IN (:p)").param("p", projets).update();
		}
		base.sql("DELETE FROM mission WHERE client_id = :id OR developpeur_id = :id").param("id", compte).update();
		base.sql("DELETE FROM message_salon WHERE auteur_id = :id").param("id", compte).update();
		base.sql("DELETE FROM reponse WHERE auteur_id = :id").param("id", compte).update();
		base.sql("DELETE FROM publication WHERE auteur_id = :id").param("id", compte).update();
		base.sql("DELETE FROM SPRING_SESSION WHERE PRINCIPAL_NAME = :nom").param("nom", compte.toString()).update();
		base.sql("DELETE FROM utilisateur WHERE id = :id").param("id", compte).update();
	}

	/** Une fois pour toutes : l'ancien compte partagé, et ce que la démo lui avait donné hors de son compte. */
	void retirerAncien() {
		base.sql("DELETE FROM cercle WHERE id = :c").param("c", id("cercle:pionniers")).update();
		base.sql("DELETE FROM mission WHERE id IN (:m)")
			.param("m", List.of(id("mission:jamii-mobile:compte-demo"), id("mission:terminee:tontine"))).update();
		base.sql("DELETE FROM publication WHERE id = :p").param("p", id("publication:compte-demo:question-cache")).update();
		effacer(ANCIEN.id());
	}

}
