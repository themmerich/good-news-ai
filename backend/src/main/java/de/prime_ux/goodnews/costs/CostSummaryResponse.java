package de.prime_ux.goodnews.costs;

import java.math.BigDecimal;

/**
 * The four running periods, as the page shows them: today, this week, this month, this year.
 *
 * <p>Running rather than complete — "this week" is Monday until now, not a finished week. That is
 * the question the page answers: what is this costing me at the moment.
 */
public record CostSummaryResponse(PeriodCost day, PeriodCost week, PeriodCost month, PeriodCost year) {

	/**
	 * @param cost in US dollars, the currency the provider bills in
	 * @param calls how many calls went into that amount, including any whose price is unknown
	 */
	public record PeriodCost(BigDecimal cost, long calls) {
	}

	static CostSummaryResponse from(CostTotals totals) {
		if (totals == null) {
			return new CostSummaryResponse(empty(), empty(), empty(), empty());
		}
		return new CostSummaryResponse(
				period(totals.dayCost(), totals.dayCalls()),
				period(totals.weekCost(), totals.weekCalls()),
				period(totals.monthCost(), totals.monthCalls()),
				period(totals.yearCost(), totals.yearCalls()));
	}

	/**
	 * A period nothing was spent in shows 0.00 $ rather than nothing at all — that is what it
	 * cost, and an empty tile would read like a fault.
	 */
	private static PeriodCost period(BigDecimal cost, Long calls) {
		return new PeriodCost(cost == null ? BigDecimal.ZERO : cost, calls == null ? 0 : calls);
	}

	private static PeriodCost empty() {
		return new PeriodCost(BigDecimal.ZERO, 0);
	}
}
