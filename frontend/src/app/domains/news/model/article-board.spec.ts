import { Article } from './article';
import { buildBoard, sortArticles } from './article-board';

const sport = { id: 'sport', name: 'Sport', sortOrder: 0 };
const politik = { id: 'politik', name: 'Politik', sortOrder: 1 };
const soziales = { id: 'soziales', name: 'Soziales', sortOrder: 2 };
const categories = [sport, politik, soziales];

function article(overrides: Partial<Article> & { id: string }): Article {
  return {
    categoryId: null,
    categoryName: null,
    sourceName: 'kicker',
    title: overrides.id,
    link: `https://kicker.example/${overrides.id}`,
    publishedAt: '2026-09-20T10:00:00Z',
    teaser: '',
    positiveSummary: null,
    ranking: null,
    ...overrides,
  };
}

describe('buildBoard', () => {
  it('makes a tab out of every ticked category that has something in it', () => {
    const articles = [
      article({ id: 'a', categoryId: 'sport', categoryName: 'Sport' }),
      article({ id: 'b', categoryId: 'politik', categoryName: 'Politik' }),
    ];

    const board = buildBoard(articles, categories, new Set(['sport', 'politik']));

    expect(board.tabs.map((tab) => tab.category.name)).toEqual(['Sport', 'Politik']);
  });

  it('leaves out a category nobody wrote about today', () => {
    const articles = [article({ id: 'a', categoryId: 'sport', categoryName: 'Sport' })];

    const board = buildBoard(articles, categories, new Set(['sport', 'politik']));

    // Politik is ticked but empty; a tab that leads nowhere is not worth the click.
    expect(board.tabs.map((tab) => tab.category.name)).toEqual(['Sport']);
  });

  it('leaves out a category that was not ticked', () => {
    const articles = [
      article({ id: 'a', categoryId: 'sport', categoryName: 'Sport' }),
      article({ id: 'b', categoryId: 'politik', categoryName: 'Politik' }),
    ];

    const board = buildBoard(articles, categories, new Set(['politik']));

    expect(board.tabs.map((tab) => tab.category.name)).toEqual(['Politik']);
  });

  /** A new user has ticked nothing, and an empty board would look broken rather than untouched. */
  it('reads an empty selection as every category', () => {
    const articles = [
      article({ id: 'a', categoryId: 'sport', categoryName: 'Sport' }),
      article({ id: 'b', categoryId: 'politik', categoryName: 'Politik' }),
    ];

    const board = buildBoard(articles, categories, new Set());

    expect(board.tabs.map((tab) => tab.category.name)).toEqual(['Sport', 'Politik']);
  });

  it('puts the tabs in the order the categories stand in', () => {
    const articles = [
      article({ id: 'a', categoryId: 'soziales', categoryName: 'Soziales' }),
      article({ id: 'b', categoryId: 'sport', categoryName: 'Sport' }),
    ];

    const board = buildBoard(articles, [soziales, sport, politik], new Set());

    expect(board.tabs.map((tab) => tab.category.name)).toEqual(['Sport', 'Soziales']);
  });

  it('collects what has no category of its own', () => {
    const articles = [article({ id: 'a', categoryId: 'sport', categoryName: 'Sport' }), article({ id: 'b' }), article({ id: 'c' })];

    const board = buildBoard(articles, categories, new Set(['sport']));

    expect(board.unplaced.map((entry) => entry.id)).toEqual(['b', 'c']);
  });

  /**
   * The bucket ignores the selection: what is in it is what the AI has not seen or could not
   * place, and that is exactly what one wants to see when the AI is not getting through.
   */
  it('keeps the unplaced stories whatever was ticked', () => {
    const board = buildBoard([article({ id: 'b' })], categories, new Set(['sport']));

    expect(board.tabs).toHaveLength(0);
    expect(board.unplaced.map((entry) => entry.id)).toEqual(['b']);
  });

  it('has nothing to show for an empty catalog', () => {
    const board = buildBoard([], [], new Set());

    expect(board.tabs).toHaveLength(0);
    expect(board.unplaced).toHaveLength(0);
  });
});

describe('sortArticles', () => {
  it('puts the highest ranking first', () => {
    const sorted = sortArticles([
      article({ id: 'low', ranking: 3 }),
      article({ id: 'high', ranking: 9 }),
      article({ id: 'middle', ranking: 6 }),
    ]);

    expect(sorted.map((entry) => entry.id)).toEqual(['high', 'middle', 'low']);
  });

  it('falls back to the date where two share a ranking', () => {
    const sorted = sortArticles([
      article({ id: 'older', ranking: 7, publishedAt: '2026-09-19T10:00:00Z' }),
      article({ id: 'newer', ranking: 7, publishedAt: '2026-09-20T10:00:00Z' }),
    ]);

    expect(sorted.map((entry) => entry.id)).toEqual(['newer', 'older']);
  });

  /** An empty ranking is an open question, and the answer belongs at the end rather than at 0. */
  it('puts what nobody has rated last, however new it is', () => {
    const sorted = sortArticles([
      article({ id: 'unrated', ranking: null, publishedAt: '2026-09-21T10:00:00Z' }),
      article({ id: 'rated-low', ranking: 1, publishedAt: '2026-09-01T10:00:00Z' }),
    ]);

    expect(sorted.map((entry) => entry.id)).toEqual(['rated-low', 'unrated']);
  });

  it('puts a story without a date behind the ones that have one', () => {
    const sorted = sortArticles([
      article({ id: 'undated', ranking: null, publishedAt: null }),
      article({ id: 'dated', ranking: null, publishedAt: '2026-09-01T10:00:00Z' }),
    ]);

    expect(sorted.map((entry) => entry.id)).toEqual(['dated', 'undated']);
  });

  it('leaves the list it was given untouched', () => {
    const articles = [article({ id: 'a', ranking: 1 }), article({ id: 'b', ranking: 9 })];

    sortArticles(articles);

    expect(articles.map((entry) => entry.id)).toEqual(['a', 'b']);
  });
});
