/**
 * A category as the catalog holds it. The list is flat — no parents, no levels. Taken together
 * the names are the vocabulary the AI picks from when it sorts a single story, which is why a
 * source carries none: one feed brings politics, society and sport through the same address.
 */
export type Category = {
  id: string;
  name: string;
  sortOrder: number;
};

/**
 * What the dialog sends. `sortOrder` left out means "at the end", which is what creating a
 * category should do and what renaming one should leave alone.
 */
export type CategoryInput = {
  name: string;
  sortOrder?: number;
};

/**
 * How a source is read. The type decides which reader touches it and nothing else: both hand back
 * the same articles, so the rest of the app is blind to the difference.
 */
export type SourceType = 'FEED' | 'PAGE';

/** A source as the admin page lists it. */
export type Feed = {
  id: string;
  name: string;
  url: string;
  type: SourceType;
};

export type FeedInput = {
  name: string;
  url: string;
  type: SourceType;
};

/**
 * A feed the search found behind a web address, already confirmed by reading it. The count says
 * how much stands in it right now — the quickest way to tell a live feed from an abandoned one.
 */
export type FoundFeed = {
  url: string;
  title: string;
  entryCount: number;
};

/**
 * A category as the picking page shows it: the tenant's list, plus whether this user ticked it.
 * The choice orders the board and nothing else — every source is fetched and rated either way,
 * because a story's category is only known once it has been rated.
 */
export type PickedCategory = {
  id: string;
  name: string;
  sortOrder: number;
  selected: boolean;
};
