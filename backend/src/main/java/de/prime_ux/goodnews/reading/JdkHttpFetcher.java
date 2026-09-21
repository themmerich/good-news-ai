package de.prime_ux.goodnews.reading;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.stereotype.Component;

/**
 * The real thing, built on the JDK's own client.
 *
 * <p>Ten seconds is the whole budget per request, connecting included. A feed that needs longer
 * would hold up a run for everyone behind it, and a source that slow is a source with a problem.
 *
 * <p>The user agent says who is asking and why. Sites are entitled to know, and an unnamed client
 * is the first thing a bot filter turns away.
 */
@Component
class JdkHttpFetcher implements HttpFetcher {

	private static final Duration TIMEOUT = Duration.ofSeconds(10);
	private static final String USER_AGENT = "good-news-ai/1.0 (news reader; +https://github.com/themmerich/good-news-ai)";
	/** Enough for a generous feed or a heavy front page; beyond that something is wrong. */
	private static final int MAX_BYTES = 8 * 1024 * 1024;

	private final HttpClient client = HttpClient.newBuilder()
			.connectTimeout(TIMEOUT)
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();

	@Override
	public Fetched get(URI uri) {
		HttpRequest request = HttpRequest.newBuilder(uri)
				.timeout(TIMEOUT)
				.header("User-Agent", USER_AGENT)
				.header("Accept", "application/rss+xml, application/atom+xml, application/xml, text/xml, text/html;q=0.9")
				.GET()
				.build();
		try {
			HttpResponse<byte[]> response = this.client.send(request, HttpResponse.BodyHandlers.ofByteArray());
			byte[] body = response.body();
			if (body.length > MAX_BYTES) {
				body = java.util.Arrays.copyOf(body, MAX_BYTES);
			}
			return new Fetched(response.statusCode(), response.headers().firstValue("content-type").orElse(""),
					body, response.uri());
		} catch (IOException e) {
			throw new SourceReadException("not reachable: " + e.getMessage(), e);
		} catch (InterruptedException e) {
			// Someone is shutting the run down; say so and let the caller stop, rather than
			// swallowing the flag and leaving the thread to find out later.
			Thread.currentThread().interrupt();
			throw new SourceReadException("interrupted", e);
		}
	}
}
