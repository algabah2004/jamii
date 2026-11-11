package dev.jamii.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import org.junit.jupiter.api.Test;

class ImagePostgresTest {

	@Test
	void lit_l_image_de_docker_compose() {
		assertThat(TestcontainersConfiguration.imagePostgres("""
				services:
				  postgres:
				    image: postgres:16-alpine
				    restart: unless-stopped
				""")).isEqualTo("postgres:16-alpine");
	}

	@Test
	void accepte_les_guillemets() {
		assertThat(TestcontainersConfiguration.imagePostgres("    image: \"postgres:17\"\n")).isEqualTo("postgres:17");
	}

	@Test
	void echoue_clairement_sans_image_postgres() {
		assertThatIllegalStateException().isThrownBy(() -> TestcontainersConfiguration.imagePostgres("image: redis:7\n"));
	}

}
