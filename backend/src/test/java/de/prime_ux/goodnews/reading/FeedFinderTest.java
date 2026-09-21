package de.prime_ux.goodnews.reading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * The four ways a feed is found. Each one is a promise this class makes and therefore gets its own
 * test; together they are the reason the search exists at all, since sites stopped advertising
 * their feeds.
 */
class FeedFinderTest {

	private final StubHttpFetcher fetcher = new StubHttpFetcher();
	private final FeedFinder finder = new FeedFinder(this.fetcher, new RomeFeedReader(this.fetcher));

	@BeforeEach
	void clearAnswers() {
		this.fetcher.clear();
	}

	@Test
	void takesTheAddressItselfWhenItIsAlreadyAFeed() {
		this.fetcher.feed("https://beispiel.example/rss.xml", "rss2.xml");

		List<FoundFeed> found = this.finder.find("https://beispiel.example/rss.xml");

		assertThat(found).singleElement().satisfies(feed -> {
			assertThat(feed.url()).isEqualTo("https://beispiel.example/rss.xml");
			assertThat(feed.title()).isEqualTo("Beispiel News");
			assertThat(feed.entryCount()).isEqualTo(2);
		});
		// Nothing else was tried: there was nothing left to look for.
		assertThat(this.fetcher.asked()).containsExactly("https://beispiel.example/rss.xml");
	}

	@Test
	void followsWhatThePageDeclaresInItsHead() {
		this.fetcher.html("https://beispiel.example/", "declares-feed.html");
		this.fetcher.feed("https://beispiel.example/feed.rss", "rss2.xml");

		List<FoundFeed> found = this.finder.find("https://beispiel.example/");

		assertThat(found).extracting(FoundFeed::url).contains("https://beispiel.example/feed.rss");
	}

	@Test
	void findsFeedsNamedOnlyInTheFurnitureOfAnErrorPage() {
		// The front page says nothing, but every error page carries the site's footer — and that
		// is where the feeds are named. This is the real behaviour of a large German news site.
		this.fetcher.html("https://versteckt.example/", "silent-home.html");
		this.fetcher.htmlWithStatus("https://versteckt.example/rss", 404, "error-page-with-feed-links.html");
		this.fetcher.feed("https://versteckt.example/rss/alles-atom.xml", "atom.xml");
		this.fetcher.feed("https://versteckt.example/rss/alles.rdf", "rss2.xml");

		List<FoundFeed> found = this.finder.find("versteckt.example");

		assertThat(found).extracting(FoundFeed::url)
				.containsExactlyInAnyOrder("https://versteckt.example/rss/alles-atom.xml",
						"https://versteckt.example/rss/alles.rdf");
	}

	@Test
	void triesTheHostThatFeedsUsuallySitOn() {
		this.fetcher.html("https://ausgelagert.example/", "silent-home.html");
		this.fetcher.feed("https://rss.ausgelagert.example/", "rss2.xml");

		List<FoundFeed> found = this.finder.find("https://ausgelagert.example/");

		assertThat(found).extracting(FoundFeed::url).containsExactly("https://rss.ausgelagert.example/");
	}

	@Test
	void answersWithNothingWhereThereIsNothing() {
		this.fetcher.html("https://ohne.example/", "silent-home.html");

		assertThat(this.finder.find("https://ohne.example/")).isEmpty();
	}

	@Test
	void answersWithNothingForAPageThatIsFilledInTheBrowser() {
		// For a server such a page is an empty shell. Saying so is the point: the sources page
		// then offers to read the site directly, which will not help here either, but the person
		// finds out now rather than after a run that brought nothing back.
		this.fetcher.html("https://spa.example/", "javascript-shell.html");

		assertThat(this.finder.find("https://spa.example/")).isEmpty();
	}

	@Test
	void doesNotOfferAFeedThatHoldsNothing() {
		this.fetcher.html("https://leer.example/", "declares-feed.html");
		this.fetcher.feed("https://leer.example/feed.rss", "empty-feed.xml");

		// It parses, so it is a feed — but an empty one is either broken or an archive, and
		// offering it would only lead to a tab that never fills.
		assertThat(this.finder.find("https://leer.example/")).isEmpty();
	}

	@Test
	void offersEveryDistinctFeedOnlyOnce() {
		// Declared in the head and linked in the footer: one feed, named twice.
		this.fetcher.html("https://doppelt.example/", "declares-feed.html");
		this.fetcher.htmlWithStatus("https://doppelt.example/rss", 404, "declares-feed.html");
		this.fetcher.feed("https://doppelt.example/feed.rss", "rss2.xml");

		assertThat(this.finder.find("https://doppelt.example/")).hasSize(1);
	}

	@Test
	void fillsInTheSchemeAndTheSlashSomebodyLeftOff() {
		this.fetcher.html("https://nackt.example/", "declares-feed.html");
		this.fetcher.feed("https://nackt.example/feed.rss", "rss2.xml");

		// A bare domain has no path, and without the slash the front page is a different address
		// than the one anybody would have prepared — including the site itself.
		assertThat(this.finder.find("  nackt.example  ")).extracting(FoundFeed::url)
				.containsExactly("https://nackt.example/feed.rss");
	}

	@Test
	void refusesSomethingThatIsNoAddress() {
		assertThatThrownBy(() -> this.finder.find("kein komma url"))
				.isInstanceOf(SourceReadException.class)
				.hasMessageContaining("no address");
	}
}
