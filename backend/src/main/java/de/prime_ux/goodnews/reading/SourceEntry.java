package de.prime_ux.goodnews.reading;

import java.time.Instant;

/**
 * One article as a source offers it, before anything of ours touches it.
 *
 * @param guid what the source calls this entry; the link where it names nothing else. Identity
 *             across runs hangs on this.
 * @param publishedAt null where the source names no date — plenty of them do not
 * @param teaser the summary the source carries, empty where it carries none
 */
public record SourceEntry(String guid, String title, String link, Instant publishedAt, String teaser) {
}
