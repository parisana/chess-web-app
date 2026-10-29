import { Routes } from '@angular/router';

import { authGuard } from './auth/guards/auth.guard';

export const routes: Routes = [
  {
    path: 'register',
    title: 'Create account | Chess',
    loadComponent: () =>
      import('./auth/pages/register/register').then(module => module.Register),
  },
  {
    path: 'login',
    title: 'Sign in | Chess',
    loadComponent: () =>
      import('./auth/pages/login/login').then(module => module.Login),
  },
  {
    path: 'home',
    title: 'Home | Chess',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./home/pages/home/home').then(module => module.Home),
  },
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'home',
  },
  {
    path: 'games/:gameId',
    title: 'Game | Chess',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./game/pages/game/game').then(module => module.Game),
  },
  {
    path: '**',
    redirectTo: 'home',
  },
];