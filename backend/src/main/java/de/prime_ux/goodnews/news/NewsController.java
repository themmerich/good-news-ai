package de.prime_ux.goodnews.news;

import de.prime_ux.goodnews.auth.CurrentSession;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The board and the button behind it, open to every signed-in user with a tenant. What a run
 * brings back belongs to the whole tenant, so there is nothing per-person here.
 */
@RestController
@RequestMapping("/api/news")
class NewsController {

	private final CurrentSession currentSession;
	private final NewsRunner newsRunner;
	private final NewsRunRepository runRepository;
	private final ArticleRepository articleRepository;

	NewsController(CurrentSession currentSession, NewsRunner newsRunner, NewsRunRepository runRepository,
			ArticleRepository articleRepository) {
		this.currentSession = currentSession;
		this.newsRunner = newsRunner;
		this.runRepository = runRepository;
		this.articleRepository = articleRepository;
	}

	/**
	 * Answers with a run either way: a fresh one, or the one already going. Pressing again while
	 * a pass is under way is not an error and not a second pass, whoever does the pressing.
	 */
	@PostMapping("/runs")
	ResponseEntity<RunResponse> startRun() {
		NewsRun run = this.newsRunner.start(this.currentSession.tenant());
		return ResponseEntity.accepted().body(RunResponse.from(run));
	}

	/** Runs of another tenant answer 404, as if they did not exist. */
	@GetMapping("/runs/{id}")
	@Transactional(readOnly = true)
	RunResponse getRun(@PathVariable UUID id) {
		return this.runRepository.findByIdAndTenantId(id, this.currentSession.tenant().getId())
				.map(RunResponse::from)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}

	/**
	 * Every story the tenant has, newest first and the unplaced among them. Which of them become
	 * tabs is the page's decision, because it already holds the categories the user ticked.
	 *
	 * <p>A story without a rating comes through at any threshold. An empty ranking is not a zero,
	 * it is an open question, and a threshold of 7 would otherwise swallow exactly those stories
	 * the AI failed to get to.
	 */
	@GetMapping("/articles")
	@Transactional(readOnly = true)
	List<ArticleResponse> getArticles(@RequestParam(defaultValue = "0") int minRanking) {
		return this.articleRepository.findAllOfTenant(this.currentSession.tenant().getId()).stream()
				.filter(article -> article.getRanking() == null || article.getRanking() >= minRanking)
				.sorted(Comparator.comparing(Article::getPublishedAt,
						Comparator.nullsLast(Comparator.reverseOrder())))
				.map(ArticleResponse::from)
				.toList();
	}
}
