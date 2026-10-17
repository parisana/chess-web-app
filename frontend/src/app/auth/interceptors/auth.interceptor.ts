import { inject } from '@angular/core';
import { HttpInterceptorFn } from '@angular/common/http';

import { TokenStore } from '../services/token-store.service';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const token = inject(TokenStore).token();

  const isBackendRequest = request.url.startsWith('/api/');
  const path = request.url.split('?')[0];

  const isPublicAuthRequest =
    path === '/api/v1/auth/login' ||
    path === '/api/v1/auth/register';

  if (!token || !isBackendRequest || isPublicAuthRequest) {
    return next(request);
  }

  // Angular recommends functional interceptors; requests are immutable, so we clone before changing headers.
  return next(
    request.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`,
      },
    }),
  );
};