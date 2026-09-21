package de.prime_ux.goodnews.subscriptions;

import de.prime_ux.goodnews.catalog.Category;
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
 * One category a user wants to see on the board. There is deliberately nothing here about
 * sources: a run works through everything the tenant has, because a story's category is only
 * known once the AI has rated it, so picking sources would save nothing and only pretend to.
 */
@Entity
@Table(name = "user_categories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserCategory {

	@Id
	@UuidGenerator
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id")
	private AppUser user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "category_id")
	private Category category;

	public UserCategory(AppUser user, Category category) {
		this.user = user;
		this.category = category;
	}
}
