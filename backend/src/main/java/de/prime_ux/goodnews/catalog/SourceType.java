package de.prime_ux.goodnews.catalog;

/**
 * How a source is read. The type decides which reader touches it and nothing else: both hand back
 * the same articles, so everything downstream is blind to the difference.
 */
public enum SourceType {

	/** An RSS or Atom feed — structured, and the normal case. */
	FEED,

	/** A plain web page, read because the site offers no feed. */
	PAGE
}
