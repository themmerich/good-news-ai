package de.prime_ux.goodnews.auth;

import static org.assertj.core.api.Assertions.assertThat;

import de.prime_ux.goodnews.TestcontainersConfiguration;
import de.prime_ux.goodnews.branches.BranchRepository;
import de.prime_ux.goodnews.tenants.TenantLogoRepository;
import de.prime_ux.goodnews.tenants.TenantRepository;
import de.prime_ux.goodnews.users.AppUser;
import de.prime_ux.goodnews.users.AppUserRepository;
import de.prime_ux.goodnews.users.UserRole;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * The first super-user, from the configuration. The runner is driven by hand against the real
 * repository; the context's own instance has no properties set and does nothing.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class SuperuserBootstrapTest {

	@Autowired
	private AppUserRepository appUserRepository;



	@Autowired
	private TenantLogoRepository tenantLogoRepository;

	@Autowired
	private BranchRepository branchRepository;

	@Autowired
	private TenantRepository tenantRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@BeforeEach
	void cleanDatabase() {
		tenantLogoRepository.deleteAll();
		appUserRepository.deleteAll();
		branchRepository.deleteAll();
		tenantRepository.deleteAll();
	}

	private void run(String username, String password) {
		new SuperuserBootstrap(appUserRepository, passwordEncoder, username, password)
				.run(new DefaultApplicationArguments());
	}

	private List<AppUser> superusers() {
		return appUserRepository.findAll().stream().filter(AppUser::isSuperuser).toList();
	}

	@Test
	void createsTheSuperuserWhenNoneExistsAndBothPropertiesAreSet() {
		run(" root ", "top-secret");

		assertThat(superusers()).hasSize(1);
		AppUser user = superusers().getFirst();
		assertThat(user.getUsername()).isEqualTo("root");
		assertThat(user.getRole()).isEqualTo(UserRole.SUPERUSER);
		assertThat(user.getTenant()).isNull();
		assertThat(user.isActive()).isTrue();
		assertThat(passwordEncoder.matches("top-secret", user.getPasswordHash())).isTrue();
	}

	@Test
	void doesNothingWhenASuperuserExists() {
		run("root", "top-secret");

		// A second start with other values changes nothing: the first super-user stands.
		run("other", "other-secret");

		assertThat(superusers()).extracting(AppUser::getUsername).containsExactly("root");
	}

	@Test
	void doesNothingWhenAPropertyIsMissing() {
		run("root", "");
		run("", "top-secret");

		assertThat(superusers()).isEmpty();
	}
}
