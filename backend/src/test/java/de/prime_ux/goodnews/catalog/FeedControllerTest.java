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
import de.prime_ux.goodnews.reading.StubHttpFetcher;
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

	@Autowired
	private StubHttpFetcher httpFetcher;

	private Tenant tenant;
	private Feed kicker;
	private Feed foreignFeed;

	@BeforeEach
	void cleanDatabaseAndCreateFeeds() {
		httpFetcher.clear();
		feedRepository.deleteAll();
		categoryRepository.deleteAll();
		appUserRepository.deleteAll();
		tenantRepository.deleteAll();
		tenant = tenantRepository.save(new Tenant("Musterfirma GmbH", "musterfirma"));
		Tenant otherTenant = tenantRepository.save(new Tenant("Beispiel AG", "beispiel-ag"));
		kicker = feedRepository.save(new Feed(tenant, "kicker", "https://kicker.example/rss", SourceType.FEED));
		foreignFeed = feedRepository.save(
				new Feed(otherTenant, "Tagesschau", "https://tagesschau.example/rss", SourceType.FEED));
		appUserRepository.save(new AppUser(tenant, "anna", "Anna", "Admin", "{noop}irrelevant", UserRole.ADMIN));
		appUserRepository.save(new AppUser(tenant, "ben", "Ben", "Benutzer", "{noop}irrelevant", UserRole.USER));
	}

	@Test
	@AsUser("anna")
	void listsTheOwnTenantsFeeds() throws Exception {
		mockMvc.perform(get("/api/feeds"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].name").value("kicker"))
				.andExpect(jsonPath("$[0].url").value("https://kicker.example/rss"))
				// A source carries no category any more; its stories are sorted one by one.
				.andExpect(jsonPath("$[0].categoryId").doesNotExist());
	}

	@Test
	@AsUser("anna")
	void createsAFeedAndTrimsWhatWasPasted() throws Exception {
		httpFetcher.feed("https://sportschau.example/rss", "rss2.xml");

		mockMvc.perform(post("/api/feeds").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \" Sportschau \", \"url\": \" https://sportschau.example/rss \","
						+ " \"type\": \"FEED\"}"))
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
						+ " \"type\": \"FEED\"}"))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.reason").value("url"));
	}

	@Test
	@AsUser("anna")
	void refusesSomethingThatIsNoUrl() throws Exception {
		mockMvc.perform(post("/api/feeds").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"kicker\", \"url\": \"kicker.example\", \"type\": \"FEED\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@AsUser("anna")
	void renamesAFeed() throws Exception {
		mockMvc.perform(put("/api/feeds/" + kicker.getId()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"kicker.de\", \"url\": \"https://kicker.example/rss\","
						+ " \"type\": \"FEED\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("kicker.de"));
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
				.content("{\"name\": \"Gekapert\", \"url\": \"https://gekapert.example/rss\","
						+ " \"type\": \"FEED\"}"))
				.andExpect(status().isNotFound());
		mockMvc.perform(delete("/api/feeds/" + foreignFeed.getId()).with(csrf()))
				.andExpect(status().isNotFound());
	}

	@Test
	@AsUser("anna")
	void answersASearchWithTheFeedsBehindAnAddress() throws Exception {
		httpFetcher.html("https://beispiel.example/", "declares-feed.html");
		httpFetcher.feed("https://beispiel.example/feed.rss", "rss2.xml");

		mockMvc.perform(post("/api/feeds/probe").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"url\": \"  beispiel.example \"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.feeds.length()").value(1))
				.andExpect(jsonPath("$.feeds[0].url").value("https://beispiel.example/feed.rss"))
				.andExpect(jsonPath("$.feeds[0].title").value("Beispiel News"))
				.andExpect(jsonPath("$.feeds[0].entryCount").value(2));
	}

	@Test
	@AsUser("anna")
	void answersASearchWithNothingWhereASiteHasNoFeed() throws Exception {
		httpFetcher.html("https://ohne.example/", "silent-home.html");

		// Not an error: the site simply has none, and the page then offers to read it directly.
		mockMvc.perform(post("/api/feeds/probe").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"url\": \"https://ohne.example/\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.feeds.length()").value(0));
	}

	@Test
	@AsUser("anna")
	void refusesASearchForSomethingThatIsNoAddress() throws Exception {
		mockMvc.perform(post("/api/feeds/probe").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"url\": \"kein komma url\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@AsUser("ben")
	void keepsTheSearchToTheAdmins() throws Exception {
		mockMvc.perform(post("/api/feeds/probe").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"url\": \"https://beispiel.example/\"}"))
				.andExpect(status().isForbidden());
	}

	@Test
	@AsUser("anna")
	void readsAFeedOnceBeforeStoringIt() throws Exception {
		// Nothing answers at this address, so it is caught where it was typed rather than hours
		// later in a run that quietly brought nothing back.
		mockMvc.perform(post("/api/feeds").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"Tot\", \"url\": \"https://tot.example/rss\", \"type\": \"FEED\"}"))
				.andExpect(status().isBadRequest());

		assertThat(feedRepository.findAllOfTenant(tenant.getId())).hasSize(1);
	}

	@Test
	@AsUser("anna")
	void takesAWebPageAsItComes() throws Exception {
		// Whether articles can be pulled out of a page only shows when the reader tries, and that
		// belongs to the run rather than to the form.
		mockMvc.perform(post("/api/feeds").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"NFL\", \"url\": \"https://nfl.example/news/\", \"type\": \"PAGE\"}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.type").value("PAGE"));
	}

	@Test
	@AsUser("anna")
	void refusesABodyThatLeavesTheTypeOpen() throws Exception {
		mockMvc.perform(post("/api/feeds").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\": \"kicker\", \"url\": \"https://neu.example/rss\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@AsUser("ben")
	void staysClosedToRegularUsers() throws Exception {
		mockMvc.perform(get("/api/feeds")).andExpect(status().isForbidden());
	}
}
