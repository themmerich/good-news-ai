package de.prime_ux.goodnews;

import de.prime_ux.goodnews.reading.StubHttpFetcher;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		// Major version pinned; keep in sync with compose.yaml.
		return new PostgreSQLContainer(DockerImageName.parse("postgres:18"));
	}

	/**
	 * No test reaches the open internet. A test that asked a real site would be a test of that
	 * site: it would go red the day that site is redesigned rather than the day this code breaks,
	 * and it would fail on a train.
	 *
	 * <p>It sits here, beside the database, because both are the same thing — the world outside,
	 * held still for the length of a test run. A test that needs an answer prepares one; every
	 * address nobody prepared answers 404.
	 */
	@Bean
	@Primary
	StubHttpFetcher stubHttpFetcher() {
		return new StubHttpFetcher();
	}
}
