package de.prime_ux.goodnews.tenants;

/**
 * Why a request was refused with 409, named so the page can put the message under the right
 * field: {@code slug} when the Kennung is taken, {@code name} when a category name is, {@code url}
 * when a feed is already in the catalog.
 */
public record ConflictResponse(String reason) {
}
