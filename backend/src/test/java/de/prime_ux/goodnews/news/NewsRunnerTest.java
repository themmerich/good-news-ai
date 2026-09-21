package de.prime_ux.goodnews.news;

import static org.assertj.core.api.Assertions.assertThat;

import de.prime_ux.goodnews.TestcontainersConfiguration;
import de.prime_ux.goodnews.catalog.Feed;
import de.prime_ux.goodnews.catalog.FeedRepository;
import de.prime_ux.goodnews.catalog.SourceType;
import de.prime_ux.goodnews.reading.StubHttpFetcher;
import de.prime_ux.goodnews.tenants.Tenant;
import de.prime_ux.goodnews.tenants.TenantRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * One pass over a tenant's sources. The readers are the real ones; what is held still is the
 * network, so a feed and a page go through the same machinery they would in earnest.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class NewsRunnerTest {

	private static final String FEED_URL = "https://beispiel.example/rss";
	private static final String PAGE_URL = "https://beispiel.example/nachrichten/";

	@Autowired
	private NewsRunner newsRunner;

	@Autowired
	private NewsRunRepository runRepository;

	@Autowired
	private ArticleRepository articleRepository;

	@Autowired
	private FeedRepository feedRepository;

	@Autowired
	private TenantRepository tenantRepository;

	@Autowired
	private StubHttpFetcher fetcher;

	@Autowired
	private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

	private Tenant tenant;

	@BeforeEach
	void cleanDatabaseAndPrepareTheSources() {
		this.fetcher.clear();
		this.articleRepository.deleteAll();
		this.runRepository.deleteAll();
		this.feedRepository.deleteAll();
		this.tenantRepository.deleteAll();
		this.tenant = this.tenantRepository.save(new Tenant("Musterfirma GmbH", "musterfirma"));

		this.fetcher.feed(FEED_URL, "rss2.xml");
		this.fetcher.html(PAGE_URL, "news-overview.html");
		this.fetcher.html("https://beispiel.example/politik/haushalt-beschlossen", "article-with-og.html");
		this.fetcher.html("https://beispiel.example/sport/aufstieg-in-letzter-minute", "article-name-attribute.html");
		this.fetcher.html("https://beispiel.example/bilder/woche-in-bildern", "article-without-og.html");
	}

	@Test
	void bringsBackWhatAFeedAndAPageOfferInTheSamePass() {
		feed("Beispiel Feed", FEED_URL);
		page("Beispiel Seite", PAGE_URL);

		NewsRun run = awaitFinish(this.newsRunner.start(this.tenant));

		assertThat(run.getStatus()).isEqualTo(RunStatus.DONE);
		// Two out of the feed, two out of the page.
		assertThat(this.articleRepository.findAllOfTenant(this.tenant.getId())).hasSize(4);
		// Nothing has been rated, so everything still counts as waiting for the AI.
		assertThat(run.getTotalArticles()).isEqualTo(4);
		assertThat(run.getProcessedArticles()).isZero();
	}

	@Test
	void marksASourceThatCouldNotBeReadAndCarriesOn() {
		Feed broken = feed("Weg", "https://weg.example/rss");
		feed("Beispiel Feed", FEED_URL);

		awaitFinish(this.newsRunner.start(this.tenant));

		Feed afterwards = this.feedRepository.findById(broken.getId()).orElseThrow();
		assertThat(afterwards.hasError()).isTrue();
		assertThat(afterwards.getLastFetchedAt()).isNotNull();
		// The good source went through regardless; one moved address costs no tenant its news.
		assertThat(this.articleRepository.findAllOfTenant(this.tenant.getId())).hasSize(2);
	}

	@Test
	void clearsAnOldErrorOnceASourceAnswersAgain() {
		Feed feed = feed("Beispiel Feed", FEED_URL);
		feed.recordFailure("answered with status 500");
		this.feedRepository.save(feed);

		awaitFinish(this.newsRunner.start(this.tenant));

		assertThat(this.feedRepository.findById(feed.getId()).orElseThrow().hasError()).isFalse();
	}

	/**
	 * The pass in flight is written here rather than started: a pass over a stubbed feed is over
	 * in milliseconds, and a second start after the first one ended is quite correctly a second
	 * pass. Starting twice in a row would test how fast the machine is, not the rule.
	 */
	@Test
	void joinsTheRunAlreadyGoingRatherThanStartingASecond() {
		feed("Beispiel Feed", FEED_URL);
		NewsRun going = this.runRepository.save(new NewsRun(this.tenant));

		NewsRun joined = this.newsRunner.start(this.tenant);

		assertThat(joined.getId()).isEqualTo(going.getId());
		assertThat(this.runRepository.count()).isOne();
	}

	/**
	 * A pass that still says it is running a quarter of an hour on did not survive whatever
	 * happened to the server. Leaving it there would block the button for good.
	 */
	@Test
	void retiresARunThatWasAbandonedAndLetsTheNextOneThrough() {
		feed("Beispiel Feed", FEED_URL);
		UUID abandoned = abandonedRun();

		NewsRun fresh = this.newsRunner.start(this.tenant);

		assertThat(fresh.getId()).isNotEqualTo(abandoned);
		assertThat(this.runRepository.findById(abandoned).orElseThrow().getStatus()).isEqualTo(RunStatus.FAILED);
		awaitFinish(fresh);
	}

	@Test
	void finishesWithoutAnySourcesAtAll() {
		NewsRun run = awaitFinish(this.newsRunner.start(this.tenant));

		assertThat(run.getStatus()).isEqualTo(RunStatus.DONE);
		assertThat(run.getTotalArticles()).isZero();
	}

	private Feed feed(String name, String url) {
		return this.feedRepository.save(new Feed(this.tenant, name, url, SourceType.FEED));
	}

	private Feed page(String name, String url) {
		return this.feedRepository.save(new Feed(this.tenant, name, url, SourceType.PAGE));
	}

	/**
	 * A RUNNING row whose start lies further back than a pass may take. The clock is wound back
	 * through JDBC because the entity offers no way to set the start — nothing in the application
	 * has any business moving it, and a setter added for a test would say otherwise.
	 */
	private UUID abandonedRun() {
		NewsRun run = this.runRepository.saveAndFlush(new NewsRun(this.tenant));
		this.jdbcTemplate.update("update news_runs set started_at = ? where id = ?",
				java.sql.Timestamp.from(Instant.now().minus(NewsRun.STALE_AFTER).minusSeconds(60)), run.getId());
		return run.getId();
	}

	/** Polls until the pass is over, rather than guessing how long it takes. */
	private NewsRun awaitFinish(NewsRun started) {
		Instant deadline = Instant.now().plus(Duration.ofSeconds(30));
		while (Instant.now().isBefore(deadline)) {
			NewsRun current = this.runRepository.findById(started.getId()).orElseThrow();
			if (!current.isRunning()) {
				return current;
			}
			sleep();
		}
		throw new AssertionError("the run did not finish within 30 seconds");
	}

	private static void sleep() {
		try {
			Thread.sleep(50);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new AssertionError(e);
		}
	}
}
