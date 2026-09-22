import { Article } from './article';
import { OrderedCategory, sortCategories } from './category-order';

/**
 * How the stories become tabs. It lives here rather than in the component because it is a rule
 * with edges worth testing — what an empty selection means, where an unplaced story goes, how two
 * stories with the same ranking sort — and none of that needs a framework to be true.
 */

/**
 * The thresholds worth offering. Finer steps would be a slider nobody asked for, and the scale
 * the AI works to has four bands, not eleven.
 */
export const THRESHOLDS = [0, 4, 7, 9] as const;

export function isThreshold(value: number): boolean {
  return (THRESHOLDS as readonly number[]).includes(value);
}

/**
 * One tab. The bucket at the end is not one of these: it stands for the absence of a category
 * rather than for a category, and giving it a nullable field here would make every template that
 * touches a tab assert the field is there.
 */
export type BoardTab<T extends OrderedCategory & { id: string }> = {
  category: T;
  articles: Article[];
};

/**
 * One tab per ticked category that has something in it, and the bucket behind them.
 *
 * <p>Nothing ticked means everything: a new user would otherwise be shown an empty board and take
 * it for broken. An empty tab is left out — a category nobody wrote about today is not worth a
 * click that leads nowhere.
 *
 * <p>The bucket ignores the selection on purpose. What is in it is what has no category: stories
 * the AI has not seen and stories it could not place. Hiding those behind a filter would mean the
 * board quietly swallows exactly what one wants to see when the AI is not getting through.
 */
export function buildBoard<T extends OrderedCategory & { id: string }>(
  articles: Article[],
  categories: T[],
  pickedIds: ReadonlySet<string>,
): { tabs: BoardTab<T>[]; unplaced: Article[] } {
  const wanted = pickedIds.size === 0 ? new Set(categories.map((category) => category.id)) : pickedIds;
  const byCategory = new Map<string, Article[]>();
  const unplaced: Article[] = [];

  for (const article of articles) {
    if (article.categoryId === null) {
      unplaced.push(article);
      continue;
    }
    const bucket = byCategory.get(article.categoryId);
    if (bucket === undefined) {
      byCategory.set(article.categoryId, [article]);
    } else {
      bucket.push(article);
    }
  }

  const tabs: BoardTab<T>[] = [];
  for (const category of sortCategories(categories)) {
    if (!wanted.has(category.id)) {
      continue;
    }
    const articlesHere = byCategory.get(category.id);
    if (articlesHere !== undefined && articlesHere.length > 0) {
      tabs.push({ category, articles: sortArticles(articlesHere) });
    }
  }
  return { tabs, unplaced: sortArticles(unplaced) };
}

/**
 * By ranking, highest first, and by date where two share one. Stories nobody has rated go last,
 * whatever their date: an empty ranking is an open question rather than a zero, and the answer to
 * it is worth having at the end of the list rather than scattered through it.
 */
export function sortArticles(articles: Article[]): Article[] {
  return [...articles].sort((left, right) => {
    if ((left.ranking === null) !== (right.ranking === null)) {
      return left.ranking === null ? 1 : -1;
    }
    const byRanking = (right.ranking ?? 0) - (left.ranking ?? 0);
    return byRanking !== 0 ? byRanking : byDateDescending(left.publishedAt, right.publishedAt);
  });
}

/** Newest first; a story without a date goes last rather than to the top by accident. */
function byDateDescending(left: string | null, right: string | null): number {
  if (left === right) {
    return 0;
  }
  if (left === null) {
    return 1;
  }
  if (right === null) {
    return -1;
  }
  return right.localeCompare(left);
}
