package de.prime_ux.goodnews.subscriptions;

import de.prime_ux.goodnews.catalog.Feed;
import de.prime_ux.goodnews.users.AppUser;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * One feed a user picked from the catalog. There is deliberately nothing here about categories: a
 * category counts as picked as soon as one of its feeds is, and storing that separately would be
 * the same truth written down twice.
 */
@Entity
@Table(name = "user_feeds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserFeed {

	@Id
	@UuidGenerator
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id")
	private AppUser user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "feed_id")
	private Feed feed;

	public UserFeed(AppUser user, Feed feed) {
		this.user = user;
		this.feed = feed;
	}
}
