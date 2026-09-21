package de.prime_ux.goodnews.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Create and update share the same shape. {@code sortOrder} is the position in the list, counted
 * from zero. Left empty, the category goes to the end — which is what creating one should do, and
 * what a rename should leave alone.
 */
record CategoryRequest(@NotBlank @Size(max = 100) String name, @PositiveOrZero Integer sortOrder) {

	String trimmedName() {
		return name.trim();
	}
}
