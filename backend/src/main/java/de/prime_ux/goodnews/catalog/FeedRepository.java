package de.prime_ux.goodnews.catalog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FeedRepository extends JpaRepository<Feed, UUID> {

	/** The category comes along eagerly: the responses name it, and a lazy proxy could not. */
	@Query("select f from Feed f join fetch f.category where f.tenant.id = :tenantId order by lower(f.name)")
	List<Feed> findAllOfTenant(UUID tenantId);

	Optional<Feed> findByIdAndTenantId(UUID id, UUID tenantId);

	boolean existsByTenantIdAndUrlIgnoreCase(UUID tenantId, String url);

	boolean existsByCategoryId(UUID categoryId);

	/** How many feeds hang on each category, one query however many categories. */
	@Query("select f.category.id as categoryId, count(f) as count from Feed f where f.tenant.id = :tenantId"
			+ " group by f.category.id")
	List<CategoryFeedCount> countPerCategory(UUID tenantId);
}
