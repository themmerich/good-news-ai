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
 * Where news come from: one RSS or Atom feed, hanging on one leaf category.
 *
 * <p>The URL is unique per tenant, so the same source is fetched once however many people picked
 * it. The columns recording the last fetch exist in the schema already but are not mapped yet —
 * nothing fetches anything at this point, and a field that is always null only invites a column
 * in the table that is always empty.
 */
@Entity
@Table(name = "feeds")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Feed {

	@Id
	@UuidGenerator
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "tenant_id")
	private Tenant tenant;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "category_id")
	private Category category;

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String url;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	public Feed(Tenant tenant, Category category, String name, String url) {
		this.tenant = tenant;
		this.category = category;
		this.name = name;
		this.url = url;
		this.createdAt = Instant.now();
	}

	public void update(Category category, String name, String url) {
		this.category = category;
		this.name = name;
		this.url = url;
	}
}
