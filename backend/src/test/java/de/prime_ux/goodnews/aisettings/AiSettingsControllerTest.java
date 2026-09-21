package de.prime_ux.goodnews.aisettings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import de.prime_ux.goodnews.TestcontainersConfiguration;
import de.prime_ux.goodnews.branches.BranchRepository;
import de.prime_ux.goodnews.tenants.Tenant;
import de.prime_ux.goodnews.tenants.TenantLogoRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;
import de.prime_ux.goodnews.auth.AsUser;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AiSettingsControllerTest {

	private static final String KEY_JSON = """
			{"apiKey": "%s"}""";
	private static final String A_KEY = "sk-ant-api03-testkey_0123456789";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TenantAiSettingsRepository tenantAiSettingsRepository;



	@Autowired
	private AppUserRepository appUserRepository;

	@Autowired
	private TenantRepository tenantRepository;

	@Autowired
	private TenantLogoRepository tenantLogoRepository;

	@Autowired
	private BranchRepository branchRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private Tenant tenant;

	@BeforeEach
	void cleanDatabaseAndCreateAdmin() {
		tenantAiSettingsRepository.deleteAll();
		tenantLogoRepository.deleteAll();
		appUserRepository.deleteAll();
		branchRepository.deleteAll();
		tenantRepository.deleteAll();
		tenant = tenantRepository.save(new Tenant("Musterfirma GmbH", "musterfirma"));
		appUserRepository.save(new AppUser(tenant, "admin", "Anna", "Admin", "{noop}irrelevant", UserRole.ADMIN));
	}

	@Test
	@AsUser("admin")
	void reportsThatNoKeyIsStoredWhileTheTenantRunsOnThePlatformsCredentials() throws Exception {
		mockMvc.perform(get("/api/settings/ai"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ownKey").value(false));

		// Reading must not create a row; a tenant without a key has nothing stored.
		assertThat(tenantAiSettingsRepository.count()).isZero();
	}

	@Test
	@AsUser("admin")
	void storesAKeyWithoutEverHandingItBack() throws Exception {
		mockMvc.perform(put("/api/settings/ai").with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(KEY_JSON.formatted(A_KEY)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ownKey").value(true))
				// The response says that a key exists, never what it is.
				.andExpect(content().string(not(containsString(A_KEY))));

		mockMvc.perform(get("/api/settings/ai"))
				.andExpect(jsonPath("$.ownKey").value(true))
				.andExpect(content().string(not(containsString(A_KEY))));

		// And it does not stand in the column either.
		String stored = jdbcTemplate.queryForObject("SELECT api_key FROM tenant_ai_settings", String.class);
		assertThat(stored).isNotNull().doesNotContain(A_KEY);
		assertThat(tenantAiSettingsRepository.findByTenantId(tenant.getId()).orElseThrow().getApiKey())
				.isEqualTo(A_KEY);
	}

	@Test
	@AsUser("admin")
	void putsTheTenantBackOnThePlatformsCredentials() throws Exception {
		mockMvc.perform(put("/api/settings/ai").with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(KEY_JSON.formatted(A_KEY)))
				.andExpect(status().isOk());

		mockMvc.perform(delete("/api/settings/ai").with(csrf()))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/settings/ai")).andExpect(jsonPath("$.ownKey").value(false));
		assertThat(tenantAiSettingsRepository.findByTenantId(tenant.getId()).orElseThrow().hasApiKey()).isFalse();
	}

	@Test
	@AsUser("admin")
	void refusesSomethingThatIsNotAKey() throws Exception {
		// Catches a pasted mail address or a truncated line; whether the key works
		// is the provider's answer, which is what the test endpoint is for.
		for (String notAKey : new String[] { "", "anna@example.com", "sk-live-0123456789" }) {
			mockMvc.perform(put("/api/settings/ai").with(csrf())
					.contentType(MediaType.APPLICATION_JSON).content(KEY_JSON.formatted(notAKey)))
					.andExpect(status().isBadRequest());
		}
		assertThat(tenantAiSettingsRepository.count()).isZero();
	}
}
