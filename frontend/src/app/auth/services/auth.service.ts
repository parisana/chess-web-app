import { inject, Injectable } from '@angular/core';
import { catchError, defer, switchMap, tap, throwError } from 'rxjs';

import { LoginRequest } from '../models/auth.models';
import { AuthApiService } from './auth-api.service';
import { AuthSessionStore } from './auth-session.store';
import { PlayerApiService } from '../../player/services/player-api.service';

@Injectable({
  providedIn: 'root',
})
// keep AuthApiService for transport and AuthService for orchestration
export class AuthService {
  private readonly authApi = inject(AuthApiService);
  private readonly playerApi = inject(PlayerApiService);
  private readonly session = inject(AuthSessionStore);

  readonly currentPlayer = this.session.currentPlayer;
  readonly isAuthenticated = this.session.isAuthenticated;


  /**
    Submit credentials to login.
    Store the returned token.
    Request /players/me; the interceptor attaches that token.
    Store the player only after the profile request succeeds.
    If either request fails, clear both token and player.
   */
  login(request: LoginRequest) {
    // defer is being used as a guard to guarantee sequential execution and clean state cleanup at the exact moment the login process begins.
    return defer(() => {
      this.logout();

      return this.authApi.login(request).pipe(
        tap(response => this.session.begin(response.accessToken)),
        switchMap(() => this.playerApi.currentPlayer()),
        tap(player => this.session.setPlayer(player)),
        catchError((error: unknown) => {
          this.logout();
          return throwError(() => error);
        }),
      );
    });
  }

  logout(): void {
    this.session.clear();
  }
}