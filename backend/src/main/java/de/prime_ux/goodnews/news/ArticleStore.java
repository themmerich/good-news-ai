package de.prime_ux.goodnews.news;

import de.prime_ux.goodnews.catalog.Feed;
import de.prime_ux.goodnews.reading.SourceEntry;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Brings what a source offers now together with what it offered last time.
 *
 * <p>Only what currently stands in the source is kept. A story that has dropped out of it is
 * deleted, which is the smallest amount of data the app can hold and the reason no archive builds
 * up. The price is that a story which falls out and comes back later costs a second look from the
 * AI — rare enough to be worth the simplicity.
 *
 * <p>A story that is still there is left exactly as it is, with whatever the AI made of it. That
 * is the whole point of matching on the guid rather than replacing the lot: rewriting it every
 * run would throw the ratings away and pay for them again.
 */
@Component
public class ArticleStore {

	private final ArticleRepository articleRepository;

	ArticleStore(ArticleRepository articleRepository) {
		this.articleRepository = articleRepository;
	}

	/**
	 * @return the stories now standing under this source, the ones kept among them
	 */
	@Transactional
	public List<Article> reconcile(Feed feed, List<SourceEntry> entries) {
		Map<String, Article> known = new HashMap<>();
		for (Article article : this.articleRepository.findAllByFeedId(feed.getId())) {
			known.put(article.getGuid(), article);
		}
		// Keyed by guid rather than collected in order, because a source may offer the same entry
		// twice in one document — t-online does — and the guid is what says two entries are one
		// story. Without this the whole source is lost to a unique constraint on the way in.
		Map<String, Article> current = new LinkedHashMap<>();
		for (SourceEntry entry : entries) {
			if (current.containsKey(entry.guid())) {
				continue;
			}
			Article kept = known.remove(entry.guid());
			current.put(entry.guid(), kept != null ? kept : newArticle(feed, entry));
		}
		// Whatever is left in the map was not offered this time round.
		this.articleRepository.deleteAll(known.values());
		return this.articleRepository.saveAll(current.values());
	}

	private static Article newArticle(Feed feed, SourceEntry entry) {
		return new Article(feed, entry.guid(), entry.title(), entry.link(), entry.publishedAt(),
				entry.teaser() == null ? "" : entry.teaser());
	}
}
