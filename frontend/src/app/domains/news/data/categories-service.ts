import { HttpClient, httpResource } from '@angular/common/http';
import { inject, Service } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { Category, CategoryInput } from '../model/category';

/**
 * The catalog's categories, for the admin pages. Every change reloads the list, so what the row
 * shows is what the backend holds — a 409 for a name a sibling already has travels up to the
 * page as the HttpErrorResponse it is, with `error.reason === 'name'` in its body.
 */
@Service()
export class CategoriesService {
  private readonly http = inject(HttpClient);

  readonly categories = httpResource<Category[]>(() => '/api/categories', { defaultValue: [] });

  async create(category: CategoryInput): Promise<void> {
    await firstValueFrom(this.http.post<Category>('/api/categories', category));
    this.categories.reload();
  }

  async update(id: string, category: CategoryInput): Promise<void> {
    await firstValueFrom(this.http.put<Category>(`/api/categories/${id}`, category));
    this.categories.reload();
  }

  async remove(id: string): Promise<void> {
    await firstValueFrom(this.http.delete<void>(`/api/categories/${id}`));
    this.categories.reload();
  }
}
