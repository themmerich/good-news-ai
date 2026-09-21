/**
 * The catalog arrives flat, with a `parentId` on every node, and is nested where it is shown.
 * Flat travels better through JSON and is far easier to assert in a test; the nesting is a
 * display concern and belongs here, next to its own tests, rather than in a component.
 */

/** The least a node needs for the tree to be built from it. */
export type TreeItem = {
  id: string;
  parentId: string | null;
  name: string;
  sortOrder: number;
};

export type TreeNode<T extends TreeItem> = {
  item: T;
  children: TreeNode<T>[];
};

/**
 * Nests the list two levels deep and puts every row of siblings in order: by position first, by
 * name where two share one.
 *
 * <p>A node whose parent is not in the list is treated as a top-level one. That only happens if
 * the backend contradicts itself, and showing such a node in the wrong place beats letting it
 * disappear without a trace.
 */
export function buildTree<T extends TreeItem>(items: T[]): TreeNode<T>[] {
  const known = new Set(items.map((item) => item.id));
  const childrenOf = new Map<string, T[]>();
  const roots: T[] = [];

  for (const item of items) {
    if (item.parentId === null || !known.has(item.parentId)) {
      roots.push(item);
    } else {
      const siblings = childrenOf.get(item.parentId) ?? [];
      siblings.push(item);
      childrenOf.set(item.parentId, siblings);
    }
  }

  return sorted(roots).map((item) => ({
    item,
    children: sorted(childrenOf.get(item.id) ?? []).map((child) => ({ item: child, children: [] })),
  }));
}

/** A category without children is what a tab is made of, and the only place feeds may hang. */
export function isLeaf<T extends TreeItem>(node: TreeNode<T>): boolean {
  return node.children.length === 0;
}

/** Every category that can carry feeds, top-level ones without children among them. */
export function leaves<T extends TreeItem>(nodes: TreeNode<T>[]): T[] {
  return nodes.flatMap((node) => (isLeaf(node) ? [node.item] : node.children.map((child) => child.item)));
}

/** The node's position among its siblings, and how many there are — for the move buttons. */
export function siblingPosition<T extends TreeItem>(nodes: TreeNode<T>[], id: string): { index: number; count: number } | null {
  const rows: T[][] = [nodes.map((node) => node.item), ...nodes.map((node) => node.children.map((c) => c.item))];
  for (const row of rows) {
    const index = row.findIndex((item) => item.id === id);
    if (index !== -1) {
      return { index, count: row.length };
    }
  }
  return null;
}

function sorted<T extends TreeItem>(items: T[]): T[] {
  return [...items].sort((left, right) => left.sortOrder - right.sortOrder || left.name.localeCompare(right.name));
}
