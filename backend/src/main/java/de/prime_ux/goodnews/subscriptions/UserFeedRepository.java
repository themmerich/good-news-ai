package de.prime_ux.goodnews.subscriptions;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserFeedRepository extends JpaRepository<UserFeed, UUID> {

	/** Just the feed ids: the pages ask what is picked, never for the rows themselves. */
	@Query("select uf.feed.id from UserFeed uf where uf.user.id = :userId")
	List<UUID> findFeedIdsByUserId(UUID userId);

	void deleteAllByUserId(UUID userId);
}
