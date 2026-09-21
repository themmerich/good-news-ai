package de.prime_ux.goodnews.reading;

/**
 * A feed the search turned up and then confirmed by reading it. The title is what the sources
 * page proposes as a name; the count says how much stands in it right now, which is the quickest
 * way to tell a live feed from an abandoned one.
 */
public record FoundFeed(String url, String title, int entryCount) {
}
