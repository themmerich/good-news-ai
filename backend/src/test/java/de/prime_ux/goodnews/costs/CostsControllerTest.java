package de.prime_ux.goodnews.costs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import de.prime_ux.goodnews.TestcontainersConfiguration;
import de.prime_ux.goodnews.auth.AsUser;
import de.prime_ux.goodnews.tenants.Tenant;
import de.prime_ux.goodnews.tenants.TenantRepository;
import de.prime_ux.goodnews.users.AppUser;
import de.prime_ux.goodnews.users.AppUserRepository;
import de.prime_ux.goodnews.users.UserRole;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The two endpoints behind the Kosten page.
 *
 * <p>The rows are placed at the present moment rather than at a fixed date, because the endpoints ask
 * the real clock. Where a boundary falls is settled in {@link PeriodsTest}; what is checked here
 * is who may ask and whose rows come back.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CostsControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private AiCallRepository repository;

	@Autowired
	private AppUserRepository appUserRepository;

	@Autowired
	private TenantRepository tenantRepository;

	private Tenant tenant;
	private Tenant otherTenant;

	@BeforeEach
	void cleanDatabaseAndCreateUsers() {
		this.repository.deleteAll();
		this.appUserRepository.deleteAll();
		this.tenantRepository.deleteAll();
		this.tenant = this.tenantRepository.save(new Tenant("Musterfirma GmbH", "musterfirma"));
		this.otherTenant = this.tenantRepository.save(new Tenant("Beispiel AG", "beispiel-ag"));
		this.appUserRepository.save(new AppUser(this.tenant, "anna", "Anna", "Admin", "{noop}irrelevant",
				UserRole.ADMIN));
		this.appUserRepository.save(new AppUser(this.tenant, "ben", "Ben", "Benutzer", "{noop}irrelevant",
				UserRole.USER));
	}

	@Test
	@AsUser("anna")
	void addsUpWhatTodayCost() throws Exception {
		call(this.tenant, justNow(), "1.000000");
		call(this.tenant, justNow(), "0.500000");

		String body = this.mockMvc.perform(get("/api/costs/summary"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.day.calls").value(2))
				.andExpect(jsonPath("$.year.calls").value(2))
				.andReturn().getResponse().getContentAsString();

		assertThat(costAt(body, "$.day.cost")).isEqualByComparingTo("1.500000");
		assertThat(costAt(body, "$.year.cost")).isEqualByComparingTo("1.500000");
	}

	/** Nothing spent is 0.00 $, not an empty tile — an empty one would read like a fault. */
	@Test
	@AsUser("anna")
	void answersWithZeroesForATenantThatHasNotCalledAnything() throws Exception {
		String body = this.mockMvc.perform(get("/api/costs/summary"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.day.calls").value(0))
				.andExpect(jsonPath("$.month.calls").value(0))
				.andReturn().getResponse().getContentAsString();

		assertThat(costAt(body, "$.day.cost")).isEqualByComparingTo("0");
		assertThat(costAt(body, "$.year.cost")).isEqualByComparingTo("0");
	}

	@Test
	@AsUser("anna")
	void listsTheCallsNewestFirst() throws Exception {
		call(this.tenant, minutesAgo(30), "1.000000");
		call(this.tenant, minutesAgo(2), "2.000000");

		String body = this.mockMvc.perform(get("/api/costs/calls"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.totalCalls").value(2))
				.andExpect(jsonPath("$.page").value(0))
				.andExpect(jsonPath("$.calls.length()").value(2))
				.andExpect(jsonPath("$.calls[0].purpose").value("RATING"))
				.andExpect(jsonPath("$.calls[0].model").value("claude-sonnet-5"))
				.andExpect(jsonPath("$.calls[0].inputTokens").value(1000))
				.andExpect(jsonPath("$.calls[0].outputTokens").value(100))
				.andExpect(jsonPath("$.calls[0].id").exists())
				.andExpect(jsonPath("$.calls[0].calledAt").exists())
				.andReturn().getResponse().getContentAsString();

		assertThat(costAt(body, "$.calls[0].costUsd")).isEqualByComparingTo("2.000000");
	}

	/** A call whose model had no rate shows no amount at all rather than a zero. */
	@Test
	@AsUser("anna")
	void handsOutACallWithoutAnAmountAsSuch() throws Exception {
		call(this.tenant, minutesAgo(1), null);

		this.mockMvc.perform(get("/api/costs/calls"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.calls[0].costUsd").value(Matchers.nullValue()));
	}

	@Test
	@AsUser("anna")
	void cutsAnOversizedPageBackToTheCeiling() throws Exception {
		call(this.tenant, minutesAgo(1), "1.000000");

		this.mockMvc.perform(get("/api/costs/calls?page=0&size=1000"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.size").value(100));
	}

	@Test
	@AsUser("anna")
	void leavesAnotherTenantsCallsOutOfBothAnswers() throws Exception {
		call(this.tenant, justNow(), "1.000000");
		call(this.otherTenant, justNow(), "50.000000");

		this.mockMvc.perform(get("/api/costs/calls"))
				.andExpect(jsonPath("$.totalCalls").value(1));
		String body = this.mockMvc.perform(get("/api/costs/summary"))
				.andReturn().getResponse().getContentAsString();

		assertThat(costAt(body, "$.day.cost")).isEqualByComparingTo("1.000000");
	}

	@Test
	@AsUser("ben")
	void deniesTheSummaryToNonAdmins() throws Exception {
		this.mockMvc.perform(get("/api/costs/summary")).andExpect(status().isForbidden());
	}

	@Test
	@AsUser("ben")
	void deniesTheListToNonAdmins() throws Exception {
		this.mockMvc.perform(get("/api/costs/calls")).andExpect(status().isForbidden());
	}

	private static BigDecimal costAt(String body, String path) {
		// Read into an Object first: JsonPath.read infers its return type from the target, and a
		// String.valueOf(...) right on the call resolves to the char[] overload.
		Object amount = JsonPath.read(body, path);
		return new BigDecimal(amount.toString());
	}

	private void call(Tenant tenant, Instant calledAt, String costUsd) {
		this.repository.save(new AiCall(tenant, Purpose.RATING, "claude-sonnet-5", 1_000, 100,
				costUsd == null ? null : new BigDecimal(costUsd), calledAt));
	}

	/** For the sums: right now, so it falls into today whenever the test happens to run. */
	private static Instant justNow() {
		return Instant.now();
	}

	/** For the order of the list, where only the distance between two rows matters. */
	private static Instant minutesAgo(int minutes) {
		return Instant.now().minus(minutes, ChronoUnit.MINUTES);
	}
}
