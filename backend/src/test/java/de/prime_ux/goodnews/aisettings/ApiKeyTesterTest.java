package de.prime_ux.goodnews.aisettings;

import static org.assertj.core.api.Assertions.assertThat;

import de.prime_ux.goodnews.tenants.Tenant;
import org.junit.jupiter.api.Test;

/** Trying a key out. No Spring context, no provider. */
class ApiKeyTesterTest {

	private static final Tenant TENANT = new Tenant("Musterfirma GmbH", "musterfirma");

	@Test
	void reportsAKeyTheProviderAcceptsAsWorking() {
		ApiKeyTester tester = new ApiKeyTester(StubChatClients.answering("pong"));

		assertThat(tester.test(TENANT, "sk-ant-api03-testkey").success()).isTrue();
	}

	@Test
	void reportsARejectedKeyAsAResultRatherThanThrowing() {
		ApiKeyTester tester = new ApiKeyTester(StubChatClients.failing("invalid x-api-key"));

		ApiKeyTester.ApiKeyTestResult result = tester.test(TENANT, "sk-ant-api03-wrong");

		assertThat(result.success()).isFalse();
		assertThat(result.message()).contains("invalid x-api-key");
	}
}
