package de.prime_ux.goodnews.catalog;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Create and update share the same shape. The URL check is deliberately loose: it catches a name
 * typed into the wrong field and leaves whether anything answers there to the fetching, which
 * does not exist yet. The scheme is matched regardless of case, the way RFC 3986 defines it, so
 * HTTPS:// names the same source as https:// instead of counting as broken input. Surrounding
 * whitespace passes — a URL gets here by copy and paste.
 */
record FeedRequest(@NotBlank @Size(max = 200) String name,
		@NotBlank @Size(max = 500) @Pattern(regexp = "(?i)\\s*https?://\\S+\\s*") String url,
		@NotNull UUID categoryId) {

	String trimmedName() {
		return name.trim();
	}

	String trimmedUrl() {
		return url.trim();
	}
}
