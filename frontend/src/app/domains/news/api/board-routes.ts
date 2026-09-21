import { Routes } from '@angular/router';

export const boardRoutes: Routes = [
  {
    path: '',
    loadComponent: () => import('../feat-board/board-page').then((m) => m.BoardPage),
  },
];
