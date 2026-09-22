import { Routes } from '@angular/router';

export const costsRoutes: Routes = [
  {
    path: '',
    loadComponent: () => import('../feat-costs/costs-page').then((m) => m.CostsPage),
  },
];
