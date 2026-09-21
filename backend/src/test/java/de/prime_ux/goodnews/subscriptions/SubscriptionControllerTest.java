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
import de.prime_ux.goodnews.catalog.Feed;
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
	private UserFeedRepository userFeedRepository;

	@Autowired
	private FeedRepository feedRepository;

	@Autowired
	private CategoryRepository categoryRepository;

	@Autowired
	private TenantRepository tenantRepository;

	private Feed kicker;
	private Feed sportschau;
	private Feed foreignFeed;

	@BeforeEach
	void cleanDatabaseAndCreateCatalog() {
		userFeedRepository.deleteAll();
		feedRepository.deleteAll();
		categoryRepository.deleteAllInBatch();
		appUserRepository.deleteAll();
		tenantRepository.deleteAll();
		Tenant tenant = tenantRepository.save(new Tenant("Musterfirma GmbH", "musterfirma"));
		Tenant otherTenant = tenantRepository.save(new Tenant("Beispiel AG", "beispiel-ag"));
		Category sport = categoryRepository.save(new Category(tenant, null, "Sport", 0));
		Category fussball = categoryRepository.save(new Category(tenant, sport, "Fußball", 0));
		kicker = feedRepository.save(new Feed(tenant, fussball, "kicker", "https://kicker.example/rss"));
		sportschau = feedRepository.save(new Feed(tenant, fussball, "Sportschau",
				"https://sportschau.example/rss"));
		Category foreignCategory = categoryRepository.save(new Category(otherTenant, null, "Politik", 0));
		foreignFeed = feedRepository.save(new Feed(otherTenant, foreignCategory, "Tagesschau",
				"https://tagesschau.example/rss"));
		appUserRepository.save(new AppUser(tenant, "ben", "Ben", "Benutzer", "{noop}irrelevant", UserRole.USER));
		appUserRepository.save(new AppUser(tenant, "uwe", "Uwe", "User", "{noop}irrelevant", UserRole.USER));
	}

	@Test
	@AsUser("ben")
	void showsTheWholeCatalogWithNothingPickedAtFirst() throws Exception {
		mockMvc.perform(get("/api/news/catalog"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[?(@.name == 'Fußball')].feeds.length()",
						org.hamcrest.Matchers.hasItem(2)))
				.andExpect(jsonPath("$..feeds[?(@.selected == true)]").isEmpty())
				// The other tenant's feed is in neither category.
				.andExpect(jsonPath("$..feeds[?(@.name == 'Tagesschau')]").isEmpty());
	}

	@Test
	@AsUser("ben")
	void picksFeedsAndFindsThemMarkedAfterwards() throws Exception {
		mockMvc.perform(put("/api/news/picks").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"feedIds\": [\"" + kicker.getId() + "\"]}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));

		mockMvc.perform(get("/api/news/catalog"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$..feeds[?(@.name == 'kicker')].selected",
						org.hamcrest.Matchers.hasItem(true)))
				.andExpect(jsonPath("$..feeds[?(@.name == 'Sportschau')].selected",
						org.hamcrest.Matchers.hasItem(false)));
	}

	@Test
	@AsUser("ben")
	void replacesTheSelectionRatherThanAddingToIt() throws Exception {
		pick(kicker.getId(), sportschau.getId());

		pick(sportschau.getId());

		AppUser ben = TestUsers.find(appUserRepository, "ben").orElseThrow();
		assertThat(userFeedRepository.findFeedIdsByUserId(ben.getId())).containsExactly(sportschau.getId());
	}

	@Test
	@AsUser("ben")
	void clearsTheSelectionWithAnEmptyList() throws Exception {
		pick(kicker.getId());

		mockMvc.perform(put("/api/news/picks").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"feedIds\": []}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	@AsUser("ben")
	void keepsOnePersonsPicksOutOfAnothersCatalog() throws Exception {
		AppUser uwe = TestUsers.find(appUserRepository, "uwe").orElseThrow();
		userFeedRepository.save(new UserFeed(uwe, sportschau));

		mockMvc.perform(get("/api/news/catalog"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$..feeds[?(@.selected == true)]").isEmpty());
	}

	@Test
	@AsUser("ben")
	void refusesAFeedOfAnotherTenant() throws Exception {
		mockMvc.perform(put("/api/news/picks").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"feedIds\": [\"" + foreignFeed.getId() + "\"]}"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(put("/api/news/picks").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"feedIds\": [\"" + UUID.randomUUID() + "\"]}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@AsUser("ben")
	void countsADuplicateOnlyOnce() throws Exception {
		mockMvc.perform(put("/api/news/picks").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"feedIds\": [\"" + kicker.getId() + "\", \"" + kicker.getId() + "\"]}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1));
	}

	private void pick(UUID... feedIds) throws Exception {
		String ids = java.util.Arrays.stream(feedIds).map(id -> "\"" + id + "\"")
				.collect(java.util.stream.Collectors.joining(", "));
		mockMvc.perform(put("/api/news/picks").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"feedIds\": [" + ids + "]}"))
				.andExpect(status().isOk());
	}
}
