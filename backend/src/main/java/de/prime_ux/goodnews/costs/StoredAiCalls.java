package de.prime_ux.goodnews.costs;

import de.prime_ux.goodnews.tenants.Tenant;
import java.math.BigDecimal;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

/**
 * The one place that reads the provider's accounting and writes it down. Callers hand over the
 * answer they got and are done with it; what a token costs is none of their business.
 */
@Component
public class StoredAiCalls implements AiCalls {

	private static final Logger log = LoggerFactory.getLogger(StoredAiCalls.class);

	/** Where the provider names no model, so the row still says something and gets no price. */
	private static final String UNKNOWN_MODEL = "unknown";

	private final AiCallRepository repository;
	private final ModelPrices prices;

	StoredAiCalls(AiCallRepository repository, ModelPrices prices) {
		this.repository = repository;
		this.prices = prices;
	}

	/**
	 * An answer with no tokens on it — which is what a call that never reached a provider leaves
	 * behind — is not booked: nothing was billed.
	 */
	@Override
	public void record(Tenant tenant, Purpose purpose, ChatResponse response) {
		try {
			ChatResponseMetadata metadata = response == null ? null : response.getMetadata();
			Usage usage = metadata == null ? null : metadata.getUsage();
			if (usage == null) {
				return;
			}
			String model = metadata.getModel() == null || metadata.getModel().isBlank()
					? UNKNOWN_MODEL
					: metadata.getModel();
			int inputTokens = countOf(usage.getPromptTokens());
			int outputTokens = countOf(usage.getCompletionTokens());
			if (inputTokens == 0 && outputTokens == 0) {
				// No tokens is no bill. An answer carries an empty accounting when it never came
				// from a provider at all, and a row of zeroes would be noise in the list.
				return;
			}
			BigDecimal cost = this.prices.costOf(model, inputTokens, outputTokens).orElse(null);
			this.repository.save(new AiCall(tenant, purpose, model, inputTokens, outputTokens, cost, Instant.now()));
		} catch (RuntimeException e) {
			log.warn("the cost of a {} call could not be recorded", purpose, e);
		}
	}

	private static int countOf(Integer tokens) {
		return tokens == null ? 0 : tokens;
	}
}
