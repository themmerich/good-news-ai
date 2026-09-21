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
 * A subject the news are sorted into: "Politik" with "Deutschland" and "International" under it,
 * or "Angular" standing on its own.
 *
 * <p>The tree is two levels deep at most. That is not a technical limit but the shape of the
 * product: a category without children is what a tab on the board is made of, and a third level
 * would have nowhere to show. The limit is guarded here as well as in the controller, so a second
 * entry point cannot quietly create a deeper tree.
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

	/** Null for a top-level category. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "parent_id")
	private Category parent;

	@Column(nullable = false)
	private String name;

	/** Where the category sits among its siblings; the admin drags it into place. */
	@Column(name = "sort_order", nullable = false)
	private int sortOrder;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	public Category(Tenant tenant, Category parent, String name, int sortOrder) {
		requireAssignableParent(parent);
		this.tenant = tenant;
		this.parent = parent;
		this.name = name;
		this.sortOrder = sortOrder;
		this.createdAt = Instant.now();
	}

	public boolean isTopLevel() {
		return this.parent == null;
	}

	public void rename(String name) {
		this.name = name;
	}

	/** Moves the category under a new parent — null for the top level — and to a new position. */
	public void moveTo(Category parent, int sortOrder) {
		requireAssignableParent(parent);
		if (parent != null && parent.getId().equals(this.id)) {
			throw new IllegalArgumentException("a category cannot be its own parent");
		}
		this.parent = parent;
		this.sortOrder = sortOrder;
	}

	public void moveTo(int sortOrder) {
		this.sortOrder = sortOrder;
	}

	/**
	 * The last line of defence for the two-level rule. The controller checks it first and answers
	 * 400; reaching this exception means something bypassed the controller.
	 */
	private static void requireAssignableParent(Category parent) {
		if (parent != null && !parent.isTopLevel()) {
			throw new IllegalArgumentException("categories are two levels deep at most");
		}
	}
}
