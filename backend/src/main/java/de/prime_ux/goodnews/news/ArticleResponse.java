package de.prime_ux.goodnews.news;

import java.time.Instant;
import java.util.UUID;

/**
 * One story as the board shows it. The list arrives flat and is grouped where it is displayed:
 * the same articles feed the tabs and the ranking threshold, and grouping on the server would
 * mean the same rule living in two places.
 *
 * <p>An empty {@code categoryId} is the story the AI has not placed — either because it has not
 * seen it yet or because nothing fitted. The board collects those under "Sonstiges".
 */
public record ArticleResponse(UUID id, UUID categoryId, String categoryName, String sourceName, String title,
		String link, Instant publishedAt, String teaser, String positiveSummary, Integer ranking) {

	static ArticleResponse from(Article article) {
		boolean placed = article.getCategory() != null;
		return new ArticleResponse(article.getId(), placed ? article.getCategory().getId() : null,
				placed ? article.getCategory().getName() : null, article.getFeed().getName(), article.getTitle(),
				article.getLink(), article.getPublishedAt(), article.getTeaser(), article.getPositiveSummary(),
				article.getRanking());
	}
}
