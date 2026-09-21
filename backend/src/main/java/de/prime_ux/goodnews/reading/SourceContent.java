package de.prime_ux.goodnews.reading;

import java.util.List;

/**
 * What came out of a source: its own title, and the entries it offered. The title is what the
 * sources page proposes as a name when an admin picks the source from a search.
 */
public record SourceContent(String title, List<SourceEntry> entries) {
}
