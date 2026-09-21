package de.prime_ux.goodnews.catalog;

import de.prime_ux.goodnews.reading.FoundFeed;
import java.util.List;

/**
 * What the search turned up. An empty list is a result, not a failure: it means the site offers
 * no feed, and the page then offers to read the site itself.
 */
record ProbeResponse(List<FoundFeed> feeds) {
}
