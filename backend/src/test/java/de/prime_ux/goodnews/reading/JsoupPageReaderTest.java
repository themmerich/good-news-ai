package de.prime_ux.goodnews.reading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Making entries out of a page that offers no feed. No Spring context, no network. */
class JsoupPageReaderTest {

	private static final String OVERVIEW = "https://beispiel.example/nachrichten/";

	private final StubHttpFetcher fetcher = new StubHttpFetcher();
	private final PageReader reader = new JsoupPageReader(this.fetcher);

	@BeforeEach
	void prepareTheOverviewAndItsArticles() {
		this.fetcher.clear();
		this.fetcher.html(OVERVIEW, "news-overview.html");
		this.fetcher.html("https://beispiel.example/politik/haushalt-beschlossen", "article-with-og.html");
		this.fetcher.html("https://beispiel.example/sport/aufstieg-in-letzter-minute", "article-name-attribute.html");
		this.fetcher.html("https://beispiel.example/bilder/woche-in-bildern", "article-without-og.html");
		// beispiel.example/archiv/verschwundene-meldung is deliberately left unprepared: the stub
		// answers 404 for it, which is what a story taken offline looks like.
	}

	@Test
	void readsTheArticlesBehindAnOverviewPage() {
		SourceContent content = this.reader.read(OVERVIEW);

		assertThat(content.title()).isEqualTo("Beispiel Nachrichten");
		assertThat(content.entries()).extracting(SourceEntry::title)
				.containsExactly("Der Haushalt wurde nach langer Debatte beschlossen", "Aufstieg in letzter Minute");
		SourceEntry first = content.entries().getFirst();
		assertThat(first.link()).isEqualTo("https://beispiel.example/politik/haushalt-beschlossen");
		// Nothing better to hang identity on than the address, so that is what the guid is.
		assertThat(first.guid()).isEqualTo(first.link());
		assertThat(first.teaser()).startsWith("Nach drei Nächten Verhandlung");
		assertThat(first.publishedAt()).isEqualTo(Instant.parse("2026-09-20T16:45:00Z"));
	}

	@Test
	void readsOpenGraphTagsWrittenWithNameInsteadOfProperty() {
		SourceContent content = this.reader.read(OVERVIEW);

		SourceEntry sport = content.entries().getLast();
		assertThat(sport.title()).isEqualTo("Aufstieg in letzter Minute");
		assertThat(sport.teaser()).isEqualTo("Ein Tor in der Nachspielzeit entscheidet die Saison.");
		// The date on that page is not a date; losing it is better than losing the article.
		assertThat(sport.publishedAt()).isNull();
	}

	@Test
	void leavesOutWhatIsNoArticle() {
		this.reader.read(OVERVIEW);

		List<String> asked = this.fetcher.asked();
		// Navigation, because the anchor text is too short for a headline.
		assertThat(asked).doesNotContain("https://beispiel.example/sport/");
		// Another site, whatever the link says.
		assertThat(asked).doesNotContain("https://anderer.example/politik/fremde-meldung");
		// One path segment: a section rather than a story.
		assertThat(asked).doesNotContain("https://beispiel.example/wirtschaft");
	}

	@Test
	void asksTheSameStoryOnlyOnce() {
		this.reader.read(OVERVIEW);

		// The overview links its lead story from the picture and from the headline both.
		assertThat(this.fetcher.asked()).filteredOn("https://beispiel.example/politik/haushalt-beschlossen"::equals)
				.hasSize(1);
	}

	@Test
	void passesOverACandidateThatDoesNotAnswer() {
		SourceContent content = this.reader.read(OVERVIEW);

		// The archived story answers 404, and the rest of the page is read regardless.
		assertThat(this.fetcher.asked()).contains("https://beispiel.example/archiv/verschwundene-meldung");
		assertThat(content.entries()).hasSize(2);
	}

	@Test
	void passesOverACandidateWithoutAnOpenGraphTitle() {
		SourceContent content = this.reader.read(OVERVIEW);

		// The gallery answers, but names no title, so there is nothing to make an entry out of.
		assertThat(this.fetcher.asked()).contains("https://beispiel.example/bilder/woche-in-bildern");
		assertThat(content.entries()).noneMatch(entry -> entry.link().contains("woche-in-bildern"));
	}

	/**
	 * The one case worth saying out loud rather than answering with an empty list: a page that
	 * fills itself in with JavaScript is empty to a server, and an admin staring at an empty tab
	 * deserves to be told why.
	 */
	@Test
	void saysSoWhenAPageCarriesNoArticlesAtAll() {
		this.fetcher.html("https://leer.example/news/", "javascript-shell.html");

		assertThatThrownBy(() -> this.reader.read("https://leer.example/news/"))
				.isInstanceOf(SourceReadException.class)
				.hasMessageContaining("no articles found");
	}

	@Test
	void saysSoWhenThePageItselfDoesNotAnswer() {
		assertThatThrownBy(() -> this.reader.read("https://weg.example/news/"))
				.isInstanceOf(SourceReadException.class)
				.hasMessageContaining("404");
	}
}
