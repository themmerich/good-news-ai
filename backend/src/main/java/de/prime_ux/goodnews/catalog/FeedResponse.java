package de.prime_ux.goodnews.catalog;

import java.time.Instant;
import java.util.UUID;

/**
 * One feed as the admin page lists it, including how the last fetch went. A source whose last
 * attempt failed is marked in the table, with the error as its tooltip — otherwise an admin would
 * have to work out from an empty tab that something is wrong.
 */
public record FeedResponse(UUID id, String name, String url, SourceType type, Instant lastFetchedAt,
		String lastError) {

	static FeedResponse from(Feed feed) {
		return new FeedResponse(feed.getId(), feed.getName(), feed.getUrl(), feed.getType(),
				feed.getLastFetchedAt(), feed.getLastError());
	}
}
