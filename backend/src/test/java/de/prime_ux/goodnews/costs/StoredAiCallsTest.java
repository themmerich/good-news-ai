package de.prime_ux.goodnews.costs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import de.prime_ux.goodnews.TestcontainersConfiguration;
import de.prime_ux.goodnews.tenants.Tenant;
import de.prime_ux.goodnews.tenants.TenantRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** What gets written down when a call comes back, and what does not. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class StoredAiCallsTest {

	/** The model the application is configured for, and the one with a rate in the properties. */
	private static final String SONNET = "claude-sonnet-5";

	@Autowired
	private AiCalls aiCalls;

	@Autowired
	private AiCallRepository repository;

	@Autowired
	private TenantRepository tenantRepository;

	private Tenant tenant;

	@BeforeEach
	void cleanDatabase() {
		this.repository.deleteAll();
		this.tenantRepository.deleteAll();
		this.tenant = this.tenantRepository.save(new Tenant("Musterfirma GmbH", "musterfirma"));
	}

	@Test
	void writesDownTheModelTheTokensAndTheAmount() {
		this.aiCalls.record(this.tenant, Purpose.RATING, AiAnswers.withUsage(SONNET, 12_345, 678));

		assertThat(this.repository.findAll()).singleElement().satisfies(call -> {
			assertThat(call.getPurpose()).isEqualTo(Purpose.RATING);
			assertThat(call.getModel()).isEqualTo(SONNET);
			assertThat(call.getInputTokens()).isEqualTo(12_345);
			assertThat(call.getOutputTokens()).isEqualTo(678);
			// 12345 input at 2.00 and 678 output at 10.00 per million.
			assertThat(call.getCostUsd()).isEqualByComparingTo(new BigDecimal("0.031470"));
		});
	}

	/**
	 * The row is written all the same. What it cost is unknown, and the page shows that rather
	 * than dropping a call that really happened.
	 */
	@Test
	void writesTheCallOfAnUnpricedModelWithoutAnAmount() {
		this.aiCalls.record(this.tenant, Purpose.RATING, AiAnswers.withUsage("claude-something-new", 100, 20));

		assertThat(this.repository.findAll()).singleElement().satisfies(call -> {
			assertThat(call.getModel()).isEqualTo("claude-something-new");
			assertThat(call.getCostUsd()).isNull();
		});
	}

	@Test
	void writesNothingForAnAnswerThatUsedNoTokens() {
		this.aiCalls.record(this.tenant, Purpose.KEY_TEST, AiAnswers.withoutAccounting());

		assertThat(this.repository.findAll()).isEmpty();
	}

	@Test
	void writesNothingWhenThereIsNoAnswerAtAll() {
		this.aiCalls.record(this.tenant, Purpose.RATING, null);

		assertThat(this.repository.findAll()).isEmpty();
	}

	/**
	 * A booking that cannot be written must not reach the caller: it is called from the middle of
	 * a run, and a run abandoned over a failed insert costs the tenant a whole pass over its
	 * sources. A tenant that was never saved is the readiest way to make the insert fail.
	 */
	@Test
	void swallowsAFailedBookingRatherThanBreakingTheRun() {
		Tenant unsaved = new Tenant("Niemand GmbH", "niemand");

		assertThatCode(() -> this.aiCalls.record(unsaved, Purpose.RATING, AiAnswers.withUsage(SONNET, 10, 10)))
				.doesNotThrowAnyException();
		assertThat(this.repository.findAll()).isEmpty();
	}
}
