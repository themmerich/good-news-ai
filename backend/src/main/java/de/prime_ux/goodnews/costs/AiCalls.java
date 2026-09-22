package de.prime_ux.goodnews.costs;

import de.prime_ux.goodnews.tenants.Tenant;
import org.springframework.ai.chat.model.ChatResponse;

/**
 * Books what a call to the AI cost.
 *
 * <p>What the rating and the key test depend on: the interface, so a test of either can stand in
 * a booking that writes nothing, the way {@code ChatClients} stands in for the model itself. The
 * real one is {@link StoredAiCalls}.
 */
public interface AiCalls {

	/**
	 * Writes down one call, reading the provider's own accounting off the answer.
	 *
	 * <p>Nothing thrown in here reaches the caller: a missing booking is a gap in a page, while a
	 * run abandoned over a failed insert costs the tenant a whole pass over its sources.
	 */
	void record(Tenant tenant, Purpose purpose, ChatResponse response);
}
