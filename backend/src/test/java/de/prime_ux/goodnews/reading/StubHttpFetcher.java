package de.prime_ux.goodnews.reading;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Answers what a test prepared, and 404 for everything else. Nothing here reaches the network:
 * a test that asked a real site would be a test of that site, and it would fail on the day that
 * site is redesigned rather than on the day this code breaks.
 *
 * <p>Records what was asked for, so a test can show that a search stopped early rather than
 * working its way through every guess.
 */
public final class StubHttpFetcher implements HttpFetcher {

	private final Map<String, Fetched> answers = new LinkedHashMap<>();
	private final List<String> asked = new CopyOnWriteArrayList<>();

	public StubHttpFetcher feed(String url, String fixture) {
		return answer(url, 200, "application/rss+xml", fixtureBytes(fixture));
	}

	public StubHttpFetcher html(String url, String fixture) {
		return answer(url, 200, "text/html", fixtureBytes(fixture));
	}

	/** An error page with a body — which is where some sites keep the only mention of their feeds. */
	public StubHttpFetcher htmlWithStatus(String url, int status, String fixture) {
		return answer(url, status, "text/html", fixtureBytes(fixture));
	}

	public StubHttpFetcher answer(String url, int status, String contentType, byte[] body) {
		this.answers.put(url, new Fetched(status, contentType, body, URI.create(url)));
		return this;
	}

	public List<String> asked() {
		return List.copyOf(this.asked);
	}

	public void clear() {
		this.answers.clear();
		this.asked.clear();
	}

	@Override
	public Fetched get(URI uri) {
		this.asked.add(uri.toString());
		Fetched prepared = this.answers.get(uri.toString());
		return prepared != null ? prepared : new Fetched(404, "text/html", new byte[0], uri);
	}

	public static String fixture(String name) {
		return new String(fixtureBytes(name), StandardCharsets.UTF_8);
	}

	private static byte[] fixtureBytes(String name) {
		try (InputStream in = StubHttpFetcher.class.getResourceAsStream("/reading/" + name)) {
			if (in == null) {
				throw new IllegalArgumentException("no fixture named " + name);
			}
			return in.readAllBytes();
		} catch (IOException e) {
			throw new IllegalStateException("could not read the fixture " + name, e);
		}
	}
}
