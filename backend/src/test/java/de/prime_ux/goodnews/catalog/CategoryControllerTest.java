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
	private Category fussball;
	private Category angular;
	private Category foreignCategory;

	@BeforeEach
	void cleanDatabaseAndCreateCategories() {
		// Dependents first; other test classes share this context's database. The categories go
		// in one statement rather than row by row: they point at each other, and a row-wise
		// delete would have to hit the children before their parents.
		feedRepository.deleteAll();
		categoryRepository.deleteAllInBatch();
		appUserRepository.deleteAll();
		tenantRepository.deleteAll();
		tenant = tenantRepository.save(new Tenant("Musterfirma GmbH", "musterfirma"));
		Tenant otherTenant = tenantRepository.save(new Tenant("Beispiel AG", "beispiel-ag"));
		sport = categoryRepository.save(new Category(tenant, null, "Sport", 0));
		fussball = categoryRepository.save(new Category(tenant, sport, "Fußball", 0));
		categoryRepository.save(new Category(tenant, sport, "Football", 1));
		angular = categoryRepository.save(new Category(tenant, null, "Angular", 1));
		// Another tenant's category must never show up nor be reachable.
		foreignCategory = categoryRepository.save(new Category(otherTenant, null, "Politik", 0));
		appUserRepository.save(new AppUser(tenant, "anna", "Anna", "Admin", "{noop}irrelevant", UserRole.ADMIN));
		appUserRepository.save(new AppUser(tenant, "ben", "Ben", "Benutzer", "{noop}irrelevant", UserRole.USER));
	}

	@Test
	@AsUser("anna")
	void listsTheOwnTenantsTreeFlatWithTheParentOnEachNode() throws Exception {
		mockMvc.perform(get("/api/categories"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(4))
				.andExpect(jsonPath("$..name",
						org.hamcrest.Matchers.containsInAnyOrder("Sport", "Fußball", "Football", "Angular")))
				.andExpect(jsonPath("$[?(@.name == 'Fußball')].parentId",
						org.hamcrest.Matchers.hasItem(sport.getId().toString())))
				// Nothing of the other tenant, whatever its name.
				.andExpect(jsonPath("$[?(@.name == 'Politik')]").isEmpty());
	}

	@Test
	@AsUser("anna")
	void countsTheFeedsOnEachCategory() throws Exception {
		feedRepository.save(new Feed(tenant, fussball, "kicker", "https://kicker.example/rss", SourceType.FEED));

		mockMvc.perform(get("/api/categories"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[?(@.name == 'Fußball')].feedCount",
						org.hamcrest.Matchers.hasItem(1)))
				// A top-level category carries no feeds of its own, whatever hangs below it.
				.andExpect(jsonPath("$[?(@.name == 'Sport')].feedCount",
						org.hamcrest.Matchers.hasItem(0)));
	}

	@Test
	@AsUser("anna")
	void createsACategoryAtTheEndOfItsSiblings() throws Exception {
		mockMvc.perform(post("/api/categories").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"  Handball \", \"parentId\": \"" + sport.getId() + "\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("Handball"))
				.andExpect(jsonPath("$.parentId").value(sport.getId().toString()))
				.andExpect(jsonPath("$.sortOrder").value(2));
	}

	@Test
	@AsUser("anna")
	void refusesANameThatASiblingAlreadyHas() throws Exception {
		mockMvc.perform(post("/api/categories").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				// Only the casing differs. Deliberately not "FUSSBALL": the sharp s turns into "ss"
				// when upper-cased, which makes it a different name to lower() and would pass.
				.content("{\"name\": \"fußball\", \"parentId\": \"" + sport.getId() + "\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.reason").value("name"));
	}

	@Test
	@AsUser("anna")
	void letsTheSameNameStandUnderADifferentParent() throws Exception {
		mockMvc.perform(post("/api/categories").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"Fußball\", \"parentId\": \"" + angular.getId() + "\"}"))
				.andExpect(status().isCreated());
	}

	@Test
	@AsUser("anna")
	void refusesAThirdLevel() throws Exception {
		mockMvc.perform(post("/api/categories").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"Bundesliga\", \"parentId\": \"" + fussball.getId() + "\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@AsUser("anna")
	void movesACategoryIntoAPositionAndRenumbersItsSiblings() throws Exception {
		// Football stands second under Sport; dragged to the front it becomes the zeroth.
		Category football = categoryRepository
				.findAllByTenantIdAndParentIdOrderBySortOrderAscNameAsc(tenant.getId(), sport.getId()).getLast();

		mockMvc.perform(put("/api/categories/" + football.getId()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"Football\", \"parentId\": \"" + sport.getId() + "\", \"sortOrder\": 0}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sortOrder").value(0));

		List<Category> children = categoryRepository
				.findAllByTenantIdAndParentIdOrderBySortOrderAscNameAsc(tenant.getId(), sport.getId());
		assertThat(children.stream().map(Category::getName)).containsExactly("Football", "Fußball");
		assertThat(children.stream().map(Category::getSortOrder)).containsExactly(0, 1);
	}

	@Test
	@AsUser("anna")
	void movesACategoryToTheTopLevel() throws Exception {
		mockMvc.perform(put("/api/categories/" + fussball.getId()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"Fußball\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.parentId").doesNotExist());

		assertThat(categoryRepository.findById(fussball.getId()).orElseThrow().isTopLevel()).isTrue();
	}

	@Test
	@AsUser("anna")
	void refusesToMoveACategoryThatHasChildrenUnderAnother() throws Exception {
		mockMvc.perform(put("/api/categories/" + sport.getId()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"Sport\", \"parentId\": \"" + angular.getId() + "\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@AsUser("anna")
	void deletesAnEmptyCategoryAndRefusesOneWithFeeds() throws Exception {
		feedRepository.save(new Feed(tenant, fussball, "kicker", "https://kicker.example/rss", SourceType.FEED));

		// The feed hangs on Fußball, which hangs on Sport: deleting either would take it along.
		mockMvc.perform(delete("/api/categories/" + fussball.getId()).with(csrf()))
				.andExpect(status().isConflict());
		mockMvc.perform(delete("/api/categories/" + sport.getId()).with(csrf()))
				.andExpect(status().isConflict());

		mockMvc.perform(delete("/api/categories/" + angular.getId()).with(csrf()))
				.andExpect(status().isNoContent());
		assertThat(categoryRepository.findById(angular.getId())).isEmpty();
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
