package de.prime_ux.goodnews.news;

import de.prime_ux.goodnews.catalog.SourceType;
import de.prime_ux.goodnews.reading.FeedReader;
import de.prime_ux.goodnews.reading.PageReader;
import de.prime_ux.goodnews.reading.SourceReader;
import org.springframework.stereotype.Component;

/**
 * Picks the reader a source needs. This is the last place in the application that knows the
 * difference between a feed and a page; past it there are only entries.
 *
 * <p>It sits here rather than next to the readers because the type belongs to the catalog, and
 * the reading package must not depend on the catalog — the catalog already depends on it.
 */
@Component
class SourceReaders {

	private final FeedReader feedReader;
	private final PageReader pageReader;

	SourceReaders(FeedReader feedReader, PageReader pageReader) {
		this.feedReader = feedReader;
		this.pageReader = pageReader;
	}

	SourceReader forType(SourceType type) {
		return type == SourceType.PAGE ? this.pageReader : this.feedReader;
	}
}
