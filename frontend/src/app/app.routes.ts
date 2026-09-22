import { Routes } from '@angular/router';

import { adminGuard } from './core/admin-guard';
import { authGuard } from './core/auth-guard';
import { superuserGuard } from './core/superuser-guard';
import { tenantGuard } from './core/tenant-guard';
import { Shell } from './core/feat-navigation/shell/shell';

export const routes: Routes = [
  {
    path: 'login',
    loadComponent: () => import('./core/feat-login/login-page').then((m) => m.LoginPage),
  },
  // The shell wraps every signed-in route; the guard resolves the session once
  // per app start and sends anonymous visitors to the login page.
  {
    path: '',
    component: Shell,
    canActivate: [authGuard],
    children: [
      {
        // The start page is about a tenant: a super-user with none open is sent to the tenants page.
        path: '',
        canActivate: [tenantGuard],
        loadChildren: () => import('./domains/news/api/board-routes').then((m) => m.boardRoutes),
      },
      {
        path: 'tenants',
        canActivate: [superuserGuard],
        loadChildren: () => import('./domains/tenants/api/tenants-routes').then((m) => m.tenantsRoutes),
      },
      {
        path: 'ai-settings',
        canActivate: [adminGuard],
        loadChildren: () => import('./domains/admin/api/ai-settings-routes').then((m) => m.aiSettingsRoutes),
      },
      {
        path: 'costs',
        canActivate: [adminGuard],
        loadChildren: () => import('./domains/admin/api/costs-routes').then((m) => m.costsRoutes),
      },
      {
        path: 'users',
        canActivate: [adminGuard],
        loadChildren: () => import('./domains/admin/api/users-routes').then((m) => m.usersRoutes),
      },
      {
        path: 'categories',
        canActivate: [adminGuard],
        loadChildren: () => import('./domains/news/api/categories-routes').then((m) => m.categoriesRoutes),
      },
      {
        path: 'feeds',
        canActivate: [adminGuard],
        loadChildren: () => import('./domains/news/api/feeds-routes').then((m) => m.feedsRoutes),
      },
      {
        path: 'picks',
        canActivate: [tenantGuard],
        loadChildren: () => import('./domains/news/api/picks-routes').then((m) => m.picksRoutes),
      },
      {
        path: 'company',
        canActivate: [adminGuard],
        loadChildren: () => import('./domains/admin/api/company-routes').then((m) => m.companyRoutes),
      },
      {
        path: 'profile',
        canActivate: [tenantGuard],
        loadComponent: () => import('./core/feat-profile/profile-page').then((m) => m.ProfilePage),
      },
    ],
  },
];
