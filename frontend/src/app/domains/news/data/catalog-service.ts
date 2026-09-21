import { HttpClient, httpResource } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { PickedCategory } from '../model/category';

/**
 * The tenant's categories and which of them this user wants to see. One request draws the whole
 * page. The choice orders the board and saves nothing: every source is fetched and rated either
 * way, because a story's category is only known once it has been rated.
 */
@Service()
export class CatalogService {
  private readonly http = inject(HttpClient);

  readonly categories = httpResource<PickedCategory[]>(() => '/api/news/categories', { defaultValue: [] });

  /** The complete selection, not a change to it: what is sent is what is ticked afterwards. */
  async savePicks(categoryIds: string[]): Promise<void> {
    await firstValueFrom(this.http.put<string[]>('/api/news/picks', { categoryIds }));
    this.categories.reload();
  }
}
