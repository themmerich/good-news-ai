import { HttpClient, httpResource } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { CatalogCategory } from '../model/category';

/**
 * The catalog as a user sees it, and what they picked from it. One request draws the whole page:
 * the tree, the feeds on it, and a mark on each feed the user takes.
 */
@Service()
export class CatalogService {
  private readonly http = inject(HttpClient);

  readonly catalog = httpResource<CatalogCategory[]>(() => '/api/news/catalog', { defaultValue: [] });

  /** The complete selection, not a change to it: what is sent is what is picked afterwards. */
  async savePicks(feedIds: string[]): Promise<void> {
    await firstValueFrom(this.http.put<string[]>('/api/news/picks', { feedIds }));
    this.catalog.reload();
  }
}
