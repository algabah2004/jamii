package dev.jamii.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SanteTest {

	@Autowired
	MockMvcTester mvc;

	@Autowired
	JdbcTemplate jdbc;

	@Autowired
	HealthEndpointGroups groupes;

	@Test
	void health_repond_up_sans_details_internes() {
		assertThat(mvc.get().uri("/health"))
			.hasStatusOk()
			.bodyJson()
			.isLenientlyEqualTo("{\"status\":\"UP\"}");
		assertThat(mvc.get().uri("/health")).bodyText().doesNotContain("components", "postgres", "diskSpace");
	}

	@Test
	void sondes_datacloud_disponibles() {
		assertThat(mvc.get().uri("/health/liveness")).hasStatusOk();
		assertThat(mvc.get().uri("/health/readiness")).hasStatusOk();
	}

	@Test
	void readiness_depend_de_la_base_mais_pas_la_liveness() {
		assertThat(groupes.get("readiness").isMember("db")).isTrue();
		assertThat(groupes.get("liveness").isMember("db")).isFalse();
	}

	@Test
	void aucun_autre_endpoint_actuator_expose() {
		for (String chemin : new String[] { "/env", "/beans", "/configprops", "/actuator", "/actuator/env" }) {
			assertThat(mvc.get().uri(chemin)).as(chemin).hasStatus(HttpStatus.NOT_FOUND);
		}
	}

	@Test
	void migrations_flyway_appliquees() {
		Integer appliquees = jdbc.queryForObject(
				"SELECT count(*) FROM flyway_schema_history WHERE success AND version = '1'", Integer.class);
		assertThat(appliquees).isEqualTo(1);
	}

}
