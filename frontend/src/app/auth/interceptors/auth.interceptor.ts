import { inject } from '@angular/core';
import {
  HttpErrorResponse,
  HttpInterceptorFn,
} from '@angular/common/http';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { AuthSessionStore } from '../services/auth-session.store';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const session = inject(AuthSessionStore);
  const router = inject(Router);

  const isBackendRequest = request.url.startsWith('/api/');
  const isAuthRequest = request.url.startsWith('/api/v1/auth/');

  if (!isBackendRequest || isAuthRequest) {
    return next(request);
  }

  const revision = session.revision();
  const wasAuthenticated = session.isAuthenticated();

  // The browser sends the session cookie.
  // Angular's built-in XSRF interceptor supplies the CSRF header.
  return next(request).pipe(
    catchError((error: unknown) => {
      if (
        error instanceof HttpErrorResponse &&
        error.status === 401 &&
        wasAuthenticated &&
        session.revision() === revision
      ) {
        session.clear();

        void router.navigate(['/login'], {
          queryParams: { sessionExpired: 'true' },
          replaceUrl: true,
        });
      }

      return throwError(() => error);
    }),
  );
};