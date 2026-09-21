import { HttpClient, httpResource } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { Feed, FeedInput } from '../model/category';

/**
 * The catalog's feeds, for the admin page. A 409 says the URL is already in the catalog and
 * carries `error.reason === 'url'`.
 */
@Service()
export class FeedsService {
  private readonly http = inject(HttpClient);

  readonly feeds = httpResource<Feed[]>(() => '/api/feeds', { defaultValue: [] });

  async create(feed: FeedInput): Promise<void> {
    await firstValueFrom(this.http.post<Feed>('/api/feeds', feed));
    this.feeds.reload();
  }

  async update(id: string, feed: FeedInput): Promise<void> {
    await firstValueFrom(this.http.put<Feed>(`/api/feeds/${id}`, feed));
    this.feeds.reload();
  }

  async remove(id: string): Promise<void> {
    await firstValueFrom(this.http.delete<void>(`/api/feeds/${id}`));
    this.feeds.reload();
  }
}
