package de.prime_ux.goodnews.catalog;

import java.util.UUID;

/** One feed as the admin page lists it. */
public record FeedResponse(UUID id, String name, String url, SourceType type) {

	static FeedResponse from(Feed feed) {
		return new FeedResponse(feed.getId(), feed.getName(), feed.getUrl(), feed.getType());
	}
}
