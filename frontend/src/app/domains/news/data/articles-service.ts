import { DOCUMENT } from '@angular/common';
import { httpResource } from '@angular/common/http';
import { effect, inject, InjectionToken, Service, signal } from '@angular/core';

import { Article } from '../model/article';
import { isThreshold, THRESHOLDS } from '../model/article-board';

const STORAGE_KEY = 'good-news-min-ranking';

/**
 * The storage backing the threshold. Injectable so tests can provide an in-memory fake: depending
 * on Node version and jsdom, neither the global nor the jsdom window reliably offers a working
 * localStorage in unit tests.
 */
export const MIN_RANKING_STORAGE = new InjectionToken<Storage | null>('MIN_RANKING_STORAGE', {
  providedIn: 'root',
  factory: () => inject(DOCUMENT).defaultView?.localStorage ?? null,
});

/**
 * The stories of the tenant, threshold and all. The whole list comes down and is grouped where it
 * is shown: the same articles feed the tabs and the threshold, and grouping on the server would
 * put one rule in two places.
 *
 * <p>The threshold is remembered at this browser, the way the theme and the column choice are.
 * Somebody who reads only what matters should not have to say so again every morning.
 */
@Service()
export class ArticlesService {
  private readonly storage = inject(MIN_RANKING_STORAGE);

  /** The lowest ranking worth showing. Stories without one come through at any setting. */
  readonly minRanking = signal(this.restore());

  readonly articles = httpResource<Article[]>(() => `/api/news/articles?minRanking=${this.minRanking()}`, {
    defaultValue: [],
  });

  constructor() {
    effect(() => this.storage?.setItem(STORAGE_KEY, String(this.minRanking())));
  }

  reload(): void {
    this.articles.reload();
  }

  private restore(): number {
    const stored = Number(this.storage?.getItem(STORAGE_KEY));
    // A value that is not one of the offered steps is drift, from an older version or a hand
    // edit; falling back beats asking the backend to filter on something nobody can unset.
    return isThreshold(stored) ? stored : THRESHOLDS[0];
  }
}
