package de.prime_ux.goodnews.subscriptions;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserCategoryRepository extends JpaRepository<UserCategory, UUID> {

	/** Just the category ids: the pages ask what is ticked, never for the rows themselves. */
	@Query("select uc.category.id from UserCategory uc where uc.user.id = :userId")
	List<UUID> findCategoryIdsByUserId(UUID userId);

	void deleteAllByUserId(UUID userId);
}
