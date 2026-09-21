package de.prime_ux.goodnews.reading;

/**
 * Reads a news page that offers no feed. An interface for the same reason {@link FeedReader} is
 * one: a run has to be testable without a site on the other end.
 */
public interface PageReader extends SourceReader {
}
