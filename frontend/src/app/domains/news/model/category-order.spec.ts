import { positionOf, sortCategories } from './category-order';

function category(id: string, name: string, sortOrder: number) {
  return { id, name, sortOrder };
}

describe('sortCategories', () => {
  it('puts the categories in their position order', () => {
    const sorted = sortCategories([category('c', 'Sport', 2), category('a', 'Politik', 0), category('b', 'Soziales', 1)]);

    expect(sorted.map((entry) => entry.name)).toEqual(['Politik', 'Soziales', 'Sport']);
  });

  it('falls back to the name where two share a position', () => {
    const sorted = sortCategories([category('a', 'Sport', 0), category('b', 'Politik', 0)]);

    expect(sorted.map((entry) => entry.name)).toEqual(['Politik', 'Sport']);
  });

  it('leaves the list it was given untouched', () => {
    const categories = [category('a', 'Sport', 1), category('b', 'Politik', 0)];

    sortCategories(categories);

    expect(categories.map((entry) => entry.name)).toEqual(['Sport', 'Politik']);
  });
});

describe('positionOf', () => {
  const categories = [category('a', 'Sport', 2), category('b', 'Politik', 0), category('c', 'Soziales', 1)];

  it('counts the position in the sorted list, not in the one it was given', () => {
    expect(positionOf(categories, 'a')).toEqual({ index: 2, count: 3 });
    expect(positionOf(categories, 'b')).toEqual({ index: 0, count: 3 });
  });

  it('answers nothing for a category that is not in the list', () => {
    expect(positionOf(categories, 'nope')).toBeNull();
  });
});
