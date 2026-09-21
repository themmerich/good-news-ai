package de.prime_ux.goodnews.catalog;

import de.prime_ux.goodnews.tenants.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

/**
 * A subject the news are sorted into: "Politik", "Soziales", "Sport".
 *
 * <p>The list is flat and, taken together, it is the vocabulary the AI picks from when it sorts
 * a single story. That is why it wants to stay short: from fifteen terms with clear meanings a
 * model chooses reliably, from fifty overlapping ones it does not. It is also why a category
 * carries no sources — one feed brings several subjects at once, and which one a story belongs to
 * is only known after it has been read.
 */
@Entity
@Table(name = "categories")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Category {

	@Id
	@UuidGenerator
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tenant_id")
	private Tenant tenant;

	@Column(nullable = false)
	private String name;

	/** Where the category sits in the list, and so which tab comes before which. */
	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	public Category(Tenant tenant, String name, int sortOrder) {
		this.tenant = tenant;
		this.name = name;
		this.sortOrder = sortOrder;
		this.createdAt = Instant.now();
	}

	public void rename(String name) {
		this.name = name;
	}

	public void moveTo(int sortOrder) {
		this.sortOrder = sortOrder;
	}
}
