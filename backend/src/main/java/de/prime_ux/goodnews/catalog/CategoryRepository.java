package de.prime_ux.goodnews.catalog;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, UUID> {

	/**
	 * The whole tree of one tenant in one query, in the order the pages show it: top-level
	 * categories first — they carry no parent and sort among themselves — then the children.
	 * The nesting is built from parentId by whoever needs it.
	 */
	List<Category> findAllByTenantIdOrderBySortOrderAscNameAsc(UUID tenantId);

	Optional<Category> findByIdAndTenantId(UUID id, UUID tenantId);

	List<Category> findAllByTenantIdAndParentIsNullOrderBySortOrderAscNameAsc(UUID tenantId);

	List<Category> findAllByTenantIdAndParentIdOrderBySortOrderAscNameAsc(UUID tenantId, UUID parentId);

	boolean existsByParentId(UUID parentId);

	boolean existsByTenantIdAndParentIsNullAndNameIgnoreCase(UUID tenantId, String name);

	boolean existsByTenantIdAndParentIdAndNameIgnoreCase(UUID tenantId, UUID parentId, String name);
}
