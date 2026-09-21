import { Routes } from '@angular/router';

export const playgroundRoutes: Routes = [
  {
    path: '',
    loadComponent: () => import('../feat-test/test-page').then((m) => m.TestPage),
  },
];
