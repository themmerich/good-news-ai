package de.prime_ux.goodnews.reading;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * Makes entries out of a news page, in two moves and without asking a model anything.
 *
 * <p>An overview page has no structure to hold on to: the articles sit somewhere between
 * navigation, advertising and the footer. The NFL news page brings 437 links in 1.5 MB of HTML.
 * Handing that to a model would be around 400,000 tokens for a single fetch, which is not worth
 * the money.
 *
 * <p>So first the links are collected and filtered down to a few dozen plausible articles, and
 * then the head of each candidate is read for its Open Graph tags. Those are on news sites
 * practically always, because Facebook and WhatsApp need them, and they carry exactly what a feed
 * would have carried: a title, a couple of sentences and a date.
 *
 * <p>What this cannot do is a page that fills itself in with JavaScript. To a server such a page
 * is empty, and it says so rather than quietly returning nothing.
 */
@Component
class JsoupPageReader implements PageReader {

	/** A ceiling on what one page may cost in requests, to the site and in run time. */
	private static final int MAX_CANDIDATES = 30;

	/** Shorter than this is navigation, a tag or a "more" link, not a headline. */
	private static final int MIN_ANCHOR_LENGTH = 30;

	/** Between the candidate fetches, so a source does not read the run as an attack. */
	private static final long PAUSE_MILLIS = 150;

	private final HttpFetcher fetcher;

	JsoupPageReader(HttpFetcher fetcher) {
		this.fetcher = fetcher;
	}

	@Override
	public SourceContent read(String url) {
		HttpFetcher.Fetched fetched = this.fetcher.get(toUri(url));
		if (!fetched.isOk()) {
			throw new SourceReadException("answered with status " + fetched.status());
		}
		Document document = Jsoup.parse(fetched.bodyAsText(), fetched.finalUri().toString());
		List<URI> candidates = candidateLinks(document, fetched.finalUri());
		List<SourceEntry> entries = new ArrayList<>();
		for (URI candidate : candidates) {
			readArticle(candidate).ifPresent(entries::add);
			pause();
		}
		if (entries.isEmpty()) {
			// Either the page loads its content with JavaScript, or the filter threw everything
			// away. Both look the same from here, and both are worth saying out loud: an admin
			// staring at an empty tab deserves better than silence.
			throw new SourceReadException("no articles found on this page");
		}
		return new SourceContent(titleOf(document, url), entries);
	}

	/**
	 * Every link that could be an article: on the same host, at least two path segments deep, and
	 * carrying enough anchor text to be a headline. Duplicates drop out, because an overview page
	 * links its lead story from the picture and from the headline both.
	 */
	private static List<URI> candidateLinks(Document document, URI base) {
		Set<URI> seen = new LinkedHashSet<>();
		for (Element anchor : document.select("a[href]")) {
			if (anchor.text().strip().length() < MIN_ANCHOR_LENGTH) {
				continue;
			}
			URI href = absolute(anchor.attr("abs:href"));
			if (href == null || !sameHost(href, base) || pathDepth(href) < 2) {
				continue;
			}
			seen.add(withoutFragment(href));
			if (seen.size() >= MAX_CANDIDATES) {
				break;
			}
		}
		return List.copyOf(seen);
	}

	/**
	 * Title, teaser and date out of the Open Graph tags. A candidate without a title was no
	 * article, but a topic page or a gallery or whatever else lives at that depth, and is dropped
	 * rather than stored as an entry with nothing in it.
	 */
	private Optional<SourceEntry> readArticle(URI uri) {
		HttpFetcher.Fetched fetched;
		try {
			fetched = this.fetcher.get(uri);
		} catch (SourceReadException e) {
			// One candidate that does not answer is no reason to give up on the page.
			return Optional.empty();
		}
		if (!fetched.isOk()) {
			return Optional.empty();
		}
		Document document = Jsoup.parse(fetched.bodyAsText(), uri.toString());
		String title = meta(document, "og:title");
		if (title.isBlank()) {
			return Optional.empty();
		}
		String link = uri.toString();
		return Optional.of(new SourceEntry(link, title, link,
				parsePublished(meta(document, "article:published_time")), meta(document, "og:description")));
	}

	private static String meta(Document document, String property) {
		Element element = document.selectFirst("meta[property=" + property + "]");
		if (element == null) {
			// Some sites write name= where the standard says property=; both mean the same thing.
			element = document.selectFirst("meta[name=" + property + "]");
		}
		return element == null ? "" : element.attr("content").strip();
	}

	/** The name the page gives itself, for what the sources page proposes as a name. */
	private static String titleOf(Document document, String fallback) {
		String siteName = meta(document, "og:site_name");
		if (!siteName.isBlank()) {
			return siteName;
		}
		String title = document.title().strip();
		return title.isBlank() ? fallback : title;
	}

	private static Instant parsePublished(String value) {
		if (value.isBlank()) {
			return null;
		}
		try {
			return OffsetDateTime.parse(value).toInstant();
		} catch (DateTimeParseException e) {
			try {
				return Instant.parse(value);
			} catch (DateTimeParseException ignored) {
				// A date nobody can read is no reason to lose the article; plenty of feeds carry
				// none either, and the entry says so with an empty one.
				return null;
			}
		}
	}

	private static boolean sameHost(URI candidate, URI base) {
		String left = stripWww(candidate.getHost());
		String right = stripWww(base.getHost());
		return left != null && left.equalsIgnoreCase(right);
	}

	private static String stripWww(String host) {
		return host == null ? null : host.startsWith("www.") ? host.substring(4) : host;
	}

	private static int pathDepth(URI uri) {
		String path = uri.getPath();
		if (path == null || path.isBlank()) {
			return 0;
		}
		return (int) Arrays.stream(path.split("/")).filter(segment -> !segment.isBlank()).count();
	}

	private static URI withoutFragment(URI uri) {
		if (uri.getFragment() == null) {
			return uri;
		}
		try {
			return new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), uri.getQuery(), null);
		} catch (URISyntaxException e) {
			return uri;
		}
	}

	private static URI absolute(String href) {
		if (href == null || href.isBlank()) {
			return null;
		}
		try {
			URI uri = new URI(href.strip());
			return uri.isAbsolute() && uri.getHost() != null ? uri : null;
		} catch (URISyntaxException e) {
			return null;
		}
	}

	private static void pause() {
		try {
			Thread.sleep(PAUSE_MILLIS);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new SourceReadException("reading the page was interrupted", e);
		}
	}

	private static URI toUri(String url) {
		try {
			return new URI(url.strip());
		} catch (URISyntaxException e) {
			throw new SourceReadException("this is no address", e);
		}
	}
}
