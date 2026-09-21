package de.prime_ux.goodnews.reading;

/**
 * Reads one RSS or Atom feed. An interface so a test can hand back a prepared feed instead of
 * reaching a real site, the way {@code ChatClients} does for the AI.
 *
 * <p>Named apart from {@link PageReader} because the search for feeds needs this one in
 * particular: confirming a candidate means parsing it as a feed, and a page reader would happily
 * make entries out of anything.
 */
public interface FeedReader extends SourceReader {
}
