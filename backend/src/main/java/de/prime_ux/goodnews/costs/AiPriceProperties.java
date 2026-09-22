package de.prime_ux.goodnews.costs;

import java.math.BigDecimal;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * What the provider charges, per model, in US dollars per million tokens.
 *
 * <p>In the properties rather than in a table with a screen behind it: the rates change rarely,
 * and when they do, a deployment carries the change. A model with no entry here is billed at no
 * rate at all — {@link ModelPrices} then leaves the amount empty rather than inventing one.
 *
 * @param prices keyed by the model name the provider reports, e.g. {@code claude-sonnet-5}
 */
@ConfigurationProperties("goodnews.ai")
public record AiPriceProperties(Map<String, Rate> prices) {

	public AiPriceProperties {
		prices = prices == null ? Map.of() : Map.copyOf(prices);
	}

	/**
	 * @param input dollars per million input tokens
	 * @param output dollars per million output tokens
	 */
	public record Rate(BigDecimal input, BigDecimal output) {
	}
}
