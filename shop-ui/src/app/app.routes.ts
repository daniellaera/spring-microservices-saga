import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  { path: '', redirectTo: '/dashboard', pathMatch: 'full' },
  {
    path: 'login',
    loadComponent: () =>
      import('./features/auth/login/login.component')
        .then(m => m.LoginComponent)
  },
  {
    path: 'register',
    loadComponent: () =>
      import('./features/auth/register/register.component')
        .then(m => m.RegisterComponent)
  },
  {
    path: 'dashboard',
    loadComponent: () =>
      import('./features/dashboard/dashboard.component')
        .then(m => m.DashboardComponent),
    canActivate: [authGuard]
  },
  {
    path: 'products',
    loadComponent: () =>
      import('./features/products/products.component')
        .then(m => m.ProductsComponent),
    canActivate: [authGuard]
  },
  {
    path: 'audit/orders/:orderId',
    loadComponent: () =>
      import('./features/audit/audit-timeline.component')
        .then(m => m.AuditTimelineComponent),
    canActivate: [authGuard]
  },
  {
    path: 'audit',
    loadComponent: () =>
      import('./features/audit/audit.component')
        .then(m => m.AuditComponent),
    canActivate: [authGuard]
  },
  { path: '**', redirectTo: '/dashboard' }
];
