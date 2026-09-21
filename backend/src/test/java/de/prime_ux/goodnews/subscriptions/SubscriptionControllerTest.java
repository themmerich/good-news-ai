package de.prime_ux.goodnews.subscriptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.prime_ux.goodnews.TestcontainersConfiguration;
import de.prime_ux.goodnews.auth.AsUser;
import de.prime_ux.goodnews.catalog.Category;
import de.prime_ux.goodnews.catalog.CategoryRepository;
import de.prime_ux.goodnews.catalog.FeedRepository;
import de.prime_ux.goodnews.tenants.Tenant;
import de.prime_ux.goodnews.tenants.TenantRepository;
import de.prime_ux.goodnews.users.AppUser;
import de.prime_ux.goodnews.users.AppUserRepository;
import de.prime_ux.goodnews.users.TestUsers;
import de.prime_ux.goodnews.users.UserRole;
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
class SubscriptionControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AppUserRepository appUserRepository;

	@Autowired
	private UserCategoryRepository userCategoryRepository;

	@Autowired
	private FeedRepository feedRepository;

	@Autowired
	private CategoryRepository categoryRepository;

	@Autowired
	private TenantRepository tenantRepository;

	private Category sport;
	private Category politik;
	private Category foreignCategory;

	@BeforeEach
	void cleanDatabaseAndCreateCatalog() {
		userCategoryRepository.deleteAll();
		feedRepository.deleteAll();
		categoryRepository.deleteAll();
		appUserRepository.deleteAll();
		tenantRepository.deleteAll();
		Tenant tenant = tenantRepository.save(new Tenant("Musterfirma GmbH", "musterfirma"));
		Tenant otherTenant = tenantRepository.save(new Tenant("Beispiel AG", "beispiel-ag"));
		sport = categoryRepository.save(new Category(tenant, "Sport", 0));
		politik = categoryRepository.save(new Category(tenant, "Politik", 1));
		foreignCategory = categoryRepository.save(new Category(otherTenant, "Wirtschaft", 0));
		appUserRepository.save(new AppUser(tenant, "ben", "Ben", "Benutzer", "{noop}irrelevant", UserRole.USER));
		appUserRepository.save(new AppUser(tenant, "uwe", "Uwe", "User", "{noop}irrelevant", UserRole.USER));
	}

	@Test
	@AsUser("ben")
	void showsTheTenantsCategoriesWithNothingTickedAtFirst() throws Exception {
		mockMvc.perform(get("/api/news/categories"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$..name", org.hamcrest.Matchers.contains("Sport", "Politik")))
				.andExpect(jsonPath("$[?(@.selected == true)]").isEmpty())
				// Nothing of the other tenant.
				.andExpect(jsonPath("$[?(@.name == 'Wirtschaft')]").isEmpty());
	}

	@Test
	@AsUser("ben")
	void ticksCategoriesAndFindsThemMarkedAfterwards() throws Exception {
		mockMvc.perform(put("/api/news/picks").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"categoryIds\": [\"" + sport.getId() + "\"]}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));

		mockMvc.perform(get("/api/news/categories"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.name == 'Sport')].selected",
						org.hamcrest.Matchers.hasItem(true)))
				.andExpect(jsonPath("$[?(@.name == 'Politik')].selected",
						org.hamcrest.Matchers.hasItem(false)));
	}

	@Test
	@AsUser("ben")
	void replacesTheSelectionRatherThanAddingToIt() throws Exception {
		pick(sport.getId(), politik.getId());

		pick(politik.getId());

		AppUser ben = TestUsers.find(appUserRepository, "ben").orElseThrow();
		assertThat(userCategoryRepository.findCategoryIdsByUserId(ben.getId())).containsExactly(politik.getId());
	}

	/** An empty list is the "show me everything" the board reads as no choice at all. */
	@Test
	@AsUser("ben")
	void clearsTheSelectionWithAnEmptyList() throws Exception {
		pick(sport.getId());

		mockMvc.perform(put("/api/news/picks").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"categoryIds\": []}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	@AsUser("ben")
	void keepsOnePersonsChoiceOutOfAnothersList() throws Exception {
		AppUser uwe = TestUsers.find(appUserRepository, "uwe").orElseThrow();
		userCategoryRepository.save(new UserCategory(uwe, politik));

		mockMvc.perform(get("/api/news/categories"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.selected == true)]").isEmpty());
	}

	@Test
	@AsUser("ben")
	void refusesACategoryOfAnotherTenant() throws Exception {
		mockMvc.perform(put("/api/news/picks").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"categoryIds\": [\"" + foreignCategory.getId() + "\"]}"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(put("/api/news/picks").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"categoryIds\": [\"" + UUID.randomUUID() + "\"]}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@AsUser("ben")
	void countsADuplicateOnlyOnce() throws Exception {
		mockMvc.perform(put("/api/news/picks").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"categoryIds\": [\"" + sport.getId() + "\", \"" + sport.getId() + "\"]}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));
	}

	private void pick(UUID... categoryIds) throws Exception {
		String ids = java.util.Arrays.stream(categoryIds).map(id -> "\"" + id + "\"")
				.collect(java.util.stream.Collectors.joining(", "));
		mockMvc.perform(put("/api/news/picks").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"categoryIds\": [" + ids + "]}"))
				.andExpect(status().isOk());
	}
}
