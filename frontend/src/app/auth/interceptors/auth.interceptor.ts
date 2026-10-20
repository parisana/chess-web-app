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

  const token = session.token();
  const path = request.url.split('?')[0];

  const isBackendRequest = request.url.startsWith('/api/');
  const isPublicAuthRequest =
    path === '/api/v1/auth/login' ||
    path === '/api/v1/auth/register';

  if (!token || !isBackendRequest || isPublicAuthRequest) {
    return next(request);
  }

  const authenticatedRequest = request.clone({
    setHeaders: {
      Authorization: `Bearer ${token}`,
    },
  });

  return next(authenticatedRequest).pipe(
    catchError((error: unknown) => {
      if (
        error instanceof HttpErrorResponse &&
        error.status === 401 &&
        session.token() === token
      ) {
        const hadAuthenticatedPlayer =
          session.currentPlayer() !== null;

        session.clear();

        if (hadAuthenticatedPlayer) {
          void router.navigate(['/login'], {
            queryParams: { sessionExpired: 'true' },
            replaceUrl: true,
          });
        }
      }

      return throwError(() => error);
    }),
  );
};

// Login 401 stays on the form: public authentication requests bypass this handling.
// Initial /me failure stays a login failure: there’s no established player session yet.
// An older request won’t clear a different current token: we compare the token used by the request with the current token.

// We handle 401, not 403: lacking permission doesn’t necessarily mean the session is invalid.