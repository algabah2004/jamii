package dev.jamii.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Une vraie base PostgreSQL pour les tests, avec la même image que docker-compose.yml (une seule source). */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	static final Path DOCKER_COMPOSE = Path.of("../../docker-compose.yml");

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() throws IOException {
		return new PostgreSQLContainer(DockerImageName.parse(imagePostgres(Files.readString(DOCKER_COMPOSE))));
	}

	static String imagePostgres(String dockerCompose) {
		Matcher image = Pattern.compile("(?m)^\\s*image:\\s*[\"']?(postgres:[^\\s\"']+)").matcher(dockerCompose);
		if (!image.find()) {
			throw new IllegalStateException("Aucune image postgres:… trouvée dans " + DOCKER_COMPOSE);
		}
		return image.group(1);
	}

}
