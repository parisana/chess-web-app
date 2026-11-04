import { DestroyRef, inject, Injectable } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import {
  catchError,
  filter,
  from,
  of,
  switchMap,
} from 'rxjs';

import { AuthService } from './auth.service';
import { AuthSessionStore } from './auth-session.store';
import { AuthSessionEventsService } from './auth-session-events.service';

@Injectable({
  providedIn: 'root',
})
export class SessionSyncService {
  private readonly auth = inject(AuthService);
  private readonly session = inject(AuthSessionStore);
  private readonly events = inject(AuthSessionEventsService);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);

  constructor() {
    this.events.changes$.pipe(
      switchMap(() => {
        // Disable authenticated interactions and invalidate old callbacks
        // before waiting for navigation or HTTP.
        const revision = this.session.begin();

        return from(
          this.router.navigateByUrl('/login', {
            replaceUrl: true,
          }),
        ).pipe(
          // A newer local login/logout takes precedence over this
          // pending synchronization attempt.
          filter(() => this.session.revision() === revision),

          // Navigation destroys the old Home/Game components first.
          // Restoring another account must not reuse their local state.
          switchMap(() => this.auth.restore()),

          switchMap(isAuthenticated =>
            isAuthenticated
              ? from(this.router.navigateByUrl('/home', {
                replaceUrl: true,
              }))
              : of(false),
          ),

          // Keep listening for future session changes if navigation
          // or another unexpected operation fails.
          catchError(() => of(false)),
        );
      }),

      takeUntilDestroyed(this.destroyRef),
    ).subscribe();
  }
}