package de.prime_ux.goodnews.costs;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Tokens into dollars. No Spring context, no database. */
class ModelPricesTest {

	private static final String SONNET = "claude-sonnet-5";

	private final ModelPrices prices = new ModelPrices(new AiPriceProperties(Map.of(SONNET,
			new AiPriceProperties.Rate(new BigDecimal("2.00"), new BigDecimal("10.00")))));

	@Test
	void chargesTheInputAndTheOutputAtTheirOwnRates() {
		Optional<BigDecimal> cost = this.prices.costOf(SONNET, 1_000_000, 1_000_000);

		assertThat(cost.orElseThrow()).isEqualByComparingTo(new BigDecimal("12.00"));
	}

	@Test
	void chargesAFractionOfAMillionInProportion() {
		// 12345 * 2.00 + 678 * 10.00 = 31470, over a million.
		Optional<BigDecimal> cost = this.prices.costOf(SONNET, 12_345, 678);

		assertThat(cost.orElseThrow()).isEqualByComparingTo(new BigDecimal("0.031470"));
	}

	/** Six places is what the column holds, and far below a cent. */
	@Test
	void roundsToSixPlaces() {
		Optional<BigDecimal> cost = this.prices.costOf(SONNET, 1, 0);

		assertThat(cost.orElseThrow().scale()).isEqualTo(6);
	}

	@Test
	void chargesNothingForACallThatUsedNoTokens() {
		assertThat(this.prices.costOf(SONNET, 0, 0).orElseThrow()).isEqualByComparingTo(BigDecimal.ZERO);
	}

	/**
	 * Empty, not zero. A model nobody configured a rate for is an amount we do not know, and the
	 * page says so; a silent zero would read as work the provider did for free.
	 */
	@Test
	void namesNoAmountForAModelWithoutARate() {
		assertThat(this.prices.costOf("claude-something-new", 1_000, 1_000)).isEmpty();
	}

	@Test
	void namesNoAmountWhenNoRatesAreConfiguredAtAll() {
		ModelPrices without = new ModelPrices(new AiPriceProperties(null));

		assertThat(without.costOf(SONNET, 1_000, 1_000)).isEmpty();
	}
}
