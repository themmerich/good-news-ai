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

/** A feed as the admin page lists it, with the name of the category it hangs on. */
export type Feed = {
  id: string;
  categoryId: string;
  categoryName: string;
  name: string;
  url: string;
};

export type FeedInput = {
  name: string;
  url: string;
  categoryId: string;
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
