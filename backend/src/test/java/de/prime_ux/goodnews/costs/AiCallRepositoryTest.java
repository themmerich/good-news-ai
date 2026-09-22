package de.prime_ux.goodnews.costs;

import static org.assertj.core.api.Assertions.assertThat;

import de.prime_ux.goodnews.TestcontainersConfiguration;
import de.prime_ux.goodnews.tenants.Tenant;
import de.prime_ux.goodnews.tenants.TenantRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * The four sums, against rows placed on either side of every boundary.
 *
 * <p>Those rows are the point of this test. A sum that takes one day too many looks entirely
 * plausible on the page, and only a row that sits an hour outside a boundary shows it.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AiCallRepositoryTest {

	private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

	/** A Tuesday morning: today is the 22nd, the week began Monday the 21st. */
	private static final Clock TUESDAY = Clock.fixed(
			ZonedDateTime.of(2026, 9, 22, 6, 28, 0, 0, BERLIN).toInstant(), BERLIN);

	@Autowired
	private AiCallRepository repository;

	@Autowired
	private TenantRepository tenantRepository;

	private Tenant tenant;
	private Tenant otherTenant;

	@BeforeEach
	void cleanDatabaseAndCreateTwoTenants() {
		this.repository.deleteAll();
		this.tenantRepository.deleteAll();
		this.tenant = this.tenantRepository.save(new Tenant("Musterfirma GmbH", "musterfirma"));
		this.otherTenant = this.tenantRepository.save(new Tenant("Andere GmbH", "andere"));
	}

	@Test
	void addsUpEachPeriodFromItsOwnBoundary() {
		call(this.tenant, at(2026, 9, 22, 5, 0), "1.000000");
		call(this.tenant, at(2026, 9, 21, 12, 0), "0.500000");
		call(this.tenant, at(2026, 9, 20, 23, 0), "0.250000");
		call(this.tenant, at(2026, 8, 31, 23, 30), "0.125000");
		call(this.tenant, at(2025, 12, 31, 23, 30), "99.000000");

		CostTotals totals = totals();

		assertThat(totals.dayCost()).isEqualByComparingTo("1.000000");
		assertThat(totals.weekCost()).isEqualByComparingTo("1.500000");
		assertThat(totals.monthCost()).isEqualByComparingTo("1.750000");
		assertThat(totals.yearCost()).isEqualByComparingTo("1.875000");
	}

	@Test
	void countsTheCallsOfEachPeriodAlongside() {
		call(this.tenant, at(2026, 9, 22, 5, 0), "1.000000");
		call(this.tenant, at(2026, 9, 21, 12, 0), "0.500000");
		call(this.tenant, at(2026, 9, 20, 23, 0), "0.250000");
		call(this.tenant, at(2026, 8, 31, 23, 30), "0.125000");

		CostTotals totals = totals();

		assertThat(totals.dayCalls()).isEqualTo(1);
		assertThat(totals.weekCalls()).isEqualTo(2);
		assertThat(totals.monthCalls()).isEqualTo(3);
		assertThat(totals.yearCalls()).isEqualTo(4);
	}

	/** Midnight belongs to the new day: a boundary includes the moment it names. */
	@Test
	void countsACallMadeExactlyAtMidnightAsToday() {
		call(this.tenant, at(2026, 9, 22, 0, 0), "2.000000");

		assertThat(totals().dayCost()).isEqualByComparingTo("2.000000");
	}

	/** And the second before it does not. */
	@Test
	void leavesLastNightOutOfToday() {
		call(this.tenant, ZonedDateTime.of(2026, 9, 21, 23, 59, 59, 0, BERLIN).toInstant(), "2.000000");

		CostTotals totals = totals();

		assertThat(totals.dayCost()).isNull();
		assertThat(totals.weekCost()).isEqualByComparingTo("2.000000");
	}

	/**
	 * A call whose model had no rate adds nothing to the sum and still counts. The row is real;
	 * only its amount is unknown.
	 */
	@Test
	void countsAnUnpricedCallWithoutAddingToTheSum() {
		call(this.tenant, at(2026, 9, 22, 5, 0), "1.000000");
		call(this.tenant, at(2026, 9, 22, 5, 30), null);

		CostTotals totals = totals();

		assertThat(totals.dayCost()).isEqualByComparingTo("1.000000");
		assertThat(totals.dayCalls()).isEqualTo(2);
	}

	@Test
	void leavesTheCallsOfAnotherTenantOut() {
		call(this.tenant, at(2026, 9, 22, 5, 0), "1.000000");
		call(this.otherTenant, at(2026, 9, 22, 5, 0), "50.000000");

		assertThat(totals().dayCost()).isEqualByComparingTo("1.000000");
	}

	/** A tenant that has never called anything has no sums at all — zeroes are made of this later. */
	@Test
	void hasNothingToAddUpForATenantWithoutCalls() {
		CostTotals totals = totals();

		assertThat(totals.yearCost()).isNull();
		assertThat(totals.yearCalls()).isNull();
	}

	@Test
	void handsOutTheListNewestFirstAPageAtATime() {
		call(this.tenant, at(2026, 9, 22, 5, 0), "1.000000");
		call(this.tenant, at(2026, 9, 22, 6, 0), "2.000000");
		call(this.tenant, at(2026, 9, 22, 4, 0), "3.000000");

		var page = this.repository.findAllByTenantId(this.tenant.getId(),
				PageRequest.of(0, 2, Sort.by(Sort.Direction.DESC, "calledAt")));

		assertThat(page.getTotalElements()).isEqualTo(3);
		assertThat(page.getContent()).hasSize(2);
		assertThat(page.getContent()).extracting(AiCall::getCalledAt)
				.containsExactly(at(2026, 9, 22, 6, 0), at(2026, 9, 22, 5, 0));
	}

	private CostTotals totals() {
		Periods periods = Periods.startingAt(TUESDAY);
		return this.repository.totalsOf(this.tenant.getId(), periods.day(), periods.week(), periods.month(),
				periods.year());
	}

	private void call(Tenant tenant, Instant calledAt, String costUsd) {
		this.repository.save(new AiCall(tenant, Purpose.RATING, "claude-sonnet-5", 1_000, 100,
				costUsd == null ? null : new BigDecimal(costUsd), calledAt));
	}

	private static Instant at(int year, int month, int day, int hour, int minute) {
		return ZonedDateTime.of(year, month, day, hour, minute, 0, 0, BERLIN).toInstant();
	}
}
