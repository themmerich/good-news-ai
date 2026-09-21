/** A category as the catalog holds it. `parentId` is null for a top-level one. */
export type Category = {
  id: string;
  parentId: string | null;
  name: string;
  sortOrder: number;
  /** The category's own feeds, never a child's — a top-level category carries none. */
  feedCount: number;
};

/**
 * What the dialog sends. `sortOrder` left out means "at the end", which is what creating a
 * category should do and what renaming one should leave alone.
 */
export type CategoryInput = {
  name: string;
  parentId: string | null;
  sortOrder?: number;
};

/**
 * How a source is read. The type decides which reader touches it and nothing else: both hand back
 * the same articles, so the rest of the app is blind to the difference.
 */
export type SourceType = 'FEED' | 'PAGE';

/** A source as the admin page lists it, with the name of the category it hangs on. */
export type Feed = {
  id: string;
  categoryId: string;
  categoryName: string;
  name: string;
  url: string;
  type: SourceType;
};

export type FeedInput = {
  name: string;
  url: string;
  categoryId: string;
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

/** A feed as the picking page shows it: fewer fields, plus whether this user picked it. */
export type CatalogFeed = {
  id: string;
  name: string;
  url: string;
  selected: boolean;
};

/** A category on the picking page, with the feeds hanging on it. */
export type CatalogCategory = {
  id: string;
  parentId: string | null;
  name: string;
  sortOrder: number;
  feeds: CatalogFeed[];
};
