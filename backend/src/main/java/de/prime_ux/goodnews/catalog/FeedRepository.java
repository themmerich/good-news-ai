package de.prime_ux.goodnews.catalog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface FeedRepository extends JpaRepository<Feed, UUID> {

	@Query("select f from Feed f where f.tenant.id = :tenantId order by lower(f.name)")
	List<Feed> findAllOfTenant(UUID tenantId);

	Optional<Feed> findByIdAndTenantId(UUID id, UUID tenantId);

	boolean existsByTenantIdAndUrlIgnoreCase(UUID tenantId, String url);
}
