/**
 * What one period cost.
 *
 * `cost` is in US dollars, the currency Anthropic bills in. `calls` counts every call of the
 * period, including those whose price is unknown and therefore not in the amount.
 */
export type PeriodCost = {
  cost: number;
  calls: number;
};

/**
 * The four running periods. Running, not complete: "this week" is Monday until now, which is the
 * question the page answers — what is this costing at the moment.
 */
export type CostSummary = {
  day: PeriodCost;
  week: PeriodCost;
  month: PeriodCost;
  year: PeriodCost;
};
