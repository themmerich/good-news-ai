package de.prime_ux.goodnews.reading;

import java.net.URI;

/**
 * One GET against the open internet. An interface, not a utility: everything in this package
 * stands or falls with what the network answers, and a test that had to reach a real site would
 * be a test of that site.
 */
public interface HttpFetcher {

	/**
	 * @param uri what to fetch; redirects are followed
	 * @return what came back, whatever the status
	 * @throws SourceReadException when nothing came back at all — no route, no answer, too slow
	 */
	Fetched get(URI uri);

	/**
	 * @param finalUri where the answer actually came from, after redirects — relative links in the
	 *                 body are resolved against this, not against what was asked for
	 */
	record Fetched(int status, String contentType, byte[] body, URI finalUri) {

		public boolean isOk() {
			return this.status >= 200 && this.status < 300;
		}

		public String bodyAsText() {
			return new String(this.body, java.nio.charset.StandardCharsets.UTF_8);
		}
	}
}
