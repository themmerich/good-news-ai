package de.prime_ux.goodnews.news;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.prime_ux.goodnews.TestcontainersConfiguration;
import de.prime_ux.goodnews.auth.AsUser;
import de.prime_ux.goodnews.catalog.Category;
import de.prime_ux.goodnews.catalog.CategoryRepository;
import de.prime_ux.goodnews.catalog.Feed;
import de.prime_ux.goodnews.catalog.FeedRepository;
import de.prime_ux.goodnews.catalog.SourceType;
import de.prime_ux.goodnews.reading.StubHttpFetcher;
import de.prime_ux.goodnews.tenants.Tenant;
import de.prime_ux.goodnews.tenants.TenantRepository;
import de.prime_ux.goodnews.users.AppUser;
import de.prime_ux.goodnews.users.AppUserRepository;
import de.prime_ux.goodnews.users.UserRole;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class NewsControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private NewsRunRepository runRepository;

	@Autowired
	private ArticleRepository articleRepository;

	@Autowired
	private CategoryRepository categoryRepository;

	@Autowired
	private FeedRepository feedRepository;

	@Autowired
	private AppUserRepository appUserRepository;

	@Autowired
	private TenantRepository tenantRepository;

	@Autowired
	private StubHttpFetcher fetcher;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private Tenant tenant;
	private Tenant otherTenant;
	private Feed kicker;
	private AppUser uwe;

	@BeforeEach
	void cleanDatabaseAndPrepareACatalog() {
		this.fetcher.clear();
		this.articleRepository.deleteAll();
		this.runRepository.deleteAll();
		this.feedRepository.deleteAll();
		this.categoryRepository.deleteAll();
		this.appUserRepository.deleteAll();
		this.tenantRepository.deleteAll();
		this.tenant = this.tenantRepository.save(new Tenant("Musterfirma GmbH", "musterfirma"));
		this.otherTenant = this.tenantRepository.save(new Tenant("Beispiel AG", "beispiel-ag"));
		this.kicker = this.feedRepository
				.save(new Feed(this.tenant, "kicker", "https://kicker.example/rss", SourceType.FEED));
		this.appUserRepository
				.save(new AppUser(this.tenant, "ben", "Ben", "Benutzer", "{noop}irrelevant", UserRole.USER));
		this.uwe = this.appUserRepository
				.save(new AppUser(this.tenant, "uwe", "Uwe", "User", "{noop}irrelevant", UserRole.USER));
	}

	@Test
	@AsUser("ben")
	void startsARunAndAnswersWithSomethingToAskAfter() throws Exception {
		UUID runId = startRun();

		this.mockMvc.perform(get("/api/news/runs/" + runId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(runId.toString()))
				.andExpect(jsonPath("$.startedAt").exists());
	}

	/**
	 * A run belongs to the tenant, not to whoever pressed. Two people pressing share one pass and
	 * one progress bar rather than fetching the same sources twice over.
	 *
	 * <p>The pass in flight is written here rather than started, because a run over no sources is
	 * finished before a second call can reach it — and a second press after the first pass ended
	 * is quite correctly a second pass. Pressing twice in a row would test the clock, not the rule.
	 */
	@Test
	@AsUser("ben")
	void givesASecondPresserTheRunThatIsAlreadyGoing() throws Exception {
		NewsRun going = this.runRepository.save(new NewsRun(this.tenant));

		UUID joined = startRunAs(this.uwe);

		assertThat(joined).isEqualTo(going.getId());
		assertThat(this.runRepository.count()).isOne();
	}

	@Test
	@AsUser("ben")
	void listsTheArticlesOfTheOwnTenantOnly() throws Exception {
		article("Eigene Meldung", this.kicker, Instant.parse("2026-09-20T10:00:00Z"));
		Feed foreign = this.feedRepository
				.save(new Feed(this.otherTenant, "Fremd", "https://fremd.example/rss", SourceType.FEED));
		article("Fremde Meldung", foreign, Instant.parse("2026-09-20T11:00:00Z"));

		this.mockMvc.perform(get("/api/news/articles"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].title").value("Eigene Meldung"))
				.andExpect(jsonPath("$[0].sourceName").value("kicker"));
	}

	@Test
	@AsUser("ben")
	void putsTheNewestStoryFirst() throws Exception {
		article("Aeltere", this.kicker, Instant.parse("2026-09-19T08:00:00Z"));
		article("Neuere", this.kicker, Instant.parse("2026-09-20T08:00:00Z"));

		this.mockMvc.perform(get("/api/news/articles"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$..title", Matchers.contains("Neuere", "Aeltere")));
	}

	@Test
	@AsUser("ben")
	void namesTheCategoryWhereThereIsOneAndLeavesItEmptyWhereThereIsNot() throws Exception {
		Category sport = this.categoryRepository.save(new Category(this.tenant, "Sport", 0));
		UUID placed = article("Eingeordnet", this.kicker, Instant.parse("2026-09-20T10:00:00Z"));
		rate(placed, sport.getId(), 8);
		article("Unzugeordnet", this.kicker, Instant.parse("2026-09-19T10:00:00Z"));

		this.mockMvc.perform(get("/api/news/articles"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].categoryName").value("Sport"))
				.andExpect(jsonPath("$[0].ranking").value(8))
				.andExpect(jsonPath("$[1].categoryId").doesNotExist())
				.andExpect(jsonPath("$[1].ranking").doesNotExist());
	}

	/**
	 * An empty ranking is not a zero, it is an open question. A threshold would otherwise swallow
	 * exactly the stories the AI failed to get to, which are the ones worth noticing.
	 */
	@Test
	@AsUser("ben")
	void letsAnUnratedStoryThroughAtAnyThreshold() throws Exception {
		rate(article("Hoch", this.kicker, Instant.parse("2026-09-20T10:00:00Z")), null, 9);
		rate(article("Niedrig", this.kicker, Instant.parse("2026-09-20T09:00:00Z")), null, 3);
		article("Unbewertet", this.kicker, Instant.parse("2026-09-20T08:00:00Z"));

		this.mockMvc.perform(get("/api/news/articles").param("minRanking", "7"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$..title", Matchers.contains("Hoch", "Unbewertet")));
	}

	@Test
	@AsUser("ben")
	void neverReachesAnotherTenantsRun() throws Exception {
		NewsRun foreign = this.runRepository.save(new NewsRun(this.otherTenant));

		this.mockMvc.perform(get("/api/news/runs/" + foreign.getId())).andExpect(status().isNotFound());
		this.mockMvc.perform(get("/api/news/runs/" + UUID.randomUUID())).andExpect(status().isNotFound());
	}

	private UUID startRun() throws Exception {
		String body = this.mockMvc.perform(post("/api/news/runs").with(csrf()))
				.andExpect(status().isAccepted())
				.andReturn().getResponse().getContentAsString();
		return UUID.fromString(JsonPath.read(body, "$.id"));
	}

	/** The same shape a session has: the principal is the user id, as AsUserFactory builds it. */
	private UUID startRunAs(AppUser other) throws Exception {
		String body = this.mockMvc.perform(post("/api/news/runs").with(csrf())
				.with(user(other.getId().toString()).roles(other.getRole().name())))
				.andExpect(status().isAccepted())
				.andReturn().getResponse().getContentAsString();
		return UUID.fromString(JsonPath.read(body, "$.id"));
	}

	private UUID article(String title, Feed feed, Instant publishedAt) {
		Article article = this.articleRepository
				.save(new Article(feed, title, title, "https://kicker.example/" + title, publishedAt, "Teaser"));
		return article.getId();
	}

	/**
	 * What the AI stage will write once it exists. Set through JDBC on purpose: nothing in the
	 * application may move these fields yet, and a setter added for a test would claim otherwise.
	 */
	private void rate(UUID articleId, UUID categoryId, Integer ranking) {
		this.jdbcTemplate.update(
				"update articles set category_id = ?, ranking = ?, positive_summary = ?, processed_at = ?"
						+ " where id = ?",
				categoryId, ranking, "Kurzfassung", Timestamp.from(Instant.now()), articleId);
	}
}
