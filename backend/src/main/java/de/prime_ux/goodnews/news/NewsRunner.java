package de.prime_ux.goodnews.news;

import de.prime_ux.goodnews.catalog.Feed;
import de.prime_ux.goodnews.catalog.FeedRepository;
import de.prime_ux.goodnews.reading.SourceContent;
import de.prime_ux.goodnews.reading.SourceReadException;
import de.prime_ux.goodnews.tenants.Tenant;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * One pass over every source a tenant has: fetch, match against what is already stored, and
 * afterwards hand the new stories to the AI.
 *
 * <p>The pass runs on its own thread because it takes up to a minute and no HTTP response should
 * be held open that long. The button therefore answers with the id of a run rather than with a
 * result, and the page asks after it.
 *
 * <p>A broken source does not stop the pass. It records what went wrong against itself, the
 * sources page shows that, and the run carries on — one feed that moved its address should not
 * cost a tenant its news.
 */
@Component
public class NewsRunner {

	private static final Logger log = LoggerFactory.getLogger(NewsRunner.class);

	private final NewsRunRepository runRepository;
	private final FeedRepository feedRepository;
	private final ArticleRepository articleRepository;
	private final ArticleStore articleStore;
	private final SourceReaders readers;
	private final TaskExecutor executor;

	// Named, because the context holds two of these: the one Boot sets up for the application and
	// the scheduler's own. A pass that took a minute has no business on the scheduler thread.
	NewsRunner(NewsRunRepository runRepository, FeedRepository feedRepository, ArticleRepository articleRepository,
			ArticleStore articleStore, SourceReaders readers,
			@Qualifier("applicationTaskExecutor") TaskExecutor executor) {
		this.runRepository = runRepository;
		this.feedRepository = feedRepository;
		this.articleRepository = articleRepository;
		this.articleStore = articleStore;
		this.readers = readers;
		this.executor = executor;
	}

	/**
	 * Starts a pass, or joins the one already under way. Either way the caller gets an id to ask
	 * after, so pressing the button twice looks the same from the page as pressing it once.
	 */
	public NewsRun start(Tenant tenant) {
		NewsRun joined = runningRun(tenant.getId());
		if (joined != null) {
			return joined;
		}
		NewsRun run;
		try {
			run = this.runRepository.save(new NewsRun(tenant));
		} catch (DataIntegrityViolationException e) {
			// Somebody else got there in the same instant; the partial unique index caught it.
			NewsRun theirs = runningRun(tenant.getId());
			if (theirs == null) {
				throw e;
			}
			return theirs;
		}
		UUID runId = run.getId();
		UUID tenantId = tenant.getId();
		this.executor.execute(() -> work(runId, tenantId));
		return run;
	}

	/**
	 * The run in flight, having first retired one that is past saving. A pass that still says it
	 * is running a quarter of an hour on has not survived whatever happened to the server, and
	 * leaving it there would block the button for good.
	 */
	private NewsRun runningRun(UUID tenantId) {
		NewsRun running = this.runRepository
				.findFirstByTenantIdAndStatusOrderByStartedAtDesc(tenantId, RunStatus.RUNNING)
				.orElse(null);
		if (running == null) {
			return null;
		}
		if (!running.isStale(Instant.now())) {
			return running;
		}
		running.fail("the run was abandoned, most likely because the server restarted");
		this.runRepository.save(running);
		return null;
	}

	private void work(UUID runId, UUID tenantId) {
		try {
			for (Feed feed : this.feedRepository.findAllOfTenant(tenantId)) {
				fetch(feed);
			}
			NewsRun run = this.runRepository.findById(runId).orElseThrow();
			run.countArticles(unprocessedCount(tenantId));
			process(run);
			run.finish();
			this.runRepository.save(run);
		} catch (RuntimeException e) {
			log.error("the run {} did not finish", runId, e);
			this.runRepository.findById(runId).ifPresent(run -> {
				run.fail(e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
				this.runRepository.save(run);
			});
		}
	}

	private void fetch(Feed feed) {
		try {
			SourceContent content = this.readers.forType(feed.getType()).read(feed.getUrl());
			this.articleStore.reconcile(feed, content.entries());
			feed.recordFetch();
		} catch (SourceReadException e) {
			feed.recordFailure(e.getMessage());
		} catch (RuntimeException e) {
			// Anything the readers did not think to wrap. It still belongs against the source
			// rather than against the run: the next one may well get through.
			log.warn("reading the source {} went wrong in a way it did not account for", feed.getId(), e);
			feed.recordFailure(e.getClass().getSimpleName());
		}
		this.feedRepository.save(feed);
	}

	private int unprocessedCount(UUID tenantId) {
		return (int) this.articleRepository.findAllOfTenant(tenantId).stream()
				.filter(Article::isUnprocessed)
				.count();
	}

	/**
	 * Where the AI stage goes. Empty for now, which is why every story sits on the board under
	 * "Sonstiges" without a ranking: the fetching is what this step promises, and the rating is
	 * the next one.
	 */
	private void process(NewsRun run) {
		// Deliberately nothing yet.
	}
}
