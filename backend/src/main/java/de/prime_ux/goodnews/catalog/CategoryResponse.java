package de.prime_ux.goodnews.catalog;

import java.util.UUID;

/** One category as the admin page lists it. */
public record CategoryResponse(UUID id, String name, int sortOrder) {

	static CategoryResponse from(Category category) {
		return new CategoryResponse(category.getId(), category.getName(), category.getSortOrder());
	}
}
