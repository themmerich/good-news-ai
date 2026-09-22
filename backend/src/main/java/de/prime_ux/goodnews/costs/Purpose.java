package de.prime_ux.goodnews.costs;

/**
 * What an AI call was for, stored as text in the database.
 *
 * <p>Two, because there are two places that call the model: rating a bundle of stories, and
 * trying out a key on the settings page. A key test costs next to nothing, but leaving it out
 * would make the page disagree with the provider's invoice.
 */
public enum Purpose {

	RATING, KEY_TEST
}
