package de.prime_ux.goodnews.catalog;

import java.util.UUID;

/**
 * One feed as the admin page lists it. The category's name travels along so the table can show it
 * without looking every id up again.
 */
public record FeedResponse(UUID id, UUID categoryId, String categoryName, String name, String url) {

	static FeedResponse from(Feed feed) {
		return new FeedResponse(feed.getId(), feed.getCategory().getId(), feed.getCategory().getName(),
				feed.getName(), feed.getUrl());
	}
}
