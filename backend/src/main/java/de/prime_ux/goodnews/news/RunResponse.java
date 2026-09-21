package de.prime_ux.goodnews.news;

import java.time.Instant;
import java.util.UUID;

/**
 * Where a run stands, for the page that asks every couple of seconds while one is going.
 *
 * <p>{@code totalArticles} counts what the AI still has to look at, not what was fetched: the
 * fetching is over in a moment, the rating is what takes the minute, and a progress bar that
 * stood still through the slow part would be worse than none.
 */
public record RunResponse(UUID id, RunStatus status, int totalArticles, int processedArticles, Instant startedAt,
		Instant finishedAt, String error) {

	static RunResponse from(NewsRun run) {
		return new RunResponse(run.getId(), run.getStatus(), run.getTotalArticles(), run.getProcessedArticles(),
				run.getStartedAt(), run.getFinishedAt(), run.getError());
	}
}
