package de.prime_ux.goodnews.news;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewsRunRepository extends JpaRepository<NewsRun, UUID> {

	/** The run in progress, if there is one. Asked on every press of the button. */
	Optional<NewsRun> findFirstByTenantIdAndStatusOrderByStartedAtDesc(UUID tenantId, RunStatus status);

	Optional<NewsRun> findByIdAndTenantId(UUID id, UUID tenantId);
}
