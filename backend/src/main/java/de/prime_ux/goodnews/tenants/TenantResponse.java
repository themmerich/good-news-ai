package de.prime_ux.goodnews.tenants;

import java.time.Instant;
import java.util.UUID;

/**
 * A tenant as the Mandanten page lists it: identity, and how much hangs on it. The count is
 * for the person about to open or delete it, not for the tenant's own pages.
 */
public record TenantResponse(UUID id, String slug, String name, Instant createdAt, long userCount) {

	public static TenantResponse from(Tenant tenant, long userCount) {
		return new TenantResponse(tenant.getId(), tenant.getSlug(), tenant.getName(), tenant.getCreatedAt(), userCount);
	}
}
