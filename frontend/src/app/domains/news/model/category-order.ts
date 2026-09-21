/**
 * The order the categories are shown in, and so the order of the tabs on the board. It lives here
 * rather than in a component because both admin pages and the board need the same answer, and
 * because a rule with a test is cheaper to trust than one spread over three templates.
 */

/** The least a row needs to be put in order. */
export type OrderedCategory = {
  name: string;
  sortOrder: number;
};

/**
 * By position first, by name where two share one. Two categories sharing a position is not
 * supposed to happen — the backend renumbers the whole list on every move — but a list that
 * jumps around between two renders would be worse than a tie broken arbitrarily but stably.
 */
export function sortCategories<T extends OrderedCategory>(categories: T[]): T[] {
  return [...categories].sort((left, right) => left.sortOrder - right.sortOrder || left.name.localeCompare(right.name));
}

/** Where a category stands in the sorted list, and how long that list is — for the move buttons. */
export function positionOf<T extends OrderedCategory & { id: string }>(
  categories: T[],
  id: string,
): { index: number; count: number } | null {
  const ordered = sortCategories(categories);
  const index = ordered.findIndex((category) => category.id === id);
  return index === -1 ? null : { index, count: ordered.length };
}
