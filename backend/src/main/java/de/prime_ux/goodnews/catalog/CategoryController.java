package de.prime_ux.goodnews.catalog;

import de.prime_ux.goodnews.auth.CurrentSession;
import de.prime_ux.goodnews.tenants.ConflictResponse;
import jakarta.validation.Valid;
import java.util.ArrayList;
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
 * The catalog's categories, for the tenant's admins — the role requirement is enforced in the
 * SecurityConfig. Users never come here; they read the same tree through the subscriptions
 * endpoint, which also says what they picked.
 */
@RestController
@RequestMapping("/api/categories")
class CategoryController {

	private final CurrentSession currentSession;
	private final CategoryRepository categoryRepository;
	private final FeedRepository feedRepository;

	CategoryController(CurrentSession currentSession, CategoryRepository categoryRepository,
			FeedRepository feedRepository) {
		this.currentSession = currentSession;
		this.categoryRepository = categoryRepository;
		this.feedRepository = feedRepository;
	}

	@GetMapping
	@Transactional(readOnly = true)
	List<CategoryResponse> listCategories() {
		UUID tenantId = currentSession.tenant().getId();
		Map<UUID, Long> feedCounts = feedRepository.countPerCategory(tenantId).stream()
				.collect(Collectors.toMap(CategoryFeedCount::getCategoryId, CategoryFeedCount::getCount));
		return categoryRepository.findAllByTenantIdOrderBySortOrderAscNameAsc(tenantId).stream()
				.map(category -> CategoryResponse.from(category, feedCounts.getOrDefault(category.getId(), 0L)))
				.toList();
	}

	@PostMapping
	@Transactional
	ResponseEntity<?> createCategory(@Valid @RequestBody CategoryRequest request) {
		UUID tenantId = currentSession.tenant().getId();
		Category parent = resolveParent(request.parentId(), tenantId);
		String name = request.trimmedName();
		if (nameTaken(tenantId, parent, name)) {
			return nameConflict();
		}
		List<Category> siblings = siblingsOf(tenantId, parent);
		Category category = categoryRepository
				.save(new Category(currentSession.tenant(), parent, name, siblings.size()));
		place(category, siblings, request.sortOrder());
		return ResponseEntity.status(HttpStatus.CREATED).body(CategoryResponse.from(category, 0));
	}

	/**
	 * Rename, move under another parent, or drag into a new position — a drag in the tree changes
	 * all three at once, so one move is one request. Categories of other tenants answer 404 as if
	 * they did not exist.
	 */
	@PutMapping("/{id}")
	@Transactional
	ResponseEntity<?> updateCategory(@PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
		UUID tenantId = currentSession.tenant().getId();
		Category category = ownCategory(id);
		Category parent = resolveParent(request.parentId(), tenantId);
		if (parent != null && parent.getId().equals(id)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "a category cannot be its own parent");
		}
		// A category that has children cannot become a child itself: that would be the third
		// level, which the board has no place for.
		if (parent != null && categoryRepository.existsByParentId(id)) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
					"a category with subcategories cannot be moved under another one");
		}
		String name = request.trimmedName();
		boolean staysWhereItWas = samePlace(category, parent);
		if ((!staysWhereItWas || !name.equalsIgnoreCase(category.getName())) && nameTaken(tenantId, parent, name)) {
			return nameConflict();
		}
		category.rename(name);
		List<Category> siblings = new ArrayList<>(siblingsOf(tenantId, parent));
		siblings.removeIf(sibling -> sibling.getId().equals(id));
		category.moveTo(parent, siblings.size());
		place(category, siblings, request.sortOrder());
		long feedCount = feedRepository.countPerCategory(tenantId).stream()
				.filter(count -> count.getCategoryId().equals(id))
				.mapToLong(CategoryFeedCount::getCount).findFirst().orElse(0L);
		return ResponseEntity.ok(CategoryResponse.from(category, feedCount));
	}

	/**
	 * Refused with 409 while any feed still hangs on the category or on one of its children.
	 * The database would cascade them away without a word, and a source somebody entered by hand
	 * should not disappear as a side effect of tidying up the tree.
	 */
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Transactional
	void deleteCategory(@PathVariable UUID id) {
		UUID tenantId = currentSession.tenant().getId();
		Category category = ownCategory(id);
		if (carriesFeeds(tenantId, category)) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "the category still has feeds");
		}
		categoryRepository.delete(category);
	}

	private boolean carriesFeeds(UUID tenantId, Category category) {
		if (feedRepository.existsByCategoryId(category.getId())) {
			return true;
		}
		return categoryRepository.findAllByTenantIdAndParentIdOrderBySortOrderAscNameAsc(tenantId, category.getId())
				.stream().anyMatch(child -> feedRepository.existsByCategoryId(child.getId()));
	}

	/**
	 * Gives every sibling a position from zero upwards, with the category itself dropped in at the
	 * requested index. Renumbering the whole row rather than nudging single values keeps the
	 * numbers dense, so no drag can ever run out of room between two of them.
	 */
	private void place(Category category, List<Category> siblings, Integer requestedIndex) {
		List<Category> ordered = new ArrayList<>(siblings);
		int index = requestedIndex == null ? ordered.size() : Math.min(requestedIndex, ordered.size());
		ordered.add(index, category);
		for (int position = 0; position < ordered.size(); position++) {
			ordered.get(position).moveTo(position);
		}
		categoryRepository.saveAll(ordered);
	}

	private List<Category> siblingsOf(UUID tenantId, Category parent) {
		return parent == null
				? categoryRepository.findAllByTenantIdAndParentIsNullOrderBySortOrderAscNameAsc(tenantId)
				: categoryRepository.findAllByTenantIdAndParentIdOrderBySortOrderAscNameAsc(tenantId, parent.getId());
	}

	private boolean samePlace(Category category, Category parent) {
		UUID currentParentId = category.isTopLevel() ? null : category.getParent().getId();
		UUID targetParentId = parent == null ? null : parent.getId();
		return java.util.Objects.equals(currentParentId, targetParentId);
	}

	private boolean nameTaken(UUID tenantId, Category parent, String name) {
		return parent == null
				? categoryRepository.existsByTenantIdAndParentIsNullAndNameIgnoreCase(tenantId, name)
				: categoryRepository.existsByTenantIdAndParentIdAndNameIgnoreCase(tenantId, parent.getId(), name);
	}

	/** A parent of another tenant, an unknown one, or one that is already a child is broken input. */
	private Category resolveParent(UUID parentId, UUID tenantId) {
		if (parentId == null) {
			return null;
		}
		Category parent = categoryRepository.findByIdAndTenantId(parentId, tenantId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown category"));
		if (!parent.isTopLevel()) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "categories are two levels deep at most");
		}
		return parent;
	}

	private Category ownCategory(UUID id) {
		return categoryRepository.findByIdAndTenantId(id, currentSession.tenant().getId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}

	private static ResponseEntity<ConflictResponse> nameConflict() {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(new ConflictResponse("name"));
	}
}
