package de.prime_ux.goodnews.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * A plain web address to look for feeds behind — `heise.de` is enough. The scheme may be left
 * off; the search fills in https, because that is what somebody pasting a domain means.
 */
record ProbeRequest(@NotBlank @Size(max = 500) String url) {

	String trimmedUrl() {
		return url.trim();
	}
}
