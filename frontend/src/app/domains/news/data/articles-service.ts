import { httpResource } from '@angular/common/http';
import { Service, signal } from '@angular/core';

import { Article } from '../model/article';

/**
 * The stories of the tenant, threshold and all. The whole list comes down and is grouped where it
 * is shown: the same articles feed the tabs and the threshold, and grouping on the server would
 * put one rule in two places.
 */
@Service()
export class ArticlesService {
  /** The lowest ranking worth showing. Stories without one come through at any setting. */
  readonly minRanking = signal(0);

  readonly articles = httpResource<Article[]>(() => `/api/news/articles?minRanking=${this.minRanking()}`, {
    defaultValue: [],
  });

  reload(): void {
    this.articles.reload();
  }
}
