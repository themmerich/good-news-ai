package de.prime_ux.goodnews.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.prime_ux.goodnews.TestcontainersConfiguration;
import de.prime_ux.goodnews.auth.AsUser;
import de.prime_ux.goodnews.tenants.Tenant;
import de.prime_ux.goodnews.tenants.TenantRepository;
import de.prime_ux.goodnews.users.AppUser;
import de.prime_ux.goodnews.users.AppUserRepository;
import de.prime_ux.goodnews.users.UserRole;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CategoryControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AppUserRepository appUserRepository;

	@Autowired
	private FeedRepository feedRepository;

	@Autowired
	private CategoryRepository categoryRepository;

	@Autowired
	private TenantRepository tenantRepository;

	private Tenant tenant;
	private Category sport;
	private Category politik;
	private Category foreignCategory;

	@BeforeEach
	void cleanDatabaseAndCreateCategories() {
		// Dependents first; other test classes share this context's database.
		feedRepository.deleteAll();
		categoryRepository.deleteAll();
		appUserRepository.deleteAll();
		tenantRepository.deleteAll();
		tenant = tenantRepository.save(new Tenant("Musterfirma GmbH", "musterfirma"));
		Tenant otherTenant = tenantRepository.save(new Tenant("Beispiel AG", "beispiel-ag"));
		sport = categoryRepository.save(new Category(tenant, "Sport", 0));
		politik = categoryRepository.save(new Category(tenant, "Politik", 1));
		categoryRepository.save(new Category(tenant, "Soziales", 2));
		// Another tenant's category must never show up nor be reachable.
		foreignCategory = categoryRepository.save(new Category(otherTenant, "Wirtschaft", 0));
		appUserRepository.save(new AppUser(tenant, "anna", "Anna", "Admin", "{noop}irrelevant", UserRole.ADMIN));
		appUserRepository.save(new AppUser(tenant, "ben", "Ben", "Benutzer", "{noop}irrelevant", UserRole.USER));
	}

	@Test
	@AsUser("anna")
	void listsTheOwnTenantsCategoriesInOrder() throws Exception {
		mockMvc.perform(get("/api/categories"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(3))
				.andExpect(jsonPath("$..name", org.hamcrest.Matchers.contains("Sport", "Politik", "Soziales")))
				// Nothing of the other tenant, whatever its name.
				.andExpect(jsonPath("$[?(@.name == 'Wirtschaft')]").isEmpty());
	}

	@Test
	@AsUser("anna")
	void createsACategoryAtTheEndOfTheList() throws Exception {
		mockMvc.perform(post("/api/categories").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"  Technik \"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("Technik"))
				.andExpect(jsonPath("$.sortOrder").value(3));
	}

	@Test
	@AsUser("anna")
	void refusesANameTheTenantAlreadyHas() throws Exception {
		mockMvc.perform(post("/api/categories").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				// Only the casing differs. The name is unique per tenant now, where it used to be
				// unique only among one parent's children.
				.content("{\"name\": \"sPoRt\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.reason").value("name"));
	}

	@Test
	@AsUser("anna")
	void letsAnotherTenantKeepTheSameName() throws Exception {
		mockMvc.perform(post("/api/categories").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"Wirtschaft\"}"))
				.andExpect(status().isCreated());
	}

	@Test
	@AsUser("anna")
	void movesACategoryIntoAPositionAndRenumbersTheRest() throws Exception {
		mockMvc.perform(put("/api/categories/" + politik.getId()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"Politik\", \"sortOrder\": 0}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sortOrder").value(0));

		List<Category> categories = categoryRepository.findAllByTenantIdOrderBySortOrderAscNameAsc(tenant.getId());
		assertThat(categories.stream().map(Category::getName)).containsExactly("Politik", "Sport", "Soziales");
		assertThat(categories.stream().map(Category::getSortOrder)).containsExactly(0, 1, 2);
	}

	/**
	 * Deleting used to answer 409 while feeds hung on the category. Feeds no longer carry one, and
	 * the articles that do lose their column rather than the row, so nothing stands in the way.
	 */
	@Test
	@AsUser("anna")
	void deletesACategoryEvenWhileSourcesExist() throws Exception {
		feedRepository.save(new Feed(tenant, "kicker", "https://kicker.example/rss", SourceType.FEED));

		mockMvc.perform(delete("/api/categories/" + sport.getId()).with(csrf()))
				.andExpect(status().isNoContent());

		assertThat(categoryRepository.findById(sport.getId())).isEmpty();
		assertThat(feedRepository.count()).isOne();
	}

	@Test
	@AsUser("anna")
	void neverReachesAnotherTenantsCategory() throws Exception {
		mockMvc.perform(put("/api/categories/" + foreignCategory.getId()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"Gekapert\"}"))
				.andExpect(status().isNotFound());
		mockMvc.perform(delete("/api/categories/" + foreignCategory.getId()).with(csrf()))
				.andExpect(status().isNotFound());
		mockMvc.perform(put("/api/categories/" + UUID.randomUUID()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"Niemand\"}"))
				.andExpect(status().isNotFound());
	}

	@Test
	@AsUser("ben")
	void staysClosedToRegularUsers() throws Exception {
		mockMvc.perform(get("/api/categories")).andExpect(status().isForbidden());
	}
}
