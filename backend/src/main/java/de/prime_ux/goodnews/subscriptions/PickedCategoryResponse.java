package de.prime_ux.goodnews.subscriptions;

import java.util.UUID;

/**
 * One category as the picking page shows it: the tenant's list, plus whether this user ticked it.
 * One request is enough to draw the whole page.
 */
public record PickedCategoryResponse(UUID id, String name, int sortOrder, boolean selected) {
}
