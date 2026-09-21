package de.prime_ux.goodnews.aisettings;

import de.prime_ux.goodnews.tenants.Tenant;
import org.springframework.stereotype.Service;

/**
 * Tries a key out with the smallest call the API allows, so an admin learns on the settings page
 * whether it works rather than hours later from calls that started failing.
 *
 * <p>A rejected key is a normal outcome and comes back as a result, not an exception.
 */
@Service
public class ApiKeyTester {

	/** The failure message is the provider's own reason, shown as a detail in the UI. */
	public record ApiKeyTestResult(boolean success, String message) {

		static ApiKeyTestResult ok() {
			return new ApiKeyTestResult(true, "");
		}

		static ApiKeyTestResult failure(String message) {
			return new ApiKeyTestResult(false, message);
		}
	}

	private final ChatClients chatClients;

	ApiKeyTester(ChatClients chatClients) {
		this.chatClients = chatClients;
	}

	/** @param tenant whose admin is trying the key; the call is billed to the key */
	public ApiKeyTestResult test(Tenant tenant, String apiKey) {
		try {
			// The answer is thrown away; that the call was accepted is the whole point.
			this.chatClients.withApiKey(apiKey).prompt().user("ping").call().chatResponse();
			return ApiKeyTestResult.ok();
		} catch (RuntimeException e) {
			return ApiKeyTestResult.failure(e.getMessage());
		}
	}
}
