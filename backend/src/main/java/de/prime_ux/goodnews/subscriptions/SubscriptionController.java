package de.prime_ux.goodnews.subscriptions;

import de.prime_ux.goodnews.auth.CurrentSession;
import de.prime_ux.goodnews.catalog.Category;
import de.prime_ux.goodnews.catalog.CategoryRepository;
import de.prime_ux.goodnews.users.AppUser;
import jakarta.validation.Valid;
import java.util.List;
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
 * Which of their tenant's categories a user wants to see. Open to every signed-in user with a
 * tenant — unlike the catalog's own endpoints, which belong to the admins.
 *
 * <p>The choice orders the board and nothing else. Every source of the tenant is fetched and
 * rated either way, because a story's category is only known once it has been rated.
 */
@RestController
@RequestMapping("/api/news")
class SubscriptionController {

	private final CurrentSession currentSession;
	private final CategoryRepository categoryRepository;
	private final UserCategoryRepository userCategoryRepository;

	SubscriptionController(CurrentSession currentSession, CategoryRepository categoryRepository,
			UserCategoryRepository userCategoryRepository) {
		this.currentSession = currentSession;
		this.categoryRepository = categoryRepository;
		this.userCategoryRepository = userCategoryRepository;
	}

	@GetMapping("/categories")
	@Transactional(readOnly = true)
	List<PickedCategoryResponse> getCategories() {
		Set<UUID> picked = Set.copyOf(userCategoryRepository.findCategoryIdsByUserId(currentSession.user().getId()));
		return categoryRepository.findAllByTenantIdOrderBySortOrderAscNameAsc(currentSession.tenant().getId())
				.stream()
				.map(category -> new PickedCategoryResponse(category.getId(), category.getName(),
						category.getSortOrder(), picked.contains(category.getId())))
				.toList();
	}

	/**
	 * Replaces the selection with what the body holds. Categories of another tenant are broken
	 * input rather than a silent no-op: nobody should be left believing they ticked something that
	 * was never theirs to tick.
	 */
	@PutMapping("/picks")
	@Transactional
	List<UUID> setPicks(@Valid @RequestBody PicksRequest request) {
		AppUser user = currentSession.user();
		UUID tenantId = currentSession.tenant().getId();
		List<Category> categories = request.categoryIds().stream().distinct()
				.map(categoryId -> categoryRepository.findByIdAndTenantId(categoryId, tenantId)
						.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown category")))
				.toList();
		// Cleared and written afresh rather than compared item by item: the selection is a handful
		// of rows, and a diff would be more code with more ways to go wrong.
		userCategoryRepository.deleteAllByUserId(user.getId());
		userCategoryRepository.flush();
		userCategoryRepository.saveAll(categories.stream().map(category -> new UserCategory(user, category)).toList());
		return categories.stream().map(Category::getId).toList();
	}
}
