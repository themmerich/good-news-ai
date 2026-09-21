package de.prime_ux.goodnews.reading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Reading a feed. No Spring context, no network. */
class RomeFeedReaderTest {

	private final StubHttpFetcher fetcher = new StubHttpFetcher();
	private final FeedReader reader = new RomeFeedReader(this.fetcher);

	@BeforeEach
	void clearAnswers() {
		this.fetcher.clear();
	}

	@Test
	void readsAnRssFeedWithItsTitleAndEntries() {
		this.fetcher.feed("https://beispiel.example/rss", "rss2.xml");

		SourceContent content = this.reader.read("https://beispiel.example/rss");

		assertThat(content.title()).isEqualTo("Beispiel News");
		assertThat(content.entries()).hasSize(2);
		SourceEntry first = content.entries().getFirst();
		assertThat(first.title()).isEqualTo("Erste Meldung");
		assertThat(first.link()).isEqualTo("https://beispiel.example/artikel/eins");
		assertThat(first.guid()).isEqualTo("beispiel-1");
		assertThat(first.publishedAt()).isEqualTo(Instant.parse("2026-09-15T06:30:00Z"));
	}

	@Test
	void takesTheMarkupOutOfATeaser() {
		this.fetcher.feed("https://beispiel.example/rss", "rss2.xml");

		SourceContent content = this.reader.read("https://beispiel.example/rss");

		// What this is for is a couple of sentences, for a person and later for the model.
		assertThat(content.entries().getFirst().teaser())
				.isEqualTo("Ein Teaser mit Markup, das nicht durchkommen soll.");
	}

	@Test
	void fallsBackToTheLinkWhereAnEntryNamesNoIdentity() {
		this.fetcher.feed("https://beispiel.example/rss", "rss2.xml");

		SourceEntry second = this.reader.read("https://beispiel.example/rss").entries().get(1);

		// Without a guid the link has to carry identity across runs — plenty of feeds leave it out.
		assertThat(second.guid()).isEqualTo("https://beispiel.example/artikel/zwei");
		assertThat(second.publishedAt()).isNull();
	}

	@Test
	void readsAtomThroughTheSameDoor() {
		this.fetcher.feed("https://beispiel.example/atom", "atom.xml");

		SourceContent content = this.reader.read("https://beispiel.example/atom");

		assertThat(content.title()).isEqualTo("Beispiel Atom");
		assertThat(content.entries()).hasSize(1);
		assertThat(content.entries().getFirst().link()).isEqualTo("https://beispiel.example/atom/eins");
	}

	@Test
	void saysPlainlyWhenSomethingIsNoFeed() {
		this.fetcher.html("https://beispiel.example/", "silent-home.html");
		this.fetcher.feed("https://beispiel.example/kaputt", "broken.xml");

		assertThatThrownBy(() -> this.reader.read("https://beispiel.example/"))
				.isInstanceOf(SourceReadException.class)
				.hasMessageContaining("no feed");
		assertThatThrownBy(() -> this.reader.read("https://beispiel.example/kaputt"))
				.isInstanceOf(SourceReadException.class)
				.hasMessageContaining("no feed");
	}

	@Test
	void namesTheStatusWhenTheAddressAnswersWithAnError() {
		assertThatThrownBy(() -> this.reader.read("https://beispiel.example/weg"))
				.isInstanceOf(SourceReadException.class)
				.hasMessageContaining("404");
	}

	@Test
	void refusesSomethingThatIsNoAddress() {
		assertThatThrownBy(() -> this.reader.read("kein komma url"))
				.isInstanceOf(SourceReadException.class);
	}
}
