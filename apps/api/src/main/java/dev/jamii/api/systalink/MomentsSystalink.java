package dev.jamii.api.systalink;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API publique du module, pour les autres modules : « je propose tel service Systalink, ici ».
 * Exemple (bloc #20, au moment de mettre en ligne) : {@code moments.proposer(DATACLOUD, PROJET, projetId)}.
 */
@Service
public class MomentsSystalink {

	private final JdbcClient base;

	private final LiensSystalink liens;

	private final Clock horloge;

	MomentsSystalink(JdbcClient base, LiensSystalink liens, ObjectProvider<Clock> horloge) {
		this.base = base;
		this.liens = liens;
		this.horloge = horloge.getIfAvailable(Clock::systemUTC);
	}

	@Transactional
	public PropositionSystalink proposer(ServiceSystalink service, ContexteMoment contexte, UUID contexteId) {
		UUID id = UUID.randomUUID();
		base.sql("""
				INSERT INTO moment_systalink (id, service, contexte, contexte_id, propose_le)
				VALUES (:id, :service, :contexte, :contexteId, :maintenant)""")
			.param("id", id)
			.param("service", service.code())
			.param("contexte", contexte.code())
			.param("contexteId", contexteId)
			.param("maintenant", maintenant())
			.update();
		return new PropositionSystalink(id, service, liens.pour(service));
	}

	/**
	 * Enregistre le premier clic. Renvoie faux si le moment n'existe pas.
	 * Idempotent : `accepte_le` n'est posé qu'une fois, un second appel ne change rien.
	 */
	@Transactional
	boolean accepter(UUID momentId) {
		int modifies = base.sql("UPDATE moment_systalink SET accepte_le = :maintenant WHERE id = :id AND accepte_le IS NULL")
			.param("id", momentId)
			.param("maintenant", maintenant())
			.update();
		return modifies == 1 || base.sql("SELECT count(*) FROM moment_systalink WHERE id = :id")
			.param("id", momentId)
			.query(Long.class)
			.single() == 1;
	}

	private OffsetDateTime maintenant() {
		return horloge.instant().atOffset(ZoneOffset.UTC);
	}

}
