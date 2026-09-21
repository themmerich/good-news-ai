import { buildTree, isLeaf, leaves, siblingPosition, TreeItem } from './category-tree';

function item(id: string, parentId: string | null, name: string, sortOrder: number): TreeItem {
  return { id, parentId, name, sortOrder };
}

describe('buildTree', () => {
  it('nests the children under their parent', () => {
    const tree = buildTree([
      item('sport', null, 'Sport', 0),
      item('fussball', 'sport', 'Fußball', 0),
      item('football', 'sport', 'Football', 1),
    ]);

    expect(tree).toHaveLength(1);
    expect(tree[0].item.name).toBe('Sport');
    expect(tree[0].children.map((child) => child.item.name)).toEqual(['Fußball', 'Football']);
  });

  it('orders siblings by position, and by name where two share one', () => {
    const tree = buildTree([item('b', null, 'Beta', 1), item('a', null, 'Alpha', 1), item('c', null, 'Gamma', 0)]);

    expect(tree.map((node) => node.item.name)).toEqual(['Gamma', 'Alpha', 'Beta']);
  });

  it('keeps a node whose parent is missing rather than losing it', () => {
    // Only happens if the backend contradicts itself — but a category nobody can see is worse
    // than one in the wrong place.
    const tree = buildTree([item('orphan', 'gone', 'Waise', 0)]);

    expect(tree.map((node) => node.item.name)).toEqual(['Waise']);
  });

  it('is empty for an empty catalog', () => {
    expect(buildTree([])).toEqual([]);
  });
});

describe('isLeaf and leaves', () => {
  const tree = buildTree([item('sport', null, 'Sport', 0), item('fussball', 'sport', 'Fußball', 0), item('angular', null, 'Angular', 1)]);

  it('calls a category without children a leaf', () => {
    expect(isLeaf(tree[0])).toBe(false);
    expect(isLeaf(tree[1])).toBe(true);
  });

  it('collects every category that may carry feeds', () => {
    // Sport carries subcategories and therefore no feeds; Angular stands alone and does.
    expect(leaves(tree).map((leaf) => leaf.name)).toEqual(['Fußball', 'Angular']);
  });
});

describe('siblingPosition', () => {
  const tree = buildTree([
    item('sport', null, 'Sport', 0),
    item('fussball', 'sport', 'Fußball', 0),
    item('football', 'sport', 'Football', 1),
    item('angular', null, 'Angular', 1),
  ]);

  it('finds a top-level category among its own row', () => {
    expect(siblingPosition(tree, 'angular')).toEqual({ index: 1, count: 2 });
  });

  it('finds a child among its siblings, not among the top level', () => {
    expect(siblingPosition(tree, 'football')).toEqual({ index: 1, count: 2 });
  });

  it('answers with nothing for a category that is not in the tree', () => {
    expect(siblingPosition(tree, 'gone')).toBeNull();
  });
});
