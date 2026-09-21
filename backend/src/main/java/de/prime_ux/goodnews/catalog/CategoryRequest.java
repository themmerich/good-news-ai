package de.prime_ux.goodnews.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Create and update share the same shape. An empty {@code parentId} means the top level.
 *
 * <p>{@code sortOrder} is the position among the new siblings, counted from zero. Left empty, the
 * category goes to the end — which is what creating one should do, and what a rename should leave
 * alone. A drag in the tree sends name, parent and position together, so one move is one request.
 */
record CategoryRequest(@NotBlank @Size(max = 100) String name, UUID parentId,
		@PositiveOrZero Integer sortOrder) {

	String trimmedName() {
		return name.trim();
	}
}
