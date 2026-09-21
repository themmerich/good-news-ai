package de.prime_ux.goodnews.tenants;

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
import de.prime_ux.goodnews.branches.Branch;
import de.prime_ux.goodnews.branches.BranchRepository;
import de.prime_ux.goodnews.users.AppUser;
import de.prime_ux.goodnews.users.AppUserRepository;
import de.prime_ux.goodnews.users.UserRole;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The tenants as the super-user manages them: listed with what hangs on them, created, renamed,
 * and deleted with everything of theirs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TenantControllerTest {

	private static final String TENANT_JSON = """
			{"name": "%s", "slug": "%s"}""";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private TenantRepository tenantRepository;

	@Autowired
	private AppUserRepository appUserRepository;


	@Autowired
	private BranchRepository branchRepository;


	@Autowired
	private TenantLogoRepository tenantLogoRepository;




	private Tenant musterfirma;
	private Tenant beispiel;

	@BeforeEach
	void cleanDatabaseAndCreateTenants() {
		tenantLogoRepository.deleteAll();
		appUserRepository.deleteAll();
		branchRepository.deleteAll();
		// The AI settings cascade from the tenant.
		tenantRepository.deleteAll();
		musterfirma = tenantRepository.save(new Tenant("Musterfirma GmbH", "musterfirma"));
		beispiel = tenantRepository.save(new Tenant("Beispiel AG", "beispiel-ag"));
		appUserRepository.save(new AppUser(musterfirma, "anna", "Anna", "Admin", "{noop}x", UserRole.ADMIN));
		appUserRepository.save(new AppUser(musterfirma, "uwe", "Uwe", "User", "{noop}x", UserRole.USER));
		appUserRepository.save(AppUser.superuser("sina", "Sina", "Super", "{noop}x"));
	}

	@Test
	@AsUser("sina")
	void listsEveryTenantByNameWithWhatHangsOnIt() throws Exception {
		mockMvc.perform(get("/api/tenants"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].name").value("Beispiel AG"))
				.andExpect(jsonPath("$[0].slug").value("beispiel-ag"))
				.andExpect(jsonPath("$[0].userCount").value(0))
				.andExpect(jsonPath("$[1].name").value("Musterfirma GmbH"))
				// The super-user is nobody's user; two of Musterfirma's own.
				.andExpect(jsonPath("$[1].userCount").value(2))
				.andExpect(jsonPath("$[1].createdAt").isNotEmpty());
	}

	@Test
	@AsUser("sina")
	void createsATenant() throws Exception {
		mockMvc.perform(post("/api/tenants").with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(TENANT_JSON.formatted(" Neue GmbH ", "Neue-GmbH")))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.name").value("Neue GmbH"))
				// Stored the way the login compares it.
				.andExpect(jsonPath("$.slug").value("neue-gmbh"))
				.andExpect(jsonPath("$.userCount").value(0));

		assertThat(tenantRepository.findBySlug("neue-gmbh")).isPresent();
	}

	@Test
	@AsUser("sina")
	void refusesAKennungThatIsTakenOrMalformed() throws Exception {
		mockMvc.perform(post("/api/tenants").with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(TENANT_JSON.formatted("Noch eine", "musterfirma")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.reason").value("slug"));

		for (String bad : new String[] { "Musterfirma GmbH", "-musterfirma", "muster--firma", "" }) {
			mockMvc.perform(post("/api/tenants").with(csrf())
					.contentType(MediaType.APPLICATION_JSON).content(TENANT_JSON.formatted("Noch eine", bad)))
					.andExpect(status().isBadRequest());
		}
		assertThat(tenantRepository.count()).isEqualTo(2);
	}

	@Test
	@AsUser("sina")
	void renamesATenantAndItsKennung() throws Exception {
		mockMvc.perform(put("/api/tenants/" + beispiel.getId()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(TENANT_JSON.formatted("Beispiel SE", "beispiel-se")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Beispiel SE"))
				.andExpect(jsonPath("$.slug").value("beispiel-se"));

		// Keeping the own Kennung is no conflict; taking another tenant's is.
		mockMvc.perform(put("/api/tenants/" + beispiel.getId()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(TENANT_JSON.formatted("Beispiel SE", "beispiel-se")))
				.andExpect(status().isOk());
		mockMvc.perform(put("/api/tenants/" + beispiel.getId()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(TENANT_JSON.formatted("Beispiel SE", "musterfirma")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.reason").value("slug"));
		mockMvc.perform(put("/api/tenants/" + UUID.randomUUID()).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(TENANT_JSON.formatted("Niemand", "niemand")))
				.andExpect(status().isNotFound());
	}

	@Test
	@AsUser("sina")
	void deletesATenantWithEverythingOfTheirs() throws Exception {
		UUID id = musterfirma.getId();
		branchRepository.save(new Branch(musterfirma, "Zentrale", true));

		mockMvc.perform(delete("/api/tenants/" + id).with(csrf()))
				.andExpect(status().isNoContent());

		assertThat(tenantRepository.findById(id)).isEmpty();
		assertThat(appUserRepository.findAllByTenantIdOrderByLastNameAscFirstNameAsc(id)).isEmpty();
		assertThat(branchRepository.findByTenantIdAndHeadquartersTrue(id)).isEmpty();
		// The other tenant and the super-user are untouched.
		assertThat(tenantRepository.findById(beispiel.getId())).isPresent();
		assertThat(appUserRepository.existsByRole(UserRole.SUPERUSER)).isTrue();

		mockMvc.perform(delete("/api/tenants/" + id).with(csrf())).andExpect(status().isNotFound());
	}

	@Test
	@AsUser("sina")
	void deletingTheTenantOpenInTheOwnSessionClosesIt() throws Exception {
		// Spring Session backs the servlet session, so the tenant is opened through the endpoint
		// and the session travels as its cookie, the way the browser carries it.
		Cookie session = mockMvc.perform(put("/api/auth/tenant").with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content("{\"slug\": \"beispiel-ag\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tenant.slug").value("beispiel-ag"))
				.andReturn().getResponse().getCookie("SESSION");
		assertThat(session).isNotNull();
		mockMvc.perform(get("/api/auth/me").cookie(session))
				.andExpect(jsonPath("$.tenant.slug").value("beispiel-ag"));

		mockMvc.perform(delete("/api/tenants/" + beispiel.getId()).with(csrf()).cookie(session))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/auth/me").cookie(session))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.tenant").doesNotExist());
	}

	@Test
	@AsUser("anna")
	void isNotForAdmins() throws Exception {
		mockMvc.perform(get("/api/tenants")).andExpect(status().isForbidden());
		mockMvc.perform(post("/api/tenants").with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(TENANT_JSON.formatted("Neue", "neue")))
				.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/tenants/" + beispiel.getId()).with(csrf())).andExpect(status().isForbidden());
		assertThat(tenantRepository.count()).isEqualTo(2);
	}
}
