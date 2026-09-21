package de.prime_ux.goodnews.subscriptions;

import de.prime_ux.goodnews.auth.CurrentSession;
import de.prime_ux.goodnews.catalog.Category;
import de.prime_ux.goodnews.catalog.CategoryRepository;
import de.prime_ux.goodnews.catalog.Feed;
import de.prime_ux.goodnews.catalog.FeedRepository;
import de.prime_ux.goodnews.users.AppUser;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * What a user picked from their tenant's catalog. Open to every signed-in user with a tenant —
 * unlike the catalog's own endpoints, which belong to the admins.
 */
@RestController
@RequestMapping("/api/news")
class SubscriptionController {

	private final CurrentSession currentSession;
	private final CategoryRepository categoryRepository;
	private final FeedRepository feedRepository;
	private final UserFeedRepository userFeedRepository;

	SubscriptionController(CurrentSession currentSession, CategoryRepository categoryRepository,
			FeedRepository feedRepository, UserFeedRepository userFeedRepository) {
		this.currentSession = currentSession;
		this.categoryRepository = categoryRepository;
		this.feedRepository = feedRepository;
		this.userFeedRepository = userFeedRepository;
	}

	@GetMapping("/catalog")
	@Transactional(readOnly = true)
	List<CatalogResponse> getCatalog() {
		UUID tenantId = currentSession.tenant().getId();
		Set<UUID> picked = Set.copyOf(userFeedRepository.findFeedIdsByUserId(currentSession.user().getId()));
		Map<UUID, List<CatalogResponse.Feed>> feedsByCategory = new LinkedHashMap<>();
		for (Feed feed : feedRepository.findAllOfTenant(tenantId)) {
			feedsByCategory.computeIfAbsent(feed.getCategory().getId(), key -> new ArrayList<>())
					.add(new CatalogResponse.Feed(feed.getId(), feed.getName(), feed.getUrl(),
							picked.contains(feed.getId())));
		}
		return categoryRepository.findAllByTenantIdOrderBySortOrderAscNameAsc(tenantId).stream()
				.map(category -> toResponse(category, feedsByCategory))
				.toList();
	}

	/**
	 * Replaces the selection with what the body holds. Feeds of another tenant are broken input
	 * rather than a silent no-op: nobody should be left believing they picked something that was
	 * never theirs to pick.
	 */
	@PutMapping("/picks")
	@Transactional
	List<UUID> setPicks(@Valid @RequestBody PicksRequest request) {
		AppUser user = currentSession.user();
		UUID tenantId = currentSession.tenant().getId();
		List<UUID> wanted = request.feedIds().stream().distinct().toList();
		List<Feed> feeds = wanted.stream()
				.map(feedId -> feedRepository.findByIdAndTenantId(feedId, tenantId)
						.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown feed")))
				.toList();
		// Cleared and written afresh rather than compared item by item: the selection is a handful
		// of rows, and a diff would be more code with more ways to go wrong.
		userFeedRepository.deleteAllByUserId(user.getId());
		userFeedRepository.flush();
		userFeedRepository.saveAll(feeds.stream().map(feed -> new UserFeed(user, feed)).toList());
		return feeds.stream().map(Feed::getId).toList();
	}

	private static CatalogResponse toResponse(Category category,
			Map<UUID, List<CatalogResponse.Feed>> feedsByCategory) {
		UUID parentId = category.isTopLevel() ? null : category.getParent().getId();
		return new CatalogResponse(category.getId(), parentId, category.getName(), category.getSortOrder(),
				feedsByCategory.getOrDefault(category.getId(), List.of()));
	}
}
