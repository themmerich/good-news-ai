import { Routes } from '@angular/router';

export const categoriesRoutes: Routes = [
  {
    path: '',
    loadComponent: () => import('../feat-catalog/categories-page').then((m) => m.CategoriesPage),
  },
];
