package de.prime_ux.goodnews.aisettings;

import static org.assertj.core.api.Assertions.assertThat;

import de.prime_ux.goodnews.costs.Purpose;
import de.prime_ux.goodnews.costs.StubAiCalls;
import de.prime_ux.goodnews.tenants.Tenant;
import org.junit.jupiter.api.Test;

/** Trying a key out. No Spring context, no provider. */
class ApiKeyTesterTest {

	private static final Tenant TENANT = new Tenant("Musterfirma GmbH", "musterfirma");

	@Test
	void reportsAKeyTheProviderAcceptsAsWorking() {
		ApiKeyTester tester = new ApiKeyTester(StubChatClients.answering("pong"), new StubAiCalls());

		assertThat(tester.test(TENANT, "sk-ant-api03-testkey").success()).isTrue();
	}

	@Test
	void reportsARejectedKeyAsAResultRatherThanThrowing() {
		ApiKeyTester tester = new ApiKeyTester(StubChatClients.failing("invalid x-api-key"), new StubAiCalls());

		ApiKeyTester.ApiKeyTestResult result = tester.test(TENANT, "sk-ant-api03-wrong");

		assertThat(result.success()).isFalse();
		assertThat(result.message()).contains("invalid x-api-key");
	}

	/** A ping is a handful of tokens, and the invoice will carry them whether the page does or not. */
	@Test
	void booksTheTestItselfAsACost() {
		StubAiCalls aiCalls = new StubAiCalls();
		ApiKeyTester tester = new ApiKeyTester(StubChatClients.answering("pong"), aiCalls);

		tester.test(TENANT, "sk-ant-api03-testkey");

		assertThat(aiCalls.bookings()).singleElement()
				.satisfies(booking -> {
					assertThat(booking.purpose()).isEqualTo(Purpose.KEY_TEST);
					assertThat(booking.tenant()).isEqualTo(TENANT);
				});
	}

	/** A key the provider turned away was never served, so there is nothing to bill. */
	@Test
	void booksNothingWhenTheKeyIsRejected() {
		StubAiCalls aiCalls = new StubAiCalls();
		ApiKeyTester tester = new ApiKeyTester(StubChatClients.failing("invalid x-api-key"), aiCalls);

		tester.test(TENANT, "sk-ant-api03-wrong");

		assertThat(aiCalls.bookings()).isEmpty();
	}
}
