import { inject, Injectable, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import {
  catchError,
  defer,
  filter,
  map,
  of,
  switchMap,
  tap,
  throwError,
  timeout,
} from 'rxjs';

import { LoginRequest } from '../models/auth.models';
import { AuthApiService } from './auth-api.service';
import { AuthSessionStore } from './auth-session.store';
import { PlayerApiService } from '../../player/services/player-api.service';
import { AuthSessionEventsService } from './auth-session-events.service';

@Injectable({
  providedIn: 'root',
})
export class AuthService {
  private readonly authApi = inject(AuthApiService);
  private readonly playerApi = inject(PlayerApiService);
  private readonly session = inject(AuthSessionStore);
  private readonly sessionEvents = inject(AuthSessionEventsService);

  readonly currentPlayer = this.session.currentPlayer;
  readonly isAuthenticated = this.session.isAuthenticated;

  readonly restorationFailed = signal(false);

  /**
   * Restore the profile using the browser's existing session cookie.
   * A 401 means anonymous; network/server failures remain distinguishable.
   */
  restore() {
    return defer(() => {
      const revision = this.session.begin();
      this.restorationFailed.set(false);

      return this.authApi.csrf().pipe(
        switchMap(() => this.playerApi.currentPlayer()),
        timeout(10_000),
        tap(player => {
          if (this.session.revision() === revision) {
            this.session.setPlayer(player);
          }
        }),
        map(() =>
          this.session.revision() === revision &&
          this.session.isAuthenticated()
        ),
        catchError((error: unknown) => {
          if (this.session.revision() === revision) {
            const anonymous =
              error instanceof HttpErrorResponse &&
              error.status === 401;

            this.restorationFailed.set(!anonymous);
          }

          // Let the application render even if restoration fails.
          return of(false);
        }),
      );
    });
  }

  login(request: LoginRequest) {
    return defer(() => {
      const revision = this.session.begin();
      this.restorationFailed.set(false);

      return this.authApi.csrf().pipe(
        switchMap(() => this.authApi.login(request)),

        // The browser's shared session cookie has now changed.
        tap(() => this.sessionEvents.notifyChanged()),

        switchMap(player =>
          this.authApi.csrf().pipe(map(() => player)),
        ),

        filter(() => this.session.revision() === revision),
        tap(player => this.session.setPlayer(player)),

        catchError((error: unknown) => {
          if (this.session.revision() === revision) {
            this.session.clear();
          }

          return throwError(() => error);
        }),
      );
    });
  }

  logout() {
    return defer(() => {
      const revision = this.session.revision();

      return this.authApi.csrf().pipe(
        switchMap(() => this.authApi.logout()),
        tap(() => {
          if (this.session.revision() === revision) {
            this.session.clear();
          }
          this.sessionEvents.notifyChanged();
        }),

        // Logout has already succeeded. A failed CSRF reinitialization
        // must not report that server logout failed.
        switchMap(() => this.authApi.csrf().pipe(
          catchError(() => of(null)),
        )),
        map(() => undefined),
      );
    });
  }
}