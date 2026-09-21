package de.prime_ux.goodnews.subscriptions;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

/**
 * The complete picture, not a change to it: whatever stands here is what the user has picked
 * afterwards. An empty list clears the selection, which is a thing someone may well want and
 * would be awkward to express as a list of removals.
 */
record PicksRequest(@NotNull List<UUID> categoryIds) {
}
