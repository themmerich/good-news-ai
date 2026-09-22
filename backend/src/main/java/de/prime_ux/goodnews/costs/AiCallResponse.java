package de.prime_ux.goodnews.costs;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One row of the list.
 *
 * @param costUsd null where the model has no rate configured; the page shows a dash for it
 */
public record AiCallResponse(UUID id, Instant calledAt, Purpose purpose, String model, int inputTokens,
		int outputTokens, BigDecimal costUsd) {

	static AiCallResponse from(AiCall call) {
		return new AiCallResponse(call.getId(), call.getCalledAt(), call.getPurpose(), call.getModel(),
				call.getInputTokens(), call.getOutputTokens(), call.getCostUsd());
	}
}
