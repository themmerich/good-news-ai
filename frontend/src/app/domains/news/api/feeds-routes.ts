import { Routes } from '@angular/router';

export const feedsRoutes: Routes = [
  {
    path: '',
    loadComponent: () => import('../feat-catalog/feeds-page').then((m) => m.FeedsPage),
  },
];
