package de.prime_ux.goodnews.news;

import de.prime_ux.goodnews.catalog.Category;
import de.prime_ux.goodnews.catalog.Feed;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * One story, as it came out of a source and before the AI has seen it.
 *
 * <p>It hangs on the source, not on a person: everyone who reads that source sees the same
 * article, so it is fetched once and later rated once. Only a run writes here, and it writes the
 * whole of the untouched part at once, which is why there is no setter for a single field.
 */
@Entity
@Table(name = "articles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Article {

	@Id
	@UuidGenerator
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "feed_id")
	private Feed feed;

	/**
	 * Empty until the AI has placed the story, and empty afterwards too where it could place it
	 * nowhere. Both end up under "Sonstiges" on the board, which is the honest answer to a
	 * question that has not been settled.
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "category_id")
	private Category category;

	/** What the source calls this entry, the link where it names nothing else. */
	@Column(nullable = false)
	private String guid;

	@Column(nullable = false)
	private String title;

	@Column(nullable = false)
	private String link;

	/** Null where the source named no date, which plenty of them do not. */
	@Column(name = "published_at")
	private Instant publishedAt;

	@Column(nullable = false)
	private String teaser;

	@Column(name = "positive_summary")
	private String positiveSummary;

	/** Between 0 and 10 once it is there; null means the AI has not been round yet. */
	private Integer ranking;

	@Column(name = "processed_at")
	private Instant processedAt;

	@Column(name = "fetched_at", nullable = false)
	private Instant fetchedAt;

	public Article(Feed feed, String guid, String title, String link, Instant publishedAt, String teaser) {
		this.feed = feed;
		this.guid = guid;
		this.title = title;
		this.link = link;
		this.publishedAt = publishedAt;
		this.teaser = teaser;
		this.fetchedAt = Instant.now();
	}

	/** True while the AI has not been round, whatever it would have made of the story. */
	public boolean isUnprocessed() {
		return this.processedAt == null;
	}

	/**
	 * What the AI made of the story, written in one go because that is how it arrives.
	 *
	 * <p>An empty category is a real answer: the model was shown the list and placed the story
	 * nowhere. The story counts as processed regardless, so the next run does not pay for it
	 * again — what it lacks is a category, not an opinion.
	 */
	public void rate(Category category, String positiveSummary, Integer ranking) {
		this.category = category;
		this.positiveSummary = positiveSummary;
		this.ranking = ranking;
		this.processedAt = Instant.now();
	}
}
