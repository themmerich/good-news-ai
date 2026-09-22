package de.prime_ux.goodnews.costs;

import de.prime_ux.goodnews.auth.CurrentSession;
import java.time.Clock;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * What the AI costs the tenant whose page this is.
 *
 * <p>Both endpoints read the tenant from the session, so there is no way to ask about another
 * one. Who may ask at all is settled in {@code SecurityConfig}: admins and super-users, like the
 * rest of the administration.
 */
@RestController
@RequestMapping("/api/costs")
class CostsController {

	/** A ceiling on the page size, so nobody pulls the whole table through the address bar. */
	private static final int MAX_PAGE_SIZE = 100;

	private static final int DEFAULT_PAGE_SIZE = 25;

	private final CurrentSession currentSession;
	private final AiCallRepository repository;

	/**
	 * The system clock, not an injected one: what depends on it is which calendar day a boundary
	 * falls on, and that question is answered — and tested — in {@link Periods}.
	 */
	private final Clock clock = Clock.systemDefaultZone();

	CostsController(CurrentSession currentSession, AiCallRepository repository) {
		this.currentSession = currentSession;
		this.repository = repository;
	}

	@GetMapping("/summary")
	@Transactional(readOnly = true)
	CostSummaryResponse getSummary() {
		Periods periods = Periods.startingAt(this.clock);
		return CostSummaryResponse.from(this.repository.totalsOf(this.currentSession.tenant().getId(),
				periods.day(), periods.week(), periods.month(), periods.year()));
	}

	/** Newest first: the interesting call is the one that just happened. */
	@GetMapping("/calls")
	@Transactional(readOnly = true)
	AiCallPageResponse getCalls(@RequestParam(defaultValue = "0") int page,
			@RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
		PageRequest request = PageRequest.of(Math.max(page, 0), clamp(size),
				Sort.by(Sort.Direction.DESC, "calledAt"));
		return AiCallPageResponse.from(this.repository.findAllByTenantId(this.currentSession.tenant().getId(), request));
	}

	private static int clamp(int size) {
		return Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
	}
}
