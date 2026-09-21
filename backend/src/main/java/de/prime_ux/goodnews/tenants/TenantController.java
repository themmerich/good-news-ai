package de.prime_ux.goodnews.tenants;

import de.prime_ux.goodnews.auth.CurrentSession;
import de.prime_ux.goodnews.users.AppUserRepository;
import jakarta.validation.Valid;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The tenants themselves, for the super-user (the security chain lets nobody else in; the role
 * is checked here once more, so a wrong rule never opens this). A tenant's own data — company,
 * users — is managed inside the tenant; here it is only created, named and, when the time
 * comes, deleted with everything that hangs on it.
 */
@RestController
@RequestMapping("/api/tenants")
class TenantController {

	private final TenantRepository tenantRepository;
	private final AppUserRepository appUserRepository;
	private final CurrentSession currentSession;

	TenantController(TenantRepository tenantRepository, AppUserRepository appUserRepository,
			CurrentSession currentSession) {
		this.tenantRepository = tenantRepository;
		this.appUserRepository = appUserRepository;
		this.currentSession = currentSession;
	}

	@GetMapping
	@Transactional(readOnly = true)
	List<TenantResponse> listTenants() {
		requireSuperuser();
		Map<UUID, Long> users = countsById(appUserRepository.countPerTenant());
		return tenantRepository.findAll().stream()
				.sorted(Comparator.comparing(Tenant::getName, String.CASE_INSENSITIVE_ORDER))
				.map(tenant -> TenantResponse.from(tenant, users.getOrDefault(tenant.getId(), 0L)))
				.toList();
	}

	@PostMapping
	@Transactional
	ResponseEntity<?> createTenant(@Valid @RequestBody TenantRequest request) {
		requireSuperuser();
		String slug = request.normalizedSlug();
		if (tenantRepository.existsBySlug(slug)) {
			return slugTaken();
		}
		Tenant tenant = tenantRepository.save(new Tenant(request.trimmedName(), slug));
		return ResponseEntity.status(HttpStatus.CREATED).body(TenantResponse.from(tenant, 0));
	}

	/** Name and Kennung. A changed Kennung changes nothing about open sessions: they carry the id. */
	@PutMapping("/{id}")
	@Transactional
	ResponseEntity<?> updateTenant(@PathVariable UUID id, @Valid @RequestBody TenantRequest request) {
		requireSuperuser();
		Tenant tenant = existing(id);
		String slug = request.normalizedSlug();
		if (!slug.equals(tenant.getSlug()) && tenantRepository.existsBySlug(slug)) {
			return slugTaken();
		}
		tenant.updateIdentity(request.trimmedName(), slug);
		tenantRepository.save(tenant);
		return ResponseEntity.ok(TenantResponse.from(tenant, countFor(appUserRepository.countPerTenant(), id)));
	}

	/**
	 * Final. The database cascades take the users, branches, settings and logo along. If the
	 * super-user had this very tenant open, their session forgets it first, so the next request
	 * does not point at a tenant that is gone.
	 */
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Transactional
	void deleteTenant(@PathVariable UUID id) {
		requireSuperuser();
		Tenant tenant = existing(id);
		if (currentSession.tenantIfAny().map(open -> open.getId().equals(id)).orElse(false)) {
			currentSession.closeTenant();
		}
		tenantRepository.delete(tenant);
	}

	private Tenant existing(UUID id) {
		return tenantRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}

	private void requireSuperuser() {
		if (!currentSession.user().isSuperuser()) {
			throw new ResponseStatusException(HttpStatus.FORBIDDEN);
		}
	}

	private static ResponseEntity<ConflictResponse> slugTaken() {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(new ConflictResponse("slug"));
	}

	private static Map<UUID, Long> countsById(List<TenantCount> counts) {
		return counts.stream().collect(Collectors.toMap(TenantCount::getTenantId, TenantCount::getCount));
	}

	private static long countFor(List<TenantCount> counts, UUID tenantId) {
		return countsById(counts).getOrDefault(tenantId, 0L);
	}
}
