package de.prime_ux.goodnews.costs;

import de.prime_ux.goodnews.tenants.Tenant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.ai.chat.model.ChatResponse;

/**
 * Bookings that go nowhere but into a list, so a test of something that calls the AI can say
 * whether it booked, and for what, without a database behind it.
 */
public final class StubAiCalls implements AiCalls {

	public record Booking(Tenant tenant, Purpose purpose, ChatResponse response) {
	}

	private final List<Booking> bookings = new ArrayList<>();

	@Override
	public void record(Tenant tenant, Purpose purpose, ChatResponse response) {
		this.bookings.add(new Booking(tenant, purpose, response));
	}

	public List<Booking> bookings() {
		return List.copyOf(this.bookings);
	}
}
