package de.prime_ux.goodnews.news;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ArticleRepository extends JpaRepository<Article, UUID> {

	/** Everything one source brought back last time, for matching against what it offers now. */
	List<Article> findAllByFeedId(UUID feedId);

	/**
	 * The board in one query. Source and category come along eagerly because the response names
	 * both, and a lazy proxy outside the transaction could not.
	 */
	@Query("select a from Article a join fetch a.feed f left join fetch a.category"
			+ " where f.tenant.id = :tenantId")
	List<Article> findAllOfTenant(UUID tenantId);

	Optional<Article> findByFeedIdAndGuid(UUID feedId, String guid);

	/**
	 * What the AI still has to look at. The source comes along eagerly because the prompt names
	 * it, and oldest first so a run that is cut short has worked through the backlog rather than
	 * skimming the top of it repeatedly.
	 */
	@Query("select a from Article a join fetch a.feed f where f.tenant.id = :tenantId"
			+ " and a.processedAt is null order by a.fetchedAt")
	List<Article> findUnprocessedOfTenant(UUID tenantId);
}
