package de.prime_ux.goodnews.reading;

/**
 * Reads one source and hands back what it offers. A feed and a plain web page both end up here,
 * and from this point on nothing downstream can tell which it was: the entries have the same
 * shape either way, so ranking, summarizing and storing work on one kind of thing.
 */
public interface SourceReader {

	/**
	 * @throws SourceReadException when the address does not answer, answers with an error, or
	 *                             holds nothing this reader can make entries out of
	 */
	SourceContent read(String url);
}
