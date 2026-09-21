package de.prime_ux.goodnews.catalog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

	/** One tenant's categories in the order the pages show them, and so the board its tabs. */
	List<Category> findAllByTenantIdOrderBySortOrderAscNameAsc(UUID tenantId);

	Optional<Category> findByIdAndTenantId(UUID id, UUID tenantId);

	boolean existsByTenantIdAndNameIgnoreCase(UUID tenantId, String name);
}
