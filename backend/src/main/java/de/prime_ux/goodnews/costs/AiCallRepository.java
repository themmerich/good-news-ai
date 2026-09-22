package de.prime_ux.goodnews.costs;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AiCallRepository extends JpaRepository<AiCall, UUID> {

	/** The list, a page at a time. The sort comes from the {@link Pageable}, newest first. */
	Page<AiCall> findAllByTenantId(UUID tenantId, Pageable pageable);

	/**
	 * All four periods in one pass.
	 *
	 * <p>Four queries would read the same rows four times over, the longest of them covering the
	 * other three: this year's rows contain this month's, this week's and today's. So the where
	 * clause cuts at the year and the four cases sort the rows out from there.
	 *
	 * <p>A call without a price contributes nothing to the sum and still counts — the row is real,
	 * only its amount is unknown.
	 */
	@Query("""
			SELECT new de.prime_ux.goodnews.costs.CostTotals(
				SUM(CASE WHEN call.calledAt >= :day THEN call.costUsd END),
				SUM(CASE WHEN call.calledAt >= :day THEN 1L ELSE 0L END),
				SUM(CASE WHEN call.calledAt >= :week THEN call.costUsd END),
				SUM(CASE WHEN call.calledAt >= :week THEN 1L ELSE 0L END),
				SUM(CASE WHEN call.calledAt >= :month THEN call.costUsd END),
				SUM(CASE WHEN call.calledAt >= :month THEN 1L ELSE 0L END),
				SUM(CASE WHEN call.calledAt >= :year THEN call.costUsd END),
				SUM(CASE WHEN call.calledAt >= :year THEN 1L ELSE 0L END))
			FROM AiCall call
			WHERE call.tenant.id = :tenantId AND call.calledAt >= :year
			""")
	CostTotals totalsOf(@Param("tenantId") UUID tenantId, @Param("day") Instant day, @Param("week") Instant week,
			@Param("month") Instant month, @Param("year") Instant year);
}
