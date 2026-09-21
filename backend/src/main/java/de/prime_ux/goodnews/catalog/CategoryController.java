package de.prime_ux.goodnews.catalog;

import de.prime_ux.goodnews.auth.CurrentSession;
import de.prime_ux.goodnews.tenants.ConflictResponse;
import jakarta.validation.Valid;
import java.util.ArrayList;
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
 * The catalog's categories, for the tenant's admins — the role requirement is enforced in the
 * SecurityConfig. Users never come here; they read the same list through the subscriptions
 * endpoint, which also says which of them they ticked.
 */
@RestController
@RequestMapping("/api/categories")
class CategoryController {

	private final CurrentSession currentSession;
	private final CategoryRepository categoryRepository;

	CategoryController(CurrentSession currentSession, CategoryRepository categoryRepository) {
		this.currentSession = currentSession;
		this.categoryRepository = categoryRepository;
	}

	@GetMapping
	@Transactional(readOnly = true)
	List<CategoryResponse> listCategories() {
		return categories().stream().map(CategoryResponse::from).toList();
	}

	@PostMapping
	@Transactional
	ResponseEntity<?> createCategory(@Valid @RequestBody CategoryRequest request) {
		UUID tenantId = currentSession.tenant().getId();
		String name = request.trimmedName();
		if (categoryRepository.existsByTenantIdAndNameIgnoreCase(tenantId, name)) {
			return nameConflict();
		}
		List<Category> others = categories();
		Category category = categoryRepository.save(new Category(currentSession.tenant(), name, others.size()));
		place(category, others, request.sortOrder());
		return ResponseEntity.status(HttpStatus.CREATED).body(CategoryResponse.from(category));
	}

	/** Categories of other tenants answer 404 as if they did not exist. */
	@PutMapping("/{id}")
	@Transactional
	ResponseEntity<?> updateCategory(@PathVariable UUID id, @Valid @RequestBody CategoryRequest request) {
		UUID tenantId = currentSession.tenant().getId();
		Category category = ownCategory(id);
		String name = request.trimmedName();
		if (!name.equalsIgnoreCase(category.getName())
				&& categoryRepository.existsByTenantIdAndNameIgnoreCase(tenantId, name)) {
			return nameConflict();
		}
		category.rename(name);
		List<Category> others = new ArrayList<>(categories());
		others.removeIf(other -> other.getId().equals(id));
		place(category, others, request.sortOrder());
		return ResponseEntity.ok(CategoryResponse.from(category));
	}

	/**
	 * Always goes through. Sources used to hang on a category and made deleting one a way to lose
	 * them; now only articles point here, and they keep their place on the board under
	 * "Sonstiges" rather than disappearing with it — the foreign key clears the column instead of
	 * cascading.
	 */
	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	@Transactional
	void deleteCategory(@PathVariable UUID id) {
		categoryRepository.delete(ownCategory(id));
	}

	/**
	 * Gives every category a position from zero upwards, with this one dropped in at the requested
	 * index. Renumbering the whole list rather than nudging single values keeps the numbers dense,
	 * so no move can ever run out of room between two of them.
	 */
	private void place(Category category, List<Category> others, Integer requestedIndex) {
		List<Category> ordered = new ArrayList<>(others);
		int index = requestedIndex == null ? ordered.size() : Math.min(requestedIndex, ordered.size());
		ordered.add(index, category);
		for (int position = 0; position < ordered.size(); position++) {
			ordered.get(position).moveTo(position);
		}
		categoryRepository.saveAll(ordered);
	}

	private List<Category> categories() {
		return categoryRepository.findAllByTenantIdOrderBySortOrderAscNameAsc(currentSession.tenant().getId());
	}

	private Category ownCategory(UUID id) {
		return categoryRepository.findByIdAndTenantId(id, currentSession.tenant().getId())
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
	}

	private static ResponseEntity<ConflictResponse> nameConflict() {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(new ConflictResponse("name"));
	}
}
