package dev.jamii.api.missions;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Le devis d'une mission (contracts/missions.yaml, tag « propositions ») : le développeur retenu l'envoie, découpé en
 * jalons ; accepté par le client, il **vaut contrat** et la mission est attribuée.
 *
 * <p>Il n'y a jamais plus d'un devis non accepté par mission : en envoyer un nouveau remplace l'ancien.
 */
@Service
class Devis {

	/** Une étape du devis. Son suivi (livraison, acceptation, paiement) vient avec #178. */
	record Jalon(UUID id, int ordre, String titre, String description, Missions.Montant montant, int delaiJours, String statut) {
	}

	record DevisComplet(UUID id, UUID missionId, UUID propositionId, UUID developpeurId, Missions.Montant total,
			int retouchesComprises, String conditions, List<Jalon> jalons, Instant envoyeLe, Instant accepteLe) {
	}

	/** Un jalon nouveau, déjà validé par le contrôleur. */
	record NouveauJalon(String titre, String description, Missions.Montant montant, int delaiJours) {
	}

	/** Un devis nouveau, déjà validé par le contrôleur. */
	record Nouveau(Missions.Montant total, int retouchesComprises, String conditions, List<NouveauJalon> jalons) {
	}

	private final JdbcClient base;

	private final Missions missions;

	private final ApplicationEventPublisher evenements;

	private final Clock horloge;

	Devis(JdbcClient base, Missions missions, ApplicationEventPublisher evenements, ObjectProvider<Clock> horloge) {
		this.base = base;
		this.missions = missions;
		this.evenements = evenements;
		this.horloge = horloge.getIfAvailable(Clock::systemUTC);
	}

	/**
	 * Envoyer son devis. Dans l'ordre : la mission est-elle visible (404), est-elle encore ouverte (409
	 * `etat_invalide`), suis-je le développeur retenu (403), la somme des jalons égale-t-elle le total (409
	 * `somme_jalons_invalide`). Il remplace le devis non accepté précédent.
	 */
	@Transactional
	DevisComplet envoyer(UUID missionId, UUID moi, Nouveau n) {
		Missions.Mission m = missions.charger(missionId, true);
		missions.role(m, moi).orElseThrow(ErreurMissions::introuvable);
		if (!"publiee".equals(m.statut())) {
			throw ErreurMissions.conflit("etat_invalide", "Cette mission n'est plus ouverte");
		}
		UUID proposition = base.sql("SELECT id FROM proposition WHERE mission_id = :m AND developpeur_id = :d AND statut = 'retenue'")
			.param("m", missionId).param("d", moi).query(UUID.class).optional().orElseThrow(ErreurMissions::interdit);
		long somme = n.jalons().stream().mapToLong(j -> j.montant().centimes()).sum();
		if (somme != n.total().centimes()) {
			throw ErreurMissions.conflit("somme_jalons_invalide", "La somme des jalons doit égaler le total du devis");
		}
		base.sql("DELETE FROM devis WHERE mission_id = :m AND accepte_le IS NULL").param("m", missionId).update();
		UUID id = UUID.randomUUID();
		Instant maintenant = horloge.instant();
		base.sql("""
				INSERT INTO devis (id, mission_id, proposition_id, total_centimes, devise, retouches_comprises, conditions, cree_le)
				VALUES (:id, :m, :p, :total, :devise, :retouches, :conditions, :t)
				""")
			.param("id", id).param("m", missionId).param("p", proposition).param("total", n.total().centimes())
			.param("devise", n.total().devise()).param("retouches", n.retouchesComprises())
			.param("conditions", n.conditions(), java.sql.Types.VARCHAR).param("t", Timestamp.from(maintenant)).update();
		int ordre = 1;
		for (NouveauJalon j : n.jalons()) {
			base.sql("""
					INSERT INTO jalon_mission (id, devis_id, ordre, titre, description, montant_centimes, delai_jours)
					VALUES (:id, :d, :ordre, :titre, :description, :montant, :delai)
					""")
				.param("id", UUID.randomUUID()).param("d", id).param("ordre", ordre++).param("titre", j.titre())
				.param("description", j.description(), java.sql.Types.VARCHAR).param("montant", j.montant().centimes())
				.param("delai", j.delaiJours()).update();
		}
		evenements.publishEvent(new DevisEnvoye(missionId, id, m.clientId(), moi, maintenant));
		return lire(id);
	}

