package de.prime_ux.goodnews.catalog;

import java.util.UUID;

/**
 * One category as the admin page lists it. The list is flat and carries {@code parentId}; the
 * nesting is built where it is displayed. Flat travels better through JSON and is far easier to
 * assert in a test than a shape that nests itself.
 *
 * <p>{@code feedCount} counts only this category's own feeds, never a child's — a top-level
 * category carries none by design.
 */
public record CategoryResponse(UUID id, UUID parentId, String name, int sortOrder, long feedCount) {

	static CategoryResponse from(Category category, long feedCount) {
		UUID parentId = category.isTopLevel() ? null : category.getParent().getId();
		return new CategoryResponse(category.getId(), parentId, category.getName(), category.getSortOrder(),
				feedCount);
	}
}
