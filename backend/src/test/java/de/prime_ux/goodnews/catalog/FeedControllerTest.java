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
class FeedControllerTest {

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
	private Feed kicker;
	private Feed foreignFeed;

	@BeforeEach
	void cleanDatabaseAndCreateFeeds() {
		feedRepository.deleteAll();
		categoryRepository.deleteAllInBatch();
		appUserRepository.deleteAll();
		tenantRepository.deleteAll();
		tenant = tenantRepository.save(new Tenant("Musterfirma GmbH", "musterfirma"));
		Tenant otherTenant = tenantRepository.save(new Tenant("Beispiel AG", "beispiel-ag"));
		sport = categoryRepository.save(new Category(tenant, null, "Sport", 0));
		fussball = categoryRepository.save(new Category(tenant, sport, "Fußball", 0));
		kicker = feedRepository.save(new Feed(tenant, fussball, "kicker", "https://kicker.example/rss"));
		Category foreignCategory = categoryRepository.save(new Category(otherTenant, null, "Politik", 0));
		foreignFeed = feedRepository.save(new Feed(otherTenant, foreignCategory, "Tagesschau",
				"https://tagesschau.example/rss"));
		appUserRepository.save(new AppUser(tenant, "anna", "Anna", "Admin", "{noop}irrelevant", UserRole.ADMIN));
		appUserRepository.save(new AppUser(tenant, "ben", "Ben", "Benutzer", "{noop}irrelevant", UserRole.USER));
	}

	@Test
	@AsUser("anna")
	void listsTheOwnTenantsFeedsWithTheirCategory() throws Exception {
		mockMvc.perform(get("/api/feeds"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].name").value("kicker"))
				.andExpect(jsonPath("$[0].categoryName").value("Fußball"))
				.andExpect(jsonPath("$[0].categoryId").value(fussball.getId().toString()));
	}

	@Test
	@AsUser("anna")
	void createsAFeedAndTrimsWhatWasPasted() throws Exception {
		mockMvc.perform(post("/api/feeds").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \" Sportschau \", \"url\": \" https://sportschau.example/rss \","
						+ " \"categoryId\": \"" + fussball.getId() + "\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("Sportschau"))
				.andExpect(jsonPath("$.url").value("https://sportschau.example/rss"));

		assertThat(feedRepository.findAllOfTenant(tenant.getId())).hasSize(2);
	}

	@Test
	@AsUser("anna")
	void refusesAUrlThatIsAlreadyInTheCatalog() throws Exception {
		mockMvc.perform(post("/api/feeds").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"Nochmal kicker\", \"url\": \"HTTPS://KICKER.EXAMPLE/RSS\","
						+ " \"categoryId\": \"" + fussball.getId() + "\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.reason").value("url"));
	}

	@Test
	@AsUser("anna")
	void refusesSomethingThatIsNoUrl() throws Exception {
		mockMvc.perform(post("/api/feeds").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"kicker\", \"url\": \"kicker.example\", \"categoryId\": \""
						+ fussball.getId() + "\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@AsUser("anna")
	void refusesACategoryThatHasSubcategories() throws Exception {
		// Sport carries Fußball, so it is no leaf and would have no tab to appear on.
		mockMvc.perform(post("/api/feeds").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"Sportschau\", \"url\": \"https://sportschau.example/rss\","
						+ " \"categoryId\": \"" + sport.getId() + "\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@AsUser("anna")
	void movesAFeedToAnotherCategory() throws Exception {
		Category football = categoryRepository.save(new Category(tenant, sport, "Football", 1));

		mockMvc.perform(put("/api/feeds/" + kicker.getId()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"kicker\", \"url\": \"https://kicker.example/rss\", \"categoryId\": \""
						+ football.getId() + "\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.categoryName").value("Football"));
	}

	@Test
	@AsUser("anna")
	void deletesAFeed() throws Exception {
		mockMvc.perform(delete("/api/feeds/" + kicker.getId()).with(csrf()))
				.andExpect(status().isNoContent());

		assertThat(feedRepository.findById(kicker.getId())).isEmpty();
	}

	@Test
	@AsUser("anna")
	void neverReachesAnotherTenantsFeed() throws Exception {
		mockMvc.perform(put("/api/feeds/" + foreignFeed.getId()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"Gekapert\", \"url\": \"https://gekapert.example/rss\", \"categoryId\": \""
						+ fussball.getId() + "\"}"))
				.andExpect(status().isNotFound());
		mockMvc.perform(delete("/api/feeds/" + foreignFeed.getId()).with(csrf()))
				.andExpect(status().isNotFound());
	}

	@Test
	@AsUser("ben")
	void staysClosedToRegularUsers() throws Exception {
		mockMvc.perform(get("/api/feeds")).andExpect(status().isForbidden());
	}
}
