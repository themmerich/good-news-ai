package de.prime_ux.goodnews.reading;

import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.FeedException;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Reads a feed with Rome, which understands RSS 0.9 through 2.0 and Atom behind one interface.
 *
 * <p>Fifty entries at most. A feed that offers more is offering an archive, and a run should not
 * spend its time — later its AI budget — on what nobody asked for.
 */
@Component
class RomeFeedReader implements FeedReader {

	private static final int MAX_ENTRIES = 50;

	private final HttpFetcher fetcher;

	RomeFeedReader(HttpFetcher fetcher) {
		this.fetcher = fetcher;
	}

	@Override
	public SourceContent read(String url) {
		HttpFetcher.Fetched fetched = this.fetcher.get(toUri(url));
		if (!fetched.isOk()) {
			throw new SourceReadException("answered with status " + fetched.status());
		}
		SyndFeed feed = parse(fetched.body());
		List<SourceEntry> entries = feed.getEntries().stream()
				.limit(MAX_ENTRIES)
				.map(RomeFeedReader::toEntry)
				.filter(entry -> !entry.link().isBlank())
				.toList();
		return new SourceContent(blankToFallback(feed.getTitle(), url), entries);
	}

	private static SyndFeed parse(byte[] body) {
		try {
			// XmlReader sorts out the encoding itself, which matters: feeds still go out as
			// ISO-8859-1, and the declaration in the XML is the only place that says so.
			return new SyndFeedInput().build(new XmlReader(new ByteArrayInputStream(body)));
		} catch (FeedException | IOException | IllegalArgumentException e) {
			throw new SourceReadException("this is no feed", e);
		}
	}

	private static SourceEntry toEntry(SyndEntry entry) {
		String link = blankToFallback(entry.getLink(), "");
		// The uri is what the feed calls the entry; plenty of feeds leave it out, and the link
		// then has to carry identity as well.
		String guid = blankToFallback(entry.getUri(), link);
		Instant publishedAt = entry.getPublishedDate() != null ? entry.getPublishedDate().toInstant()
				: entry.getUpdatedDate() != null ? entry.getUpdatedDate().toInstant() : null;
		return new SourceEntry(guid, blankToFallback(entry.getTitle(), "").strip(), link, publishedAt, teaserOf(entry));
	}

	/**
	 * The description where there is one, the first content block otherwise. Markup is dropped:
	 * what this is for is a couple of sentences of text, for a person and later for the model.
	 */
	private static String teaserOf(SyndEntry entry) {
		String raw = entry.getDescription() != null ? entry.getDescription().getValue()
				: entry.getContents().isEmpty() ? "" : entry.getContents().getFirst().getValue();
		return raw == null ? "" : org.jsoup.Jsoup.parse(raw).text().strip();
	}

	private static URI toUri(String url) {
		try {
			return new URI(url.strip());
		} catch (URISyntaxException e) {
			throw new SourceReadException("this is no address", e);
		}
	}

	private static String blankToFallback(String value, String fallback) {
		return value == null || value.isBlank() ? fallback : value;
	}
}
