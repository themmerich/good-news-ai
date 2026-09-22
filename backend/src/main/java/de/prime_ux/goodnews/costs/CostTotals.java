package de.prime_ux.goodnews.costs;

import java.math.BigDecimal;

/**
 * What the four running periods add up to, as the query hands them over: flat, and null where
 * nothing was summed.
 *
 * <p>Null rather than zero throughout — an aggregate over no rows has no sum, and a tenant that
 * has never run anything gets a row of nulls. {@link CostSummaryResponse} turns that into the
 * zeroes the page shows.
 */
public record CostTotals(
		BigDecimal dayCost, Long dayCalls,
		BigDecimal weekCost, Long weekCalls,
		BigDecimal monthCost, Long monthCalls,
		BigDecimal yearCost, Long yearCalls) {
}
