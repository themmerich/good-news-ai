package de.prime_ux.goodnews.news;

import static org.assertj.core.api.Assertions.assertThat;

import de.prime_ux.goodnews.TestcontainersConfiguration;
import de.prime_ux.goodnews.catalog.Feed;
import de.prime_ux.goodnews.catalog.FeedRepository;
import de.prime_ux.goodnews.catalog.SourceType;
import de.prime_ux.goodnews.reading.SourceEntry;
import de.prime_ux.goodnews.tenants.Tenant;
import de.prime_ux.goodnews.tenants.TenantRepository;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/** Matching what a source offers now against what it offered last time. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ArticleStoreTest {

	@Autowired
	private ArticleStore articleStore;

	@Autowired
	private ArticleRepository articleRepository;

	@Autowired
	private FeedRepository feedRepository;

	@Autowired
	private TenantRepository tenantRepository;

	private Feed kicker;

	@BeforeEach
	void cleanDatabaseAndCreateASource() {
		this.articleRepository.deleteAll();
		this.feedRepository.deleteAll();
		this.tenantRepository.deleteAll();
		Tenant tenant = this.tenantRepository.save(new Tenant("Musterfirma GmbH", "musterfirma"));
		this.kicker = this.feedRepository
				.save(new Feed(tenant, "kicker", "https://kicker.example/rss", SourceType.FEED));
	}

	@Test
	void storesWhatASourceOffersForTheFirstTime() {
		this.articleStore.reconcile(this.kicker, List.of(entry("a"), entry("b")));

		assertThat(this.articleRepository.findAllByFeedId(this.kicker.getId()))
				.extracting(Article::getGuid).containsExactlyInAnyOrder("a", "b");
	}

	/**
	 * The point of matching on the guid rather than replacing the lot. A story that is still
	 * there keeps whatever the AI made of it; rewriting it every run would throw the rating away
	 * and pay for it again.
	 */
	@Test
	void leavesAStoryThatIsStillThereExactlyWhereItWas() {
		this.articleStore.reconcile(this.kicker, List.of(entry("a")));
		Article first = this.articleRepository.findByFeedIdAndGuid(this.kicker.getId(), "a").orElseThrow();

		this.articleStore.reconcile(this.kicker, List.of(entry("a"), entry("b")));

		Article again = this.articleRepository.findByFeedIdAndGuid(this.kicker.getId(), "a").orElseThrow();
		assertThat(again.getId()).isEqualTo(first.getId());
		assertThat(again.getFetchedAt()).isEqualTo(first.getFetchedAt());
	}

	@Test
	void dropsAStoryThatHasFallenOutOfTheSource() {
		this.articleStore.reconcile(this.kicker, List.of(entry("a"), entry("b")));

		this.articleStore.reconcile(this.kicker, List.of(entry("b")));

		assertThat(this.articleRepository.findAllByFeedId(this.kicker.getId()))
				.extracting(Article::getGuid).containsExactly("b");
	}

	@Test
	void emptiesTheSourceWhenItOffersNothingAnyMore() {
		this.articleStore.reconcile(this.kicker, List.of(entry("a")));

		this.articleStore.reconcile(this.kicker, List.of());

		assertThat(this.articleRepository.findAllByFeedId(this.kicker.getId())).isEmpty();
	}

	/**
	 * Found by pointing a run at t-online, whose feed carries the same guid twice in one document.
	 * Two rows for one guid break the unique index, and with it the whole source went missing.
	 */
	@Test
	void takesAStoryOfferedTwiceInTheSameDocumentOnlyOnce() {
		this.articleStore.reconcile(this.kicker, List.of(entry("a"), entry("b"), entry("a")));

		assertThat(this.articleRepository.findAllByFeedId(this.kicker.getId()))
				.extracting(Article::getGuid).containsExactlyInAnyOrder("a", "b");
	}

	@Test
	void takesAnEntryWithoutATeaserOrADate() {
		this.articleStore.reconcile(this.kicker,
				List.of(new SourceEntry("a", "Ohne alles", "https://kicker.example/a", null, null)));

		Article stored = this.articleRepository.findByFeedIdAndGuid(this.kicker.getId(), "a").orElseThrow();
		assertThat(stored.getTeaser()).isEmpty();
		assertThat(stored.getPublishedAt()).isNull();
		assertThat(stored.isUnprocessed()).isTrue();
	}

	private static SourceEntry entry(String guid) {
		return new SourceEntry(guid, "Meldung " + guid, "https://kicker.example/" + guid,
				Instant.parse("2026-09-20T10:00:00Z"), "Ein Teaser.");
	}
}
