package de.prime_ux.goodnews.news;

import de.prime_ux.goodnews.tenants.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * One pass over every source a tenant has.
 *
 * <p>It belongs to the tenant rather than to whoever pressed the button: the articles it brings
 * back and their ratings belong to everyone, so a second press while one is running joins that
 * run instead of starting a second pass over the same sources.
 */
@Entity
@Table(name = "news_runs")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NewsRun {

	/**
	 * After this a run that still says RUNNING is taken as dead — the usual cause being a server
	 * that was restarted mid-pass. Without it one crash would block the button for good.
	 */
	public static final Duration STALE_AFTER = Duration.ofMinutes(15);

	@Id
	@UuidGenerator
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tenant_id")
	private Tenant tenant;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private RunStatus status;

	@Column(name = "total_articles", nullable = false)
	private int totalArticles;

	@Column(name = "processed_articles", nullable = false)
	private int processedArticles;

	@Column(name = "started_at", nullable = false)
	private Instant startedAt;

	@Column(name = "finished_at")
	private Instant finishedAt;

	private String error;

	public NewsRun(Tenant tenant) {
		this.tenant = tenant;
		this.status = RunStatus.RUNNING;
		this.startedAt = Instant.now();
	}

	public void countArticles(int total) {
		this.totalArticles = total;
	}

	public void advanceBy(int processed) {
		this.processedArticles += processed;
	}

	/**
	 * A run is done even when single sources or single batches failed along the way. Each of those
	 * says so in its own place — the source carries its error, the article stays unrated — and
	 * calling the whole pass a failure over one broken feed would tell the user nothing useful.
	 */
	public void finish() {
		this.status = RunStatus.DONE;
		this.finishedAt = Instant.now();
	}

	public void fail(String error) {
		this.status = RunStatus.FAILED;
		this.error = error;
		this.finishedAt = Instant.now();
	}

	public boolean isRunning() {
		return this.status == RunStatus.RUNNING;
	}

	public boolean isStale(Instant now) {
		return isRunning() && this.startedAt.isBefore(now.minus(STALE_AFTER));
	}
}
