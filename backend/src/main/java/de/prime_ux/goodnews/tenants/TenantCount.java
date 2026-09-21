package de.prime_ux.goodnews.tenants;

import java.util.UUID;

/** How many of something a tenant has — its users — from one grouped query over all tenants. */
public interface TenantCount {

	UUID getTenantId();

	long getCount();
}
