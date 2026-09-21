package de.prime_ux.goodnews.subscriptions;

import java.util.List;
import java.util.UUID;

/**
 * The catalog as a user sees it: the same tree the admin curates, flat and with {@code parentId},
 * plus the feeds hanging on each leaf and whether this user picked them. One request is enough to
 * draw the whole page.
 */
public record CatalogResponse(UUID id, UUID parentId, String name, int sortOrder, List<Feed> feeds) {

	public record Feed(UUID id, String name, String url, boolean selected) {
	}
}
