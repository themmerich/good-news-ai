package de.prime_ux.goodnews.catalog;

import de.prime_ux.goodnews.tenants.Tenant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Where news come from: usually an RSS or Atom feed, a plain web page where the site offers none.
 *
 * <p>A source carries no category. It brings whatever its site publishes — politics, society and
 * sport through the same address — and which subject a single story belongs to is settled when
 * that story is read, not when the source is entered.
 *
 * <p>The URL is unique per tenant, so the same source is fetched once however many people read
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

	@Column(nullable = false)
	private String name;

	@Column(nullable = false)
	private String url;

	/** Which reader touches this source: an RSS or Atom feed, or a plain web page. */
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private SourceType type;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	public Feed(Tenant tenant, String name, String url, SourceType type) {
		this.tenant = tenant;
		this.name = name;
		this.url = url;
		this.type = type;
		this.createdAt = Instant.now();
	}

	public void update(String name, String url, SourceType type) {
		this.name = name;
		this.url = url;
		this.type = type;
	}
}