	/**
	 * Accepter le devis : le client seulement, avec la version qu'il a lue. La mission passe à `attribuee`, le
	 * développeur retenu est enregistré, sa proposition devient « acceptée » et les autres propositions en cours sont
	 * écartées. Deux acceptations simultanées : une seule passe (la mission est verrouillée).
	 */
	@Transactional
	void accepter(UUID missionId, UUID devisId, UUID moi, int version) {
		Missions.Mission m = missions.clientDe(missionId, moi);
		if (m.version() != version) {
			throw ErreurMissions.conflit("modification_concurrente", "La mission a changé entre-temps");
		}
		if (!"publiee".equals(m.statut())) {
			throw ErreurMissions.conflit("etat_invalide", "Cette mission n'est plus ouverte");
		}
		var devis = base.sql("SELECT proposition_id FROM devis WHERE id = :d AND mission_id = :m AND accepte_le IS NULL")
			.param("d", devisId).param("m", missionId).query(UUID.class).optional().orElseThrow(ErreurMissions::introuvable);
		UUID developpeur = base.sql("SELECT developpeur_id FROM proposition WHERE id = :p").param("p", devis)
			.query(UUID.class).single();
		Instant maintenant = horloge.instant();
		base.sql("UPDATE devis SET accepte_le = :t WHERE id = :d").param("t", Timestamp.from(maintenant)).param("d", devisId).update();
		base.sql("UPDATE proposition SET statut = 'acceptee' WHERE id = :p").param("p", devis).update();
		List<UUID> ecartes = base.sql("""
				UPDATE proposition SET statut = 'non_retenue'
				WHERE mission_id = :m AND statut IN ('envoyee', 'retenue') RETURNING developpeur_id
				""")
			.param("m", missionId).query(UUID.class).list();
		base.sql("UPDATE mission SET statut = 'attribuee', developpeur_id = :d, version = version + 1 WHERE id = :m")
			.param("d", developpeur).param("m", missionId).update();
		evenements.publishEvent(new MissionAttribuee(missionId, devisId, m.clientId(), developpeur, ecartes, maintenant));
	}

	/**
	 * Le devis en cours de la mission : le dernier envoyé, accepté ou non. Vide tant qu'aucun n'a été envoyé. Réservé à
	 * qui a déjà le droit de le voir (le client et le développeur retenu) : c'est au contrôleur de le vérifier.
	 */
	@Transactional(readOnly = true)
	Optional<DevisComplet> courant(UUID missionId) {
		return base.sql("SELECT id FROM devis WHERE mission_id = :m ORDER BY cree_le DESC LIMIT 1").param("m", missionId)
			.query(UUID.class).optional().map(this::lire);
	}

	/** Les jalons du devis accepté, dans l'ordre : ce dont #178 a besoin pour suivre le travail. */
	@Transactional(readOnly = true)
	List<Jalon> jalons(UUID missionId) {
		return courant(missionId).filter(d -> d.accepteLe() != null).map(DevisComplet::jalons).orElse(List.of());
	}

	/** Est-ce le développeur dont la proposition est retenue ou acceptée ? */
	@Transactional(readOnly = true)
	boolean estRetenu(UUID missionId, UUID moi) {
		return moi != null && base.sql("""
				SELECT EXISTS (SELECT 1 FROM proposition WHERE mission_id = :m AND developpeur_id = :d AND statut IN ('retenue', 'acceptee'))
				""").param("m", missionId).param("d", moi).query(Boolean.class).single();
	}

	private DevisComplet lire(UUID id) {
		DevisComplet entete = base.sql("""
				SELECT d.id, d.mission_id, d.proposition_id, p.developpeur_id, d.total_centimes, d.devise, d.retouches_comprises,
				       d.conditions, d.cree_le, d.accepte_le
				FROM devis d JOIN proposition p ON p.id = d.proposition_id WHERE d.id = :id
				""").param("id", id)
			.query((rs, i) -> {
				Timestamp accepte = rs.getTimestamp("accepte_le");
				return new DevisComplet(rs.getObject("id", UUID.class), rs.getObject("mission_id", UUID.class),
						rs.getObject("proposition_id", UUID.class), rs.getObject("developpeur_id", UUID.class),
						new Missions.Montant(rs.getLong("total_centimes"), rs.getString("devise")), rs.getInt("retouches_comprises"),
						rs.getString("conditions"), List.of(), rs.getTimestamp("cree_le").toInstant(),
						accepte == null ? null : accepte.toInstant());
			})
			.single();
		// Tous les jalons d'un devis sont dans la devise du devis.
		String devise = entete.total().devise();
		List<Jalon> jalons = base.sql("""
				SELECT id, ordre, titre, description, montant_centimes, delai_jours, statut FROM jalon_mission
				WHERE devis_id = :d ORDER BY ordre
				""").param("d", id)
			.query((rs, i) -> new Jalon(rs.getObject("id", UUID.class), rs.getInt("ordre"), rs.getString("titre"),
					rs.getString("description"), new Missions.Montant(rs.getLong("montant_centimes"), devise),
					rs.getInt("delai_jours"), rs.getString("statut")))
			.list();
		return new DevisComplet(entete.id(), entete.missionId(), entete.propositionId(), entete.developpeurId(), entete.total(),
				entete.retouchesComprises(), entete.conditions(), List.copyOf(jalons), entete.envoyeLe(), entete.accepteLe());
	}

}
