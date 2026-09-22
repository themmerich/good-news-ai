package de.prime_ux.goodnews.costs;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Turns tokens into dollars.
 *
 * <p>Both rates are applied to the raw counts and the sum is rounded once, at the end. Rounding
 * each half first would put half a rounding error into every row, and the rows are added up.
 */
@Component
public class ModelPrices {

	private static final Logger log = LoggerFactory.getLogger(ModelPrices.class);

	/** As many places as the column holds, which is far below a cent. */
	private static final int SCALE = 6;

	private static final BigDecimal MILLION = BigDecimal.valueOf(1_000_000);

	private final AiPriceProperties properties;

	/** Models already complained about, so an unpriced model is one line in the log, not one per call. */
	private final Set<String> warnedAbout = ConcurrentHashMap.newKeySet();

	ModelPrices(AiPriceProperties properties) {
		this.properties = properties;
	}

	/**
	 * What a call cost, or nothing where the model has no rate.
	 *
	 * <p>Empty is not zero: it says the amount is unknown, and that is what the page shows. A
	 * silent zero would look like work the provider did for free.
	 */
	public Optional<BigDecimal> costOf(String model, int inputTokens, int outputTokens) {
		AiPriceProperties.Rate rate = this.properties.prices().get(model);
		if (rate == null || rate.input() == null || rate.output() == null) {
			warnOnce(model);
			return Optional.empty();
		}
		BigDecimal cost = BigDecimal.valueOf(inputTokens).multiply(rate.input())
				.add(BigDecimal.valueOf(outputTokens).multiply(rate.output()))
				.divide(MILLION, SCALE, RoundingMode.HALF_UP);
		return Optional.of(cost);
	}

	private void warnOnce(String model) {
		if (this.warnedAbout.add(model)) {
			log.warn("no price configured for the model {} — its calls are recorded without an amount "
					+ "(goodnews.ai.prices.{}.input / .output)", model, model);
		}
	}
}
