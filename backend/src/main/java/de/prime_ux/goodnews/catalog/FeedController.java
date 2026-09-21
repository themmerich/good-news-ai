package de.prime_ux.goodnews.catalog;

import de.prime_ux.goodnews.auth.CurrentSession;
import de.prime_ux.goodnews.tenants.ConflictResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
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
 * The catalog's feeds, for the tenant's admins — the role requirement is enforced in the
 * SecurityConfig. A feed hangs on a leaf category, because a leaf is what becomes a tab on the
 * board; hanging one on a category that has subcategories would leave it nowhere to appear.
 */
@RestController
@RequestMapping("/api/feeds")
class FeedController {

	private final CurrentSession currentSession;
	private final FeedRepository feedRepository;
	private final CategoryRepository categoryRepository;

	FeedController(CurrentSession currentSession, FeedRepository feedRepository,
			CategoryRepository categoryRepository) {
		this.currentSession = currentSession;
		this.feedRepository = feedRepository;
		this.categoryRepository = categoryRepository;
	}

	@GetMapping
	@Transactional(readOnly = true)
	List<FeedResponse> listFeeds() {
		return feedRepository.findAllOfTenant(currentSession.tenant().getId()).stream()
				.map(FeedResponse::from).toList();
	}

	@PostMapping
	@Transactional
	ResponseEntity<?> createFeed(@Valid @RequestBody FeedRequest request) {
		UUID tenantId = currentSession.tenant().getId();
		Category category = leafCategory(request.categoryId(), tenantId);
		String url = request.trimmedUrl();
		if (feedRepository.existsByTenantIdAndUrlIgnoreCase(tenantId, url)) {
			return urlConflict();
		}
		Feed feed = feedRepository.save(new Feed(currentSession.tenant(), category, request.trimmedName(), url));
		return ResponseEntity.status(HttpStatus.CREATED).body(FeedResponse.from(feed));
	}

	/** Feeds of other tenants answer 404 as if they did not exist. */
	@PutMapping("/{id}")
	@Transactional
	ResponseEntity<?> updateFeed(@PathVariable UUID id, @Valid @RequestBody FeedRequest request) {
		UUID tenantId = currentSession.tenant().getId();
		Feed feed = ownFeed(id);
		Category category = leafCategory(request.categoryId(), tenantId);
		String url = request.trimmedUrl();
		boolean urlTaken = !url.equalsIgnoreCase(feed.getUrl())
				&& feedRepository.existsByTenantIdAndUrlIgnoreCase(tenantId, url);
		if (urlTaken) {
			return urlConflict();
		}
		feed.update(category, request.trimmedName(), url);
		return ResponseEntity.ok(FeedResponse.from(feedRepository.save(feed)));
	}

	/** The articles fetched from this feed go with it, through the database cascade. */
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Transactional
	void deleteFeed(@PathVariable UUID id) {
		feedRepository.delete(ownFeed(id));
	}

	private Category leafCategory(UUID categoryId, UUID tenantId) {
		Category category = categoryRepository.findByIdAndTenantId(categoryId, tenantId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown category"));
		if (categoryRepository.existsByParentId(category.getId())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"feeds hang on categories without subcategories");
		}
		return category;
	}

	private Feed ownFeed(UUID id) {
		return feedRepository.findByIdAndTenantId(id, currentSession.tenant().getId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}

	private static ResponseEntity<ConflictResponse> urlConflict() {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(new ConflictResponse("url"));
	}
}
