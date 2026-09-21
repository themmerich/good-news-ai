package de.prime_ux.goodnews.reading;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

/**
 * Finds the feeds belonging to an ordinary web address.
 *
 * <p>Hardly anybody knows a feed address by heart, and sites do their best to hide them: of four
 * German news sites checked while this was written, exactly one linked its feed where the standard
 * says it should be. So the search tries four things in turn and confirms every find by reading
 * it — a guess that cannot be parsed is not a feed, whatever its address suggests.
 *
 * <p>Scanning the body of a page that answered 404 looks odd and is deliberate: heise names both
 * of its feeds in the footer of its error page and nowhere on its front page. A site's furniture
 * is often more honest than its front door.
 */
@Component
public class FeedFinder {

	/** Tried on the address itself; the first that parses wins, the others still get a look. */
	private static final List<String> COMMON_PATHS = List.of(
			"/feed", "/feed/", "/rss", "/rss/", "/rss.xml", "/feed.xml", "/atom.xml", "/index.xml", "/?feed=rss2");

	/** Golem keeps its feeds on one of these and mentions them nowhere a server can see. */
	private static final List<String> COMMON_HOSTS = List.of("rss.", "feeds.");

	/** A ceiling on how much one search may cost the sites it asks, and us. */
	private static final int MAX_CANDIDATES = 12;

	private final HttpFetcher fetcher;
	private final FeedReader feedReader;

	FeedFinder(HttpFetcher fetcher, FeedReader feedReader) {
		this.fetcher = fetcher;
		this.feedReader = feedReader;
	}

	public List<FoundFeed> find(String address) {
		URI start = toUri(address);

		// 1. The address may already be a feed — then there is nothing to search for.
		Optional<FoundFeed> itself = readFeed(start.toString());
		if (itself.isPresent()) {
			return List.of(itself.get());
		}

		// 2. What the page itself says, in the head and in its links.
		LinkedHashSet<String> candidates = new LinkedHashSet<>(fromPage(start));

		// 3. Where feeds usually sit. The answers are looked at twice: as a feed, and — when they
		//    are a page, error page or not — for addresses that look like one.
		List<FoundFeed> found = new ArrayList<>();
		for (Probe probe : inParallel(probeTargets(start), this::probe)) {
			probe.feed().ifPresent(found::add);
			candidates.addAll(probe.candidates());
		}

		// 4. Confirm what is left by reading it.
		List<String> unconfirmed = candidates.stream()
				.filter(candidate -> found.stream().noneMatch(feed -> feed.url().equals(candidate)))
				.limit(MAX_CANDIDATES)
				.toList();
		for (Optional<FoundFeed> feed : inParallel(unconfirmed, this::readFeed)) {
			feed.ifPresent(found::add);
		}

		return dedupe(found);
	}

	private record Probe(Optional<FoundFeed> feed, List<String> candidates) {
	}

	private Probe probe(URI target) {
		Optional<FoundFeed> feed = readFeed(target.toString());
		if (feed.isPresent()) {
			return new Probe(feed, List.of());
		}
		return new Probe(Optional.empty(), List.copyOf(fromPage(target)));
	}

	/** Every address on the page that could be a feed: the declared one first, then the links. */
	private LinkedHashSet<String> fromPage(URI page) {
		LinkedHashSet<String> candidates = new LinkedHashSet<>();
		HttpFetcher.Fetched fetched;
		try {
			fetched = this.fetcher.get(page);
		} catch (SourceReadException e) {
			return candidates;
		}
		if (fetched.body().length == 0) {
			return candidates;
		}
		Document document = Jsoup.parse(fetched.bodyAsText(), fetched.finalUri().toString());
		for (Element link : document.select("link[rel~=(?i)alternate][type~=(?i)(rss|atom|xml)]")) {
			addIfHttp(candidates, link.absUrl("href"));
		}
		for (Element anchor : document.select("a[href]")) {
			String href = anchor.absUrl("href");
			if (looksLikeFeed(href)) {
				addIfHttp(candidates, href);
			}
		}
		return candidates;
	}

	private List<URI> probeTargets(URI start) {
		List<URI> targets = new ArrayList<>();
		for (String path : COMMON_PATHS) {
			resolve(start, path).ifPresent(targets::add);
		}
		String host = start.getHost();
		if (host != null) {
			for (String prefix : COMMON_HOSTS) {
				if (!host.startsWith(prefix)) {
					try {
						targets.add(new URI(start.getScheme(), prefix + host, "/", null));
					} catch (URISyntaxException e) {
						// A host that cannot carry the prefix is simply not tried.
					}
				}
			}
		}
		return targets;
	}

	private Optional<FoundFeed> readFeed(String url) {
		try {
			SourceContent content = this.feedReader.read(url);
			// An address that parses but holds nothing is not worth offering: it is either broken
			// or an archive nobody wants.
			return content.entries().isEmpty() ? Optional.empty()
					: Optional.of(new FoundFeed(url, content.title(), content.entries().size()));
		} catch (SourceReadException e) {
			return Optional.empty();
		}
	}

	/**
	 * Runs the lookups side by side. One after another, a search would cost the sum of every
	 * timeout it runs into — a dozen dead guesses at ten seconds each is not an answer anybody
	 * waits for.
	 */
	private static <T, R> List<R> inParallel(List<T> inputs, Function<T, R> work) {
		if (inputs.isEmpty()) {
			return List.of();
		}
		try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
			List<Future<R>> futures = inputs.stream().map(input -> executor.submit(() -> work.apply(input))).toList();
			List<R> results = new ArrayList<>(futures.size());
			for (Future<R> future : futures) {
				try {
					results.add(future.get());
				} catch (java.util.concurrent.ExecutionException e) {
					// One dead guess says nothing about the others.
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					break;
				}
			}
			return results;
		}
	}

	private static List<FoundFeed> dedupe(List<FoundFeed> found) {
		LinkedHashSet<String> seen = new LinkedHashSet<>();
		List<FoundFeed> unique = new ArrayList<>();
		for (FoundFeed feed : found) {
			if (seen.add(feed.url())) {
				unique.add(feed);
			}
		}
		return List.copyOf(unique);
	}

	private static boolean looksLikeFeed(String url) {
		String lower = url.toLowerCase(java.util.Locale.ROOT);
		return lower.endsWith(".xml") || lower.endsWith(".rdf") || lower.contains("rss") || lower.contains("/feed")
				|| lower.contains("atom");
	}

	private static void addIfHttp(LinkedHashSet<String> candidates, String url) {
		if (url.startsWith("http://") || url.startsWith("https://")) {
			candidates.add(url);
		}
	}

	private static Optional<URI> resolve(URI base, String path) {
		try {
			return Optional.of(base.resolve(path));
		} catch (IllegalArgumentException e) {
			return Optional.empty();
		}
	}

	/** A bare domain is what people paste, so a missing scheme is filled in rather than refused. */
	private static URI toUri(String address) {
		String trimmed = address.strip();
		String withScheme = trimmed.startsWith("http://") || trimmed.startsWith("https://") ? trimmed
				: "https://" + trimmed;
		try {
			URI uri = new URI(withScheme);
			if (uri.getHost() == null) {
				throw new SourceReadException("this is no address");
			}
			// A bare domain carries no path, and "https://site.example" is then a different string
			// from "https://site.example/" — which matters, because candidates are told apart by
			// their address. A client asks for "/" anyway, so it is spelled out here once.
			return uri.getPath() == null || uri.getPath().isEmpty() ? uri.resolve("/") : uri;
		} catch (URISyntaxException e) {
			throw new SourceReadException("this is no address", e);
		}
	}
}
