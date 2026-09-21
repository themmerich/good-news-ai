package de.prime_ux.goodnews.reading;

/**
 * Reads one RSS or Atom feed. An interface so a test can hand back a prepared feed instead of
 * reaching a real site, the way {@code ChatClients} does for the AI.
 */
public interface FeedReader {

	/**
	 * @throws SourceReadException when the address does not answer, answers with an error, or
	 *                             answers with something that is not a feed
	 */
	SourceContent read(String url);
}
