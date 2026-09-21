package de.prime_ux.goodnews.auth;

import de.prime_ux.goodnews.tenants.Tenant;
import de.prime_ux.goodnews.users.AppUser;
import java.util.Locale;
import java.util.Optional;

/**
 * @param role {@code admin}, {@code user} or {@code superuser}
 * @param tenant the tenant the session is about; null for a super-user who has none open
 */
public record CurrentUserResponse(String username, String displayName, String role, TenantRef tenant,
		boolean hasAvatar) {

	public record TenantRef(String slug, String name) {
	}

	public static CurrentUserResponse from(AppUser user, Optional<Tenant> tenant, boolean hasAvatar) {
		return new CurrentUserResponse(user.getUsername(), user.getDisplayName(),
				user.getRole().name().toLowerCase(Locale.ROOT),
				tenant.map(t -> new TenantRef(t.getSlug(), t.getName())).orElse(null), hasAvatar);
	}
}
