package de.prime_ux.goodnews.catalog;

import java.util.UUID;

/** How many feeds hang on one category, from one grouped query over all of a tenant's. */
public interface CategoryFeedCount {

	UUID getCategoryId();

	long getCount();
}
